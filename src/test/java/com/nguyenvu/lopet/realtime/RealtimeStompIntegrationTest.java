package com.nguyenvu.lopet.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.nguyenvu.lopet.auth.AuthService;
import com.nguyenvu.lopet.auth.dto.RegisterRequest;
import com.nguyenvu.lopet.config.JacksonConfig;
import com.nguyenvu.lopet.email.OtpStore;
import com.nguyenvu.lopet.message.MessageService;
import com.nguyenvu.lopet.message.dto.MessageDtos;
import com.nguyenvu.lopet.message.entity.MessageStatus;
import com.nguyenvu.lopet.message.repository.MessageRepository;
import com.nguyenvu.lopet.security.jwt.JwtService;
import com.nguyenvu.lopet.security.jwt.UserPrincipal;
import com.nguyenvu.lopet.support.IntegrationTestBase;

import tools.jackson.databind.json.JsonMapper;

/**
 * Realtime đầu-cuối trên một Tomcat thật: bắt tay WebSocket, xác thực bằng chính JWT của REST, rồi
 * một ack từ người nhận phải quay về đúng người gửi.
 *
 * <p>Thay cho {@code SocketIoJsonSupportTest} của bản netty-socketio. Test cũ phải tự lắp
 * {@code JacksonJsonSupport} và bơm {@code ByteBuf} vào nó, vì netty-socketio serialize bằng một
 * ObjectMapper Jackson 2 tách rời hoàn toàn khỏi Spring — một lớp lỗi im lặng chỉ vì hai bộ Jackson
 * có thể lệch nhau. Ở đây payload đi qua đúng converter mà ứng dụng dùng thật, nên một phép thử
 * kiểm được cả định dạng ngày lẫn đường đi của sự kiện.
 *
 * <p>Không kế thừa {@link IntegrationTestBase} được: lớp đó chạy {@code webEnvironment = MOCK}, mà
 * bắt tay WebSocket thì cần một cổng TCP thật. Vẫn dùng lại {@code jdbcUrl}/{@code redisUrl} của nó
 * để lớp test này có database riêng.
 *
 * <p>YÊU CẦU: {@code docker compose up -d mysql-docker redis}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Realtime STOMP")
class RealtimeStompIntegrationTest {

    private static final String RUN = Long.toString(System.nanoTime(), 36);
    private static final long TIMEOUT_SECONDS = 10;

    /** Chỉ nodeIso mới sinh ra đúng ba chữ số mili giây kèm hậu tố Z; mặc định của Jackson thì không */
    private static final String NODE_ISO = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z";

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> IntegrationTestBase.jdbcUrl("lopet_java_realtime_test"));
        registry.add("spring.data.redis.url", () -> IntegrationTestBase.redisUrl(4));
    }

    @LocalServerPort
    private int port;

    @Value("${lopet.jwt.access-token-secret}")
    private String accessSecret;
    @Value("${lopet.jwt.refresh-token-secret}")
    private String refreshSecret;

    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private AuthService authService;
    @Autowired
    private OtpStore otpStore;
    @Autowired
    private MessageService messageService;
    @Autowired
    private MessageRepository messageRepository;

    // ------------------------------------------------------------------ CONNECT

    @Test
    @DisplayName("token hợp lệ thì bắt tay xong và phiên mở")
    void connect_voi_token_hop_le() throws Exception {
        StompSession session = connect(accessToken(register("connect-ok")));

        assertThat(session.isConnected()).isTrue();
        session.disconnect();
    }

    @Test
    @DisplayName("thiếu token, token hỏng và token hết hạn cho ba thông điệp khác nhau")
    void connect_that_bai_giu_nguyen_thong_diep_cua_ban_socket_io() throws Exception {
        Integer accountId = register("connect-fail");

        assertThat(connectError(null))
                .isEqualTo(StompAuthChannelInterceptor.AUTHENTICATION_ERROR);
        assertThat(connectError("khong-phai-jwt"))
                .isEqualTo(StompAuthChannelInterceptor.INVALID_TOKEN);
        assertThat(connectError(expiredToken(accountId)))
                .isEqualTo(StompAuthChannelInterceptor.TOKEN_EXPIRED);
    }

    // ------------------------------------------------------------------ SUBSCRIBE

    /**
     * Bản netty-socketio KHÔNG chặn được việc này: sự kiện {@code join room} nhận tên phòng tuỳ ý,
     * nên một tài khoản hợp lệ vào được {@code user_<id_người_khác>} và nghe hết tin nhắn của họ.
     */
    @Test
    @DisplayName("không subscribe được destination của người khác")
    void subscribe_destination_cua_nguoi_khac_bi_chan() throws Exception {
        Integer keXauId = register("ke-xau");
        Integer nanNhanId = register("nan-nhan");

        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        StompSession session = connect(accessToken(keXauId), errors);
        session.subscribe(RealtimeGateway.userTopic(nanNhanId, RealtimeGateway.CHANNEL_CHAT),
                new PayloadHandler(new LinkedBlockingQueue<>()));

        assertThat(errors.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isEqualTo(StompAuthChannelInterceptor.FORBIDDEN_DESTINATION);
    }

    @Test
    @DisplayName("subscribe destination của chính mình thì được")
    void subscribe_destination_cua_chinh_minh() throws Exception {
        Integer accountId = register("chinh-chu");

        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        StompSession session = connect(accessToken(accountId), errors);
        session.subscribe(RealtimeGateway.userTopic(accountId, RealtimeGateway.CHANNEL_CHAT),
                new PayloadHandler(new LinkedBlockingQueue<>()));

        assertThat(errors.poll(2, TimeUnit.SECONDS)).isNull();
        session.disconnect();
    }

    // ------------------------------------------------------------------ SEND

    /**
     * Thiếu guard này thì client SEND thẳng vào destination của broker và SimpleBroker phát tán —
     * ai cũng giả được tin nhắn đến từ người khác.
     */
    @Test
    @DisplayName("không gửi thẳng được vào destination của broker")
    void send_vao_topic_bi_chan() throws Exception {
        Integer accountId = register("gia-mao");

        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        StompSession session = connect(accessToken(accountId), errors);
        session.send(RealtimeGateway.userTopic(accountId, RealtimeGateway.CHANNEL_CHAT),
                Map.of("message", "giả mạo"));

        assertThat(errors.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isEqualTo(StompAuthChannelInterceptor.FORBIDDEN_DESTINATION);
    }

    // ------------------------------------------------------------------ Vòng đủ

    @Test
    @DisplayName("ack 'đã xem' đổi trạng thái trong DB và quay về đúng người gửi")
    void ack_da_xem_di_het_vong() throws Exception {
        Integer nguoiGuiId = register("doc-gui");
        Integer nguoiNhanId = register("doc-nhan");
        MessageDtos.CreateMessageResponse tin = messageService.create(
                String.valueOf(nguoiGuiId), String.valueOf(nguoiNhanId), "chào", "");

        BlockingQueue<Map<?, ?>> nhanDuoc = new LinkedBlockingQueue<>();
        StompSession nguoiGui = connect(accessToken(nguoiGuiId));
        nguoiGui.subscribe(
                RealtimeGateway.userTopic(nguoiGuiId, RealtimeGateway.CHANNEL_MESSAGE_STATUS),
                new PayloadHandler(nhanDuoc));

        StompSession nguoiNhan = connect(accessToken(nguoiNhanId));
        nguoiNhan.send("/app/message.read", new MessageDtos.ReadAckRequest(nguoiGuiId));

        Map<?, ?> suKien = nhanDuoc.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(suKien).isNotNull();
        assertThat(suKien.get("messageIds")).isEqualTo(List.of(tin.id()));
        assertThat(suKien.get("status")).isEqualTo(MessageStatus.READ.name());
        assertThat(suKien.get("byUserId")).isEqualTo(nguoiNhanId);
        assertThat((String) suKien.get("at")).matches(NODE_ISO);

        assertThat(messageRepository.findById(tin.id()).orElseThrow().getStatus())
                .isEqualTo(MessageStatus.READ);

        nguoiGui.disconnect();
        nguoiNhan.disconnect();
    }

    @Test
    @DisplayName("ack 'đã nhận' đi cùng đường và mang đúng lô id")
    void ack_da_nhan_di_het_vong() throws Exception {
        Integer nguoiGuiId = register("nhan-gui");
        Integer nguoiNhanId = register("nhan-nhan");
        MessageDtos.CreateMessageResponse tin = messageService.create(
                String.valueOf(nguoiGuiId), String.valueOf(nguoiNhanId), "xin chào", "");

        BlockingQueue<Map<?, ?>> nhanDuoc = new LinkedBlockingQueue<>();
        StompSession nguoiGui = connect(accessToken(nguoiGuiId));
        nguoiGui.subscribe(
                RealtimeGateway.userTopic(nguoiGuiId, RealtimeGateway.CHANNEL_MESSAGE_STATUS),
                new PayloadHandler(nhanDuoc));

        StompSession nguoiNhan = connect(accessToken(nguoiNhanId));
        nguoiNhan.send("/app/message.delivered",
                new MessageDtos.DeliveredAckRequest(List.of(tin.id())));

        Map<?, ?> suKien = nhanDuoc.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(suKien).isNotNull();
        assertThat(suKien.get("messageIds")).isEqualTo(List.of(tin.id()));
        assertThat(suKien.get("status")).isEqualTo(MessageStatus.DELIVERED.name());
        assertThat((String) suKien.get("at")).matches(NODE_ISO);

        assertThat(messageRepository.findById(tin.id()).orElseThrow().getStatus())
                .isEqualTo(MessageStatus.DELIVERED);

        nguoiGui.disconnect();
        nguoiNhan.disconnect();
    }

    /**
     * Nửa còn lại của định dạng ngày: broker serialize bằng chính {@code JsonMapper} của ứng dụng,
     * nên payload realtime và payload REST không thể lệch nhau. Kiểm bằng một mốc thời gian cố định
     * vì mốc lấy từ database đã qua vòng làm tròn micro giây của MySQL.
     */
    @Test
    @DisplayName("JsonMapper của ứng dụng định dạng ngày đúng như REST")
    void dinh_dang_ngay_giong_het_REST() {
        LocalDateTime moc = LocalDateTime.of(2026, 8, 10, 17, 58, 44, 122_797_000);
        MessageDtos.MessageStatusEvent suKien =
                new MessageDtos.MessageStatusEvent(List.of(11, 12), MessageStatus.READ, moc, 4);

        assertThat(jsonMapper.writeValueAsString(suKien))
                .contains("\"at\":\"" + JacksonConfig.nodeIso(moc) + "\"")
                .contains("\"messageIds\":[11,12]")
                .contains("\"status\":\"READ\"")
                .contains("\"byUserId\":4");
    }

    // ------------------------------------------------------------------ hạ tầng test

    private Integer register(String name) {
        String unique = name + "-" + RUN;
        String email = unique + "@realtime.local";
        otpStore.markVerified(email);
        return authService.register(
                new RegisterRequest(email, unique, "password123", "password123")).id();
    }

    private String accessToken(Integer accountId) {
        return jwtService.generateAccessToken(principal(accountId));
    }

    /** TTL âm: chữ ký vẫn đúng (cùng secret của profile test) nhưng {@code exp} đã ở quá khứ */
    private String expiredToken(Integer accountId) {
        return new JwtService(jsonMapper, accessSecret, refreshSecret, -60, -60)
                .generateAccessToken(principal(accountId));
    }

    private UserPrincipal principal(Integer accountId) {
        return new UserPrincipal(accountId, "u" + accountId + "@realtime.local", List.of());
    }

    private WebSocketStompClient stompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter(jsonMapper));
        return client;
    }

    private StompSession connect(String token) throws Exception {
        return connect(token, new LinkedBlockingQueue<>());
    }

    private StompSession connect(String token, BlockingQueue<String> errors) throws Exception {
        StompHeaders headers = new StompHeaders();
        if (token != null) {
            headers.add("Authorization", "Bearer " + token);
        }
        return stompClient()
                .connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                        new ErrorCapturingHandler(errors))
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /** Thông điệp của frame ERROR khi CONNECT bị từ chối, hoặc {@code null} nếu không có frame nào */
    private String connectError(String token) throws Exception {
        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        try {
            connect(token, errors).disconnect();
        } catch (Exception expected) {
            // CONNECT hỏng thì future cũng hỏng — thông điệp thật nằm ở frame ERROR
        }
        return errors.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /** Frame ERROR không có body, nên {@code handleFrame} chỉ mang header — trong đó có `message` */
    private static final class ErrorCapturingHandler extends StompSessionHandlerAdapter {

        private final BlockingQueue<String> errors;

        private ErrorCapturingHandler(BlockingQueue<String> errors) {
            this.errors = errors;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            // StompHeaders không có accessor riêng cho header `message` của frame ERROR
            String message = headers.getFirst("message");
            if (message != null) {
                errors.add(message);
            }
        }
    }

    /** Đẩy payload đã convert vào hàng đợi để test poll ra; {@code Map} để đọc được cả JSON thô */
    private static final class PayloadHandler implements StompFrameHandler {

        private final BlockingQueue<Map<?, ?>> received;

        private PayloadHandler(BlockingQueue<Map<?, ?>> received) {
            this.received = received;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return Map.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            received.add((Map<?, ?>) payload);
        }
    }
}
