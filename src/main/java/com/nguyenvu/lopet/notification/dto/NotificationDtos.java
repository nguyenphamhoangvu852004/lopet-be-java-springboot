package com.nguyenvu.lopet.notification.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nguyenvu.lopet.notification.entity.NotificationObjectType;
import com.nguyenvu.lopet.notification.entity.NotificationStatus;

public final class NotificationDtos {

    public record CreateNotificationRequest(Integer receptorId, String content, String objectType) {
    }

    public record CreateNotificationResponse(
            Integer notificationId,
            Integer actorId,
            Integer receptorId,
            String content,
            NotificationObjectType objectType,
            Integer objectId,
            NotificationStatus status,
            LocalDateTime createdAt) {
    }

    public record NotificationEvent(
            Integer notificationId,
            Integer actorId,
            Integer receptorId,
            String content,
            NotificationObjectType objectType,
            Integer objectId,
            NotificationStatus status,
            LocalDateTime createdAt) {
    }

    public record NotificationListItem(
            Integer notificationId,
            Integer actorId,
            Integer receptorId,
            String content,
            LocalDateTime createdAt,
            NotificationStatus status,
            NotificationObjectType type,
            Integer objectId) {
    }

    public record UpdateNotificationRequest(String status) {
    }

    public record UpdateNotificationResponse(
            Integer notificationId,
            Integer actorId,
            Integer receptorId,
            String content,
            LocalDateTime createdAt,
            NotificationStatus status,
            NotificationObjectType type,
            Integer objectId) {
    }

    public record NotificationDetail(
            Integer notificationId,
            NotificationAccount actor,
            NotificationAccount receptor,
            String content,
            NotificationStatus status,
            NotificationObjectType type,
            Integer objectId,
            LocalDateTime createdAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) LocalDateTime updatedAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) LocalDateTime deletedAt) {
    }

    public record NotificationAccount(Integer id, String username, String email, NotificationProfile profile) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NotificationProfile(
            Integer id,
            String fullName,
            String phoneNumber,
            String bio,
            String avatarUrl,
            String coverUrl) {
    }

    private NotificationDtos() {
    }
}
