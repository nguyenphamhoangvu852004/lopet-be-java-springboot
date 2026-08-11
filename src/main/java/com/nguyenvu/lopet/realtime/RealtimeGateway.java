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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeGateway {

    public static final String EVENT_CHAT_MESSAGE = "chat messsage";
    public static final String EVENT_NOTIFICATION = "notification";
    public static final String EVENT_CHANGE_STATUS = "change status";

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
