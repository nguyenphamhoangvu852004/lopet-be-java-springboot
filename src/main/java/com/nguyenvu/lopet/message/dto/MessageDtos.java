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

    public record CreateMessageResponse(String senderId, String receiverId, String content, String imageUrl,
                                        Integer id, MessageStatus status, LocalDateTime createdAt) {
    }

    public record ChangeStatusRequest(String status) {
    }

    public record ChangeStatusResponse(boolean success, String message) {
    }

    public record ChatMessageEvent(CreateMessageResponse message, String from) {
    }

    public record DeliveredAckRequest(List<Integer> messageIds) {
    }

    public record ReadAckRequest(Integer partnerId) {
    }

    public record MessageStatusEvent(List<Integer> messageIds, MessageStatus status,
                                     LocalDateTime at, Integer byUserId) {
    }

    public record StatusChange(Integer messageId, Integer senderId) {
    }

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
