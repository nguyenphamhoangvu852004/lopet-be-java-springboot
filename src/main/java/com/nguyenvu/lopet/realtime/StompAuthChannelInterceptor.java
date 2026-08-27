package com.nguyenvu.lopet.realtime;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import com.nguyenvu.lopet.security.jwt.JwtException;
import com.nguyenvu.lopet.security.jwt.JwtService;
import com.nguyenvu.lopet.security.jwt.UserPrincipal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Toàn bộ bảo mật realtime nằm ở đây — tương đương middleware {@code io.use(...)} bên TS, nhưng canh
 * cả ba frame chứ không chỉ lúc kết nối.
 *
 * <p><b>CONNECT</b> — đọc JWT rồi gắn {@link WebSocketPrincipal} vào phiên. Token đi trong header
 * của frame CONNECT chứ không phải query string: nó không lọt vào access log của nginx, và đây cũng
 * là chỗ gần nhất với {@code auth: { token }} mà socket.io-client vẫn dùng. Sai token → ném ra
 * ngoài, Spring trả frame ERROR mang đúng thông điệp rồi đóng kết nối, nên
 * <b>không tồn tại phiên vô danh nào</b>. Bản netty-socketio cũ thì có: client không gửi {@code auth}
 * vẫn sống được tới khi một watchdog 5 giây dọn đi, và trong khoảng đó gửi được sự kiện bất kỳ.
 *
 * <p><b>SUBSCRIBE</b> — mặc định TỪ CHỐI, chỉ mở đúng những destination trong hợp đồng
 * ({@link RealtimeGateway}), và {@code /topic/user.<id>/...} thì {@code <id>} phải là chính người
 * đang kết nối. Đây là chỗ vá lỗ hổng của bản cũ: sự kiện {@code join room} nhận tên phòng tuỳ ý nên
 * một tài khoản hợp lệ join được {@code user_<id_người_khác>} và nghe lén tin nhắn của họ.
 *
 * <p><b>SEND</b> — chỉ cho phép {@code /app/**}. Nếu bỏ kiểm tra này, client SEND thẳng tới
 * {@code /topic/user.<id>/chat} và SimpleBroker sẽ vui vẻ phát tán: ai cũng giả được tin nhắn của
 * người khác. {@code /app/**} thì luôn đi qua {@code @MessageMapping}, nơi danh tính được lấy từ
 * {@code Principal} chứ không phải từ payload.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    /** Ba thông điệp này là hợp đồng với client, giữ nguyên từ bản Socket.IO (`err.message`) */
    public static final String AUTHENTICATION_ERROR = "AUTHENTICATION_ERROR";
    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
    public static final String INVALID_TOKEN = "INVALID_TOKEN";
    public static final String FORBIDDEN_DESTINATION = "FORBIDDEN_DESTINATION";

    private static final String APP_PREFIX = "/app/";

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        // Heartbeat và frame nội bộ không mang command — để đi tiếp
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            case SEND -> authorizeSend(accessor);
            default -> {
                // DISCONNECT, ACK, NACK... không cần canh
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String token = extractToken(accessor);
        if (token == null) {
            log.debug("STOMP CONNECT bị từ chối: thiếu token");
            throw reject(AUTHENTICATION_ERROR);
        }
        try {
            UserPrincipal principal = jwtService.parseAccessToken(token);
            if (principal.id() == null) {
                log.debug("STOMP CONNECT bị từ chối: token không có accountId");
                throw reject(AUTHENTICATION_ERROR);
            }
            accessor.setUser(new WebSocketPrincipal(principal));
            log.info("User {} đã kết nối WebSocket, session {}", principal.id(),
                    accessor.getSessionId());
        } catch (JwtException exception) {
            log.debug("STOMP CONNECT bị từ chối: {}", exception.getMessage());
            throw reject(exception.isExpired() ? TOKEN_EXPIRED : INVALID_TOKEN);
        }
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        Integer accountId = requireAuthenticated(accessor);
        String destination = accessor.getDestination();

        if (destination != null && destination.startsWith(RealtimeGateway.USER_TOPIC_PREFIX)) {
            if (String.valueOf(accountId).equals(ownerOf(destination))) {
                return;
            }
            log.debug("User {} bị chặn khi subscribe {}", accountId, destination);
            throw reject(FORBIDDEN_DESTINATION);
        }

        // Đổi trạng thái một thông báo: nhiều người cùng nghe được, đúng như phòng object_<id> cũ
        if (destination != null && destination.startsWith(RealtimeGateway.NOTIFICATION_TOPIC_PREFIX)) {
            return;
        }

        log.debug("User {} bị chặn khi subscribe destination ngoài hợp đồng: {}", accountId, destination);
        throw reject(FORBIDDEN_DESTINATION);
    }

    private void authorizeSend(StompHeaderAccessor accessor) {
        Integer accountId = requireAuthenticated(accessor);
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(APP_PREFIX)) {
            log.debug("User {} bị chặn khi gửi tới {}", accountId, destination);
            throw reject(FORBIDDEN_DESTINATION);
        }
    }

    /**
     * Danh tính do frame CONNECT đặt được Spring giữ lại cho mọi frame sau đó của cùng phiên, nên
     * {@code null} ở đây nghĩa là frame tới trước cả CONNECT.
     */
    private Integer requireAuthenticated(StompHeaderAccessor accessor) {
        Integer accountId = WebSocketPrincipal.accountIdOf(accessor.getUser());
        if (accountId == null) {
            throw reject(AUTHENTICATION_ERROR);
        }
        return accountId;
    }

    /** {@code /topic/user.42/chat} → {@code "42"} */
    private String ownerOf(String destination) {
        String rest = destination.substring(RealtimeGateway.USER_TOPIC_PREFIX.length());
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    /**
     * Dùng constructor một tham số: bản có {@code failedMessage} sẽ nối cả frame gốc vào
     * {@code getMessage()}, và chuỗi đó chính là header {@code message} của frame ERROR trả về client.
     */
    private MessageDeliveryException reject(String reason) {
        return new MessageDeliveryException(reason);
    }

    /** Chấp nhận cả {@code Authorization: Bearer <t>} lẫn header trần {@code token} */
    private String extractToken(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization != null && !authorization.isBlank()) {
            String[] parts = authorization.split(" ");
            String value = parts.length > 1 ? parts[1] : parts[0];
            return value.isBlank() ? null : value;
        }
        String token = accessor.getFirstNativeHeader("token");
        return token != null && !token.isBlank() ? token : null;
    }
}
