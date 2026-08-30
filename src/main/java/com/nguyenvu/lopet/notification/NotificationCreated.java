package com.nguyenvu.lopet.notification;

import java.time.LocalDateTime;

import com.nguyenvu.lopet.notification.entity.NotificationObjectType;

public record NotificationCreated(
        Integer notificationId,
        Integer actorId,
        Integer receptorId,
        String content,
        NotificationObjectType objectType,
        Integer objectId,
        LocalDateTime createdAt) {
}
