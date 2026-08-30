package com.nguyenvu.lopet.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.nguyenvu.lopet.notification.dto.NotificationDtos;
import com.nguyenvu.lopet.realtime.RealtimeGateway;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NotificationSocketRelay {

    private final RealtimeGateway realtimeGateway;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreated event) {
        realtimeGateway.emit(
                RealtimeGateway.userTopic(event.receptorId(), RealtimeGateway.CHANNEL_NOTIFICATION),
                new NotificationDtos.NotificationEvent(event.notificationId(), event.actorId(),
                        event.receptorId(), event.content(), event.objectType(), event.objectId(),
                        com.nguyenvu.lopet.notification.entity.NotificationStatus.SENT,
                        event.createdAt()));
    }
}
