package com.nguyenvu.lopet.message.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.nguyenvu.lopet.message.entity.MessageStatus;

public final class MessageDtos {

    public record MessageResponse(
            Integer id,
            Integer senderId,
            Integer receiverId,
            String content,
            String mediaUrl,
            LocalDateTime createdAt,
            MessageStatus status,
            LocalDateTime deliveredAt,
            LocalDateTime readAt) {
    }

    /**
     * Bản TS trả về CHÍNH input DTO chứ không phải bản ghi vừa lưu — bốn trường đầu giữ nguyên hình
     * dạng đó, kể cả việc hai id là chuỗi đúng như client gửi lên.
     *
     * <p>Ba trường sau là phần THÊM VÀO, không thay thế gì cả: client cũ đọc theo tên khoá nên bỏ
     * qua được, còn client mới bắt buộc phải có {@code id} — không có nó thì không tồn tại khoá nào
     * để ack "đã nhận" hay để gắn trạng thái vào đúng bong bóng tin nhắn trên giao diện.
     */
    public record CreateMessageResponse(String senderId, String receiverId, String content, String imageUrl,
                                        Integer id, MessageStatus status, LocalDateTime createdAt) {
    }

    public record ChangeStatusRequest(String status) {
    }

    public record ChangeStatusResponse(boolean success, String message) {
    }

    /** Payload đẩy tới {@code /topic/user.<receiverId>/chat} */
    public record ChatMessageEvent(CreateMessageResponse message, String from) {
    }

    /**
     * Ack "đã nhận" do client gửi lên — qua WebSocket ({@code /app/message.delivered}) hoặc qua REST.
     *
     * <p>Là lô chứ không phải từng tin: khi mở lại app sau lúc offline, client nhận cả chục tin một
     * lúc và một request cho mỗi tin vừa tốn round-trip vừa sinh chục sự kiện dội ngược lại người gửi.
     */
    public record DeliveredAckRequest(List<Integer> messageIds) {
    }

    /**
     * Đánh dấu đã xem toàn bộ hội thoại với một người — payload của {@code /app/message.read}.
     */
    public record ReadAckRequest(Integer partnerId) {
    }

    /**
     * Sự kiện {@code message status} đẩy NGƯỢC về người gửi. Gộp theo người gửi nên một lần đánh dấu
     * đã xem cả hội thoại chỉ tốn một sự kiện chứ không phải mỗi tin một cái.
     *
     * @param byUserId người vừa nhận/xem — client dùng để bỏ qua sự kiện do chính mình gây ra
     */
    public record MessageStatusEvent(List<Integer> messageIds, MessageStatus status,
                                     LocalDateTime at, Integer byUserId) {
    }

    /** Một tin đã đổi trạng thái, kèm người cần được báo */
    public record StatusChange(Integer messageId, Integer senderId) {
    }

    /**
     * Kết quả một lần đánh dấu. Service trả về thay vì tự bắn socket để việc phát sự kiện xảy ra
     * SAU khi transaction commit — bắn từ trong transaction thì người gửi có thể thấy "đã xem"
     * trước cả lúc dữ liệu kịp vào database, và thấy nhầm nếu transaction rollback.
     */
    public record StatusUpdateResult(MessageStatus status, LocalDateTime at, Integer byUserId,
                                     List<StatusChange> changes) {

        public static StatusUpdateResult empty(MessageStatus status, Integer byUserId) {
            return new StatusUpdateResult(status, null, byUserId, List.of());
        }

        public boolean isEmpty() {
            return changes.isEmpty();
        }

        public int count() {
            return changes.size();
        }
    }

    public record MarkStatusResponse(int updated, MessageStatus status) {
    }

    public record UnreadCountResponse(long count) {
    }

    private MessageDtos() {
    }
}
