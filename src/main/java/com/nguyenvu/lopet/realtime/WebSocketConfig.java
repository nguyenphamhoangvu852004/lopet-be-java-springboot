package com.nguyenvu.lopet.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import lombok.RequiredArgsConstructor;

/**
 * STOMP over WebSocket chạy trên chính Tomcat, cùng cổng với REST.
 *
 * <p>Bản netty-socketio trước đây phải mở một server Netty độc lập ở cổng riêng
 * ({@code SOCKET_PORT}) vì Tomcat đã giữ cổng HTTP — kéo theo một biến môi trường, một
 * {@code EXPOSE} và một khối {@code upstream} riêng trong nginx. Ở đây endpoint {@code /ws} chỉ là
 * một handler nữa của Tomcat nên khác biệt đó biến mất, và {@code nginx} chỉ cần một
 * {@code location} biết nâng cấp WebSocket.
 *
 * <p><b>Không dùng SockJS</b>: fallback long-polling chỉ cần cho môi trường chặn Upgrade, và nó kéo
 * theo cả một tầng URL {@code /info}, {@code /xhr_streaming}... phải cấu hình đúng ở proxy. Client
 * kết nối thẳng {@code ws(s)://<host>/ws}.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    /**
     * Dùng chung property với CORS của REST ({@code config/WebConfig}) — origin được phép là một
     * danh sách duy nhất cho cả hai đường vào, không phải hai chỗ có thể lệch nhau.
     */
    @Value("${lopet.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        // Heartbeat hai chiều 25 giây: proxy và load balancer thường cắt kết nối rảnh ở mốc 60 giây,
        // và bản thân client cũng cần biết server đã chết để reconnect thay vì treo im lặng.
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[] { 25_000, 25_000 })
                .setTaskScheduler(brokerHeartbeatScheduler());
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    /** SimpleBroker chỉ phát heartbeat khi được cấp scheduler; thiếu nó thì cấu hình trên vô hiệu. */
    private ThreadPoolTaskScheduler brokerHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }
}
