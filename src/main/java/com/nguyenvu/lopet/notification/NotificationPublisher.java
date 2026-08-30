package com.nguyenvu.lopet.notification;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.notification.entity.Notification;
import com.nguyenvu.lopet.notification.entity.NotificationObjectType;
import com.nguyenvu.lopet.notification.entity.NotificationStatus;
import com.nguyenvu.lopet.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    private final NotificationRepository notificationRepository;
    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher events;

    @Transactional
    public void postLiked(Integer actorId, Integer postAuthorId, Integer postId) {
        publish(actorId, postAuthorId, NotificationObjectType.POST_LIKE, postId);
    }

    @Transactional
    public void postCommented(Integer actorId, Integer postAuthorId, Integer postId) {
        publish(actorId, postAuthorId, NotificationObjectType.POST_COMMENT, postId);
    }

    @Transactional
    public void messageSent(Integer actorId, Integer receiverId, Integer messageId) {
        publish(actorId, receiverId, NotificationObjectType.MESSAGE, messageId);
    }

    @Transactional
    public void friendRequested(Integer actorId, Integer receiverId) {
        publish(actorId, receiverId, NotificationObjectType.FRIEND_REQUEST, actorId);
    }

    @Transactional
    public void friendAccepted(Integer actorId, Integer requesterId) {
        publish(actorId, requesterId, NotificationObjectType.FRIEND_ACCEPTED, actorId);
    }

    private void publish(Integer actorId, Integer receptorId, NotificationObjectType type,
            Integer objectId) {
        if (actorId == null || receptorId == null || actorId.equals(receptorId)) {
            return;
        }
        Account actor = accountRepository.findById(actorId).orElse(null);
        Account receptor = accountRepository.findById(receptorId).orElse(null);
        if (actor == null || receptor == null) {
            log.warn("Bỏ qua thông báo {}: actor {} hoặc receptor {} không tồn tại", type, actorId,
                    receptorId);
            return;
        }

        Notification saved = notificationRepository.save(Notification.builder()
                .actor(actor)
                .receptor(receptor)
                .content(contentOf(type, actor.getUsername()))
                .objectType(type)
                .objectId(objectId)
                .status(NotificationStatus.SENT)
                .build());

        events.publishEvent(new NotificationCreated(saved.getId(), actor.getId(), receptor.getId(),
                saved.getContent(), type, objectId, saved.getCreatedAt()));
    }

    private String contentOf(NotificationObjectType type, String actorName) {
        String actor = actorName == null || actorName.isBlank() ? "Ai đó" : actorName;
        return switch (type) {
            case POST_LIKE -> actor + " đã thích bài viết của bạn";
            case POST_COMMENT -> actor + " đã bình luận bài viết của bạn";
            case MESSAGE -> actor + " đã gửi cho bạn một tin nhắn";
            case FRIEND_REQUEST -> actor + " đã gửi cho bạn lời mời kết bạn";
            case FRIEND_ACCEPTED -> actor + " đã chấp nhận lời mời kết bạn";
            case POST -> actor + " có một hoạt động mới";
        };
    }
}
