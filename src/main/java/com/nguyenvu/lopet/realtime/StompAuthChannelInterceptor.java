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

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

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
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            case SEND -> authorizeSend(accessor);
            default -> {
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

    private Integer requireAuthenticated(StompHeaderAccessor accessor) {
        Integer accountId = WebSocketPrincipal.accountIdOf(accessor.getUser());
        if (accountId == null) {
            throw reject(AUTHENTICATION_ERROR);
        }
        return accountId;
    }

    private String ownerOf(String destination) {
        String rest = destination.substring(RealtimeGateway.USER_TOPIC_PREFIX.length());
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    private MessageDeliveryException reject(String reason) {
        return new MessageDeliveryException(reason);
    }

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
