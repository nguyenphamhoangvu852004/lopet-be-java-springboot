package com.nguyenvu.lopet.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.nguyenvu.lopet.notification.dto.NotificationDtos;
import com.nguyenvu.lopet.realtime.RealtimeGateway;

import lombok.RequiredArgsConstructor;

/**
 * Đẩy thông báo vừa ghi tới phòng riêng của người nhận.
 *
 * <p>{@code AFTER_COMMIT} chứ không phải listener thường: bắn trong transaction thì người nhận có
 * thể thấy thông báo trước lúc dữ liệu kịp vào database — bấm vào là mở ra một bài viết chưa tồn
 * tại — và thấy nhầm hẳn nếu transaction rollback.
 */
@Component
@RequiredArgsConstructor
public class NotificationSocketRelay {

    private final RealtimeGateway realtimeGateway;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreated event) {
        realtimeGateway.emit(RealtimeGateway.userRoom(event.receptorId()),
                RealtimeGateway.EVENT_NOTIFICATION,
                new NotificationDtos.NotificationEvent(event.notificationId(), event.actorId(),
                        event.receptorId(), event.content(), event.objectType(), event.objectId(),
                        com.nguyenvu.lopet.notification.entity.NotificationStatus.SENT,
                        event.createdAt()));
    }
}
