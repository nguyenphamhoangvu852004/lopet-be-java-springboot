package com.nguyenvu.lopet.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Điểm phát sự kiện realtime duy nhất — thay cho {@code res.io.to(room).emit(...)} của Express.
 *
 * <p><b>Destination là HỢP ĐỒNG với client</b>, không phải chi tiết nội bộ. Bản Socket.IO cũ tách
 * làm hai mảnh: một tên phòng ({@code user_<id>}) cộng một tên sự kiện ({@code chat messsage}).
 * STOMP chỉ có một mảnh, nên tên sự kiện được nhập vào chính destination:
 * <ul>
 *   <li>{@code /topic/user.<accountId>/chat} — tin nhắn mới tới người nhận</li>
 *   <li>{@code /topic/user.<accountId>/notification} — thông báo mới</li>
 *   <li>{@code /topic/user.<accountId>/message-status} — trạng thái tin nhắn báo về NGƯỜI GỬI</li>
 *   <li>{@code /topic/notification.<notificationId>} — đổi trạng thái của một thông báo cụ thể
 *       (thay cho phòng {@code object_<id>}); đây là destination duy nhất nhiều người cùng nghe</li>
 * </ul>
 *
 * <p>Ba destination đầu chỉ chủ nhân của {@code accountId} mới subscribe được —
 * {@link StompAuthChannelInterceptor} chặn ở frame SUBSCRIBE. Bản Socket.IO cũ không có ràng buộc
 * này: sự kiện {@code join room} nhận tên phòng tuỳ ý nên một tài khoản bất kỳ vào được phòng của
 * người khác.
 *
 * <p>Chiều CLIENT → SERVER không đi qua lớp này; nó là {@code @MessageMapping} trong
 * {@code MessageStompController} ({@code /app/message.delivered}, {@code /app/message.read}).
 *
 * <p>Payload serialize bằng ObjectMapper của Spring, tức là đã có sẵn định dạng ngày của
 * {@link com.nguyenvu.lopet.config.JacksonConfig} — không còn cầu Jackson riêng như thời
 * netty-socketio.
 */
@Component
@RequiredArgsConstructor
public class RealtimeGateway {

    public static final String CHANNEL_CHAT = "chat";
    public static final String CHANNEL_NOTIFICATION = "notification";
    public static final String CHANNEL_MESSAGE_STATUS = "message-status";

    /** Tiền tố của destination riêng-người-dùng; {@link StompAuthChannelInterceptor} soi theo nó */
    public static final String USER_TOPIC_PREFIX = "/topic/user.";
    public static final String NOTIFICATION_TOPIC_PREFIX = "/topic/notification.";

    private final SimpMessagingTemplate messagingTemplate;

    public static String userTopic(Object accountId, String channel) {
        return USER_TOPIC_PREFIX + accountId + "/" + channel;
    }

    public static String notificationTopic(Object notificationId) {
        return NOTIFICATION_TOPIC_PREFIX + notificationId;
    }

    public void emit(String destination, Object payload) {
        messagingTemplate.convertAndSend(destination, payload);
    }
}
