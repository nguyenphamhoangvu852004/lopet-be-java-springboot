package com.nguyenvu.lopet.message;

import org.springframework.stereotype.Component;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.nguyenvu.lopet.message.dto.MessageDtos;
import com.nguyenvu.lopet.realtime.RealtimeGateway;
import com.nguyenvu.lopet.realtime.SocketEventRegistrar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Hai sự kiện ack đến từ người nhận, khép kín vòng "đã gửi → đã nhận → đã xem".
 *
 * <p><b>Vì sao ack đi qua socket chứ không chỉ qua REST</b>: "đã nhận" phải chứng minh tin đã tới
 * một thiết bị đang mở, và chính socket là thứ đưa tin tới. Suy ra DELIVERED từ việc phòng
 * {@code user_<id>} có người thì chỉ chứng minh có kết nối sống, không chứng minh gói tin tới nơi —
 * client vẫn có thể chết ngay giữa lúc emit. REST vẫn còn hai endpoint tương đương ở
 * {@link MessageController} làm đường lui cho client không dùng socket.
 *
 * <p><b>Danh tính KHÔNG lấy từ payload</b>. Không có {@code CurrentUser} ở đây: handler chạy trên
 * event loop của Netty chứ không phải trong một HTTP request, nên {@code SecurityContextHolder}
 * rỗng. Nguồn duy nhất là {@code userId} mà {@code SocketIoConfig} đã gắn vào client sau khi kiểm
 * JWT. Nhận id từ payload thì bất kỳ ai cũng đóng giả người khác được.
 *
 * <p>Lỗi được nuốt và ghi log thay vì ném ra: exception thoát khỏi listener của netty-socketio sẽ
 * rơi vào ExceptionListener mặc định và có thể ngắt kết nối — mất cả phiên chat chỉ vì một ack hỏng
 * là cái giá quá đắt cho thao tác phụ này.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageSocketHandlers implements SocketEventRegistrar {

    private final MessageService messageService;
    private final MessageStatusNotifier messageStatusNotifier;

    @Override
    public void register(SocketIOServer server) {
        server.addEventListener(RealtimeGateway.CLIENT_EVENT_MESSAGE_DELIVERED,
                MessageDtos.DeliveredAckRequest.class,
                (client, payload, ack) -> handle(client, "message delivered",
                        receiverId -> messageService.markDelivered(receiverId, payload.getMessageIds())));

        server.addEventListener(RealtimeGateway.CLIENT_EVENT_MESSAGE_READ,
                MessageDtos.ReadAckRequest.class,
                (client, payload, ack) -> handle(client, "message read", readerId -> {
                    if (payload.getPartnerId() == null) {
                        return null;
                    }
                    return messageService.markConversationRead(readerId, payload.getPartnerId());
                }));
    }

    private void handle(SocketIOClient client, String event, StatusAction action) {
        Integer userId = authenticatedUserId(client);
        if (userId == null) {
            log.debug("Bỏ qua '{}' từ kết nối chưa xác thực {}", event, client.getSessionId());
            return;
        }
        try {
            MessageDtos.StatusUpdateResult result = action.apply(userId);
            if (result != null) {
                // Ngoài transaction của service — sự kiện chỉ rời server sau khi dữ liệu đã commit
                messageStatusNotifier.broadcast(result);
            }
        } catch (RuntimeException exception) {
            log.warn("Xử lý '{}' của user {} thất bại: {}", event, userId, exception.getMessage());
        }
    }

    @FunctionalInterface
    private interface StatusAction {
        MessageDtos.StatusUpdateResult apply(Integer userId);
    }
}
