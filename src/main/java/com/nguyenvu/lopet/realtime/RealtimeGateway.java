package com.nguyenvu.lopet.realtime;

import org.springframework.stereotype.Component;

import com.corundumstudio.socketio.SocketIOServer;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Điểm phát sự kiện realtime duy nhất — thay cho {@code res.io.to(room).emit(...)} của Express.
 *
 * <p>Tên phòng và tên sự kiện là HỢP ĐỒNG với client, không phải chi tiết nội bộ:
 * <ul>
 *   <li>phòng {@code user_<accountId>} — mỗi kết nối tự vào phòng của mình khi handshake xong</li>
 *   <li>phòng {@code object_<notificationId>} — dùng cho sự kiện đổi trạng thái thông báo</li>
 *   <li>sự kiện {@code chat messsage} — viết sai chính tả (ba chữ s) trong bản gốc; client đang
 *       lắng nghe đúng chuỗi đó nên sửa lại là làm hỏng chat</li>
 * </ul>
 *
 * <p>Luồng trạng thái tin nhắn ("đã gửi / đã nhận / đã xem") dùng ba sự kiện, và khác với những
 * sự kiện trên ở chỗ có cả chiều CLIENT → SERVER:
 * <ul>
 *   <li>{@code message delivered} (client → server) — người nhận báo lô tin đã tới thiết bị;</li>
 *   <li>{@code message read} (client → server) — người nhận mở hội thoại với một người;</li>
 *   <li>{@code message status} (server → NGƯỜI GỬI) — kết quả của hai sự kiện trên, gộp theo người
 *       gửi để một lần đọc cả hội thoại chỉ tốn một sự kiện.</li>
 * </ul>
 * Hai sự kiện đến được đăng ký bởi {@code MessageSocketHandlers} qua {@link SocketEventRegistrar}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeGateway {

    public static final String EVENT_CHAT_MESSAGE = "chat messsage";
    public static final String EVENT_NOTIFICATION = "notification";
    public static final String EVENT_CHANGE_STATUS = "change status";
    public static final String EVENT_MESSAGE_STATUS = "message status";
    public static final String CLIENT_EVENT_MESSAGE_DELIVERED = "message delivered";
    public static final String CLIENT_EVENT_MESSAGE_READ = "message read";

    private final SocketIOServer socketIOServer;

    public static String userRoom(Object accountId) {
        return "user_" + accountId;
    }

    public static String objectRoom(Object objectId) {
        return "object_" + objectId;
    }

    public void emit(String room, String event, Object payload) {
        socketIOServer.getRoomOperations(room).sendEvent(event, payload);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Đang dừng Socket.IO server");
    }
}
