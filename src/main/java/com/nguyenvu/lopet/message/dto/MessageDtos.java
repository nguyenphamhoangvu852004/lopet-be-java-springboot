package com.nguyenvu.lopet.message.dto;

import java.time.LocalDateTime;

import com.nguyenvu.lopet.message.entity.MessageStatus;

public final class MessageDtos {

    public record MessageResponse(
            Integer id,
            Integer senderId,
            Integer receiverId,
            String content,
            String mediaUrl,
            LocalDateTime createdAt,
            MessageStatus status) {
    }

    public record CreateMessageRequest(String content, String receiverId) {
    }

    /**
     * Bản TS trả về CHÍNH input DTO chứ không phải bản ghi vừa lưu — nên response không có
     * {@code id}, không có {@code status}, không có {@code createdAt}, và hai id vẫn ở dạng chuỗi
     * đúng như client gửi lên. Đây cũng là payload được đẩy qua socket.
     */
    public record CreateMessageResponse(String senderId, String receiverId, String content, String imageUrl) {
    }

    public record ChangeStatusRequest(String status) {
    }

    public record ChangeStatusResponse(boolean success, String message) {
    }

    /** Payload của sự kiện socket {@code chat messsage} */
    public record ChatMessageEvent(CreateMessageResponse message, String from) {
    }

    private MessageDtos() {
    }
}
