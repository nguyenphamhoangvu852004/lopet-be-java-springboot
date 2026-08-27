package com.nguyenvu.lopet.message;

import java.security.Principal;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import com.nguyenvu.lopet.message.dto.MessageDtos;
import com.nguyenvu.lopet.realtime.WebSocketPrincipal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Hai ack đến từ người nhận, khép kín vòng "đã gửi → đã nhận → đã xem".
 *
 * <p>Nằm trong package {@code message} chứ không phải {@code realtime}: chiều nghiệp vụ → realtime
 * là một chiều, {@code realtime} không được biết tới {@code MessageService}. Bản netty-socketio phải
 * dựng riêng một SPI ({@code SocketEventRegistrar}) để giữ chiều đó; với {@code @MessageMapping} thì
 * Spring tự quét, không cần lớp trung gian nào.
 *
 * <p><b>Vì sao ack đi qua WebSocket chứ không chỉ qua REST</b>: "đã nhận" phải chứng minh tin đã tới
 * một thiết bị đang mở, và chính kết nối realtime là thứ đưa tin tới. Suy ra DELIVERED từ việc có
 * người đang subscribe {@code /topic/user.<id>/chat} thì chỉ chứng minh có kết nối sống, không chứng
 * minh gói tin tới nơi. REST vẫn còn hai endpoint tương đương ở {@link MessageController} làm đường
 * lui cho client không dùng WebSocket.
 *
 * <p><b>Danh tính KHÔNG lấy từ payload</b>. Nguồn duy nhất là {@link WebSocketPrincipal} mà
 * {@code StompAuthChannelInterceptor} gắn vào phiên sau khi kiểm JWT ở frame CONNECT. Nhận id từ
 * payload thì bất kỳ ai cũng đóng giả người khác được.
 *
 * <p>Lỗi được nuốt và ghi log thay vì ném ra: exception thoát khỏi {@code @MessageMapping} sẽ thành
 * một frame ERROR gửi về client — mất cả phiên chat chỉ vì một ack hỏng là cái giá quá đắt cho thao
 * tác phụ này.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class MessageStompController {

    private final MessageService messageService;
    private final MessageStatusNotifier messageStatusNotifier;

    @MessageMapping("/message.delivered")
    public void onDelivered(@Payload MessageDtos.DeliveredAckRequest payload, Principal principal) {
        handle(principal, "message.delivered",
                receiverId -> messageService.markDelivered(receiverId, payload.messageIds()));
    }

    @MessageMapping("/message.read")
    public void onRead(@Payload MessageDtos.ReadAckRequest payload, Principal principal) {
        handle(principal, "message.read", readerId -> {
            if (payload.partnerId() == null) {
                return null;
            }
            return messageService.markConversationRead(readerId, payload.partnerId());
        });
    }

    private void handle(Principal principal, String destination, StatusAction action) {
        Integer userId = WebSocketPrincipal.accountIdOf(principal);
        if (userId == null) {
            // Không tới được: interceptor đã chặn mọi frame chưa xác thực. Giữ lại như lưới cuối.
            log.debug("Bỏ qua '{}' từ kết nối chưa xác thực", destination);
            return;
        }
        try {
            MessageDtos.StatusUpdateResult result = action.apply(userId);
            if (result != null) {
                // Ngoài transaction của service — sự kiện chỉ rời server sau khi dữ liệu đã commit
                messageStatusNotifier.broadcast(result);
            }
        } catch (RuntimeException exception) {
            log.warn("Xử lý '{}' của user {} thất bại: {}", destination, userId, exception.getMessage());
        }
    }

    @FunctionalInterface
    private interface StatusAction {
        MessageDtos.StatusUpdateResult apply(Integer userId);
    }
}
