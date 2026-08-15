package com.nguyenvu.lopet.notification;

import java.time.LocalDateTime;

import com.nguyenvu.lopet.notification.entity.NotificationObjectType;

/**
 * Thông báo vừa được ghi. Sự kiện nội bộ Spring, không phải payload gửi ra ngoài —
 * {@link NotificationSocketRelay} mới là chỗ dịch nó sang payload socket.
 *
 * <p>Tồn tại để tách "ghi dữ liệu" khỏi "đẩy realtime": {@code NotificationPublisher} không cần
 * biết tới {@code RealtimeGateway}, và việc đẩy đi được hoãn tới sau khi transaction commit.
 */
public record NotificationCreated(
        Integer notificationId,
        Integer actorId,
        Integer receptorId,
        String content,
        NotificationObjectType objectType,
        Integer objectId,
        LocalDateTime createdAt) {
}
