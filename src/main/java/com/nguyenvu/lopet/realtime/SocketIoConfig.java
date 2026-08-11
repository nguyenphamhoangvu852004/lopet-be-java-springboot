package com.nguyenvu.lopet.realtime;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.corundumstudio.socketio.AuthTokenResult;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.namespace.Namespace;
import com.corundumstudio.socketio.protocol.JacksonJsonSupport;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.nguyenvu.lopet.config.JacksonConfig;
import com.nguyenvu.lopet.security.jwt.JwtService;
import com.nguyenvu.lopet.security.jwt.UserPrincipal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Socket.IO server tương thích protocol EIO4, tức là {@code socket.io-client} v4 mà frontend đang
 * dùng kết nối được mà không phải đổi thư viện.
 *
 * <p><b>Khác biệt duy nhất so với lopet-be</b>: bản Express gắn Socket.IO vào chính HTTP server đang
 * phục vụ REST (cùng cổng 8080), còn ở đây Tomcat đã chiếm cổng đó nên Socket.IO phải nghe cổng
 * riêng ({@code SOCKET_PORT}, mặc định 8081). Client cần trỏ URL socket sang cổng này, hoặc đặt một
 * reverse proxy định tuyến {@code /socket.io/} về đó. Chi tiết ở docs/MIGRATION_FINAL_REPORT.md.
 *
 * <p><b>Xác thực phải nằm ở {@code AuthTokenListener}, không phải {@code AuthorizationListener}</b>.
 * Trong EIO4, {@code auth: { token }} của client đi trong gói CONNECT gửi SAU khi bắt tay HTTP xong,
 * còn {@code AuthorizationListener} chạy ngay lúc bắt tay — lúc đó {@code handshakeData.getAuthToken()}
 * luôn null nên mọi kết nối đều bị từ chối với log "thiếu auth.token". netty-socketio chỉ điền
 * {@code authToken} khi xử lý gói CONNECT, rồi chuyển cho {@code AuthTokenListener} của namespace.
 *
 * <p>Hai cạm bẫy kèm theo, cả hai đều được xử lý ở đây và ở {@link SocketIoServerRunner}:
 * <ul>
 *   <li>{@code ConnectListener} chạy TRƯỚC {@code AuthTokenListener} (netty-socketio phát sự kiện
 *       connect ngay khi bắt tay xong, rồi phát lại lần nữa khi gói CONNECT được xử lý), nên không
 *       đọc được {@code userId} ở đó. Vì vậy việc vào phòng riêng nằm ngay trong listener này —
 *       đúng thời điểm danh tính được xác định.</li>
 *   <li>Client KHÔNG gửi {@code auth} thì thư viện bỏ qua listener này và cho kết nối đi tiếp;
 *       {@link SocketIoServerRunner} có đồng hồ dọn các kết nối không xác thực.</li>
 * </ul>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SocketIoConfig {

    public static final String USER_ID_KEY = "userId";

    private final JwtService jwtService;

    @Value("${lopet.socket.hostname:0.0.0.0}")
    private String hostname;

    @Value("${lopet.socket.port:8081}")
    private int port;

    @Bean
    public SocketIOServer socketIOServer() {
        com.corundumstudio.socketio.Configuration configuration =
                new com.corundumstudio.socketio.Configuration();
        configuration.setHostname(hostname);
        configuration.setPort(port);
        configuration.setOrigin("*");
        configuration.setJsonSupport(socketJsonSupport());

        SocketIOServer server = new SocketIOServer(configuration);
        server.getNamespace(Namespace.DEFAULT_NAME).addAuthTokenListener(this::authenticate);
        return server;
    }

    /**
     * netty-socketio serialize payload bằng ObjectMapper Jackson 2 của riêng nó, hoàn toàn không
     * thấy {@code JsonMapperBuilderCustomizer} của Spring (Spring Boot 4 đã chuyển sang Jackson 3,
     * package {@code tools.jackson}). Thiếu module này thì mọi sự kiện có {@code LocalDateTime} —
     * {@code notification}, {@code change status} — chết khi encode ("Java 8 date/time type not
     * supported by default") và client không nhận được gì, dù REST vẫn trả về 201.
     *
     * <p>Định dạng lấy từ {@link JacksonConfig#nodeIso} để payload socket và payload REST luôn
     * giống hệt nhau.
     */
    static JacksonJsonSupport socketJsonSupport() {
        return new JacksonJsonSupport(nodeDateModule());
    }

    private static Module nodeDateModule() {
        SimpleModule module = new SimpleModule("lopet-node-dates-socketio");
        module.addSerializer(LocalDateTime.class, new JsonSerializer<>() {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator generator,
                    SerializerProvider serializers) throws IOException {
                generator.writeString(JacksonConfig.nodeIso(value));
            }
        });
        return module;
    }

    /**
     * Tương đương middleware {@code io.use(...)} bên TS. Khác với {@code AuthorizationListener},
     * ở đây kèm được thông điệp lỗi nên client vẫn phân biệt được TOKEN_EXPIRED / INVALID_TOKEN
     * qua {@code err.message} của sự kiện {@code connect_error}.
     */
    private AuthTokenResult authenticate(Object authToken, SocketIOClient client) {
        String token = extractToken(authToken);
        if (token == null) {
            log.debug("Socket authentication failed: thiếu auth.token");
            return failure("AUTHENTICATION_ERROR");
        }
        try {
            UserPrincipal principal = jwtService.parseAccessToken(token);
            if (principal.id() == null) {
                log.debug("Socket authentication failed: token không có accountId");
                return failure("AUTHENTICATION_ERROR");
            }
            // Store của client — tương đương socket.data.userId bên TS
            client.set(USER_ID_KEY, principal.id());
            // Mỗi tài khoản có một phòng riêng để server đẩy tin nhắn/thông báo tới đúng người
            client.joinRoom(RealtimeGateway.userRoom(principal.id()));
            log.info("User {} connected with socketId {}", principal.id(), client.getSessionId());
            return AuthTokenResult.AuthTokenResultSuccess;
        } catch (com.nguyenvu.lopet.security.jwt.JwtException exception) {
            log.debug("Socket authentication failed: {}", exception.getMessage());
            return failure(exception.isExpired() ? "TOKEN_EXPIRED" : "INVALID_TOKEN");
        }
    }

    /** socket.io-client đọc {@code message} của gói CONNECT_ERROR làm {@code err.message} */
    private AuthTokenResult failure(String message) {
        return new AuthTokenResult(false, Map.of("message", message));
    }

    /**
     * socket.io-client gửi {@code auth} dưới dạng object, nên token nằm ở khoá {@code token}.
     * Vẫn chấp nhận trường hợp client gửi thẳng một chuỗi để không kén phiên bản client.
     */
    private String extractToken(Object authToken) {
        if (authToken instanceof Map<?, ?> auth) {
            Object token = auth.get("token");
            return token instanceof String value && !value.isBlank() ? value : null;
        }
        if (authToken instanceof String value && !value.isBlank()) {
            return value;
        }
        return null;
    }
}
