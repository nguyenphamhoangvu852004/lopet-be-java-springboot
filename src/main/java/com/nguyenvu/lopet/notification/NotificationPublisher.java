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

/**
 * Nơi DUY NHẤT sinh thông báo. Module nghiệp vụ (post, comment, message, friendship) gọi vào đây
 * ngay trong transaction của hành động vừa xảy ra.
 *
 * <p><b>Vì sao chuyển về backend</b>: trước đây frontend tự gọi {@code POST /v1/notifications} sau
 * mỗi hành động. Hệ quả là (1) nội dung và loại thông báo do client tự khai, nên bất kỳ ai đăng
 * nhập cũng gửi được thông báo nội dung tuỳ ý cho bất kỳ người dùng nào; (2) hành động thành công
 * mà request thông báo hỏng thì thông báo mất luôn, không ai biết; (3) mỗi client mới lại phải nhớ
 * gọi đủ 5 chỗ, quên chỗ nào thì mất thông báo ở đó.
 *
 * <p><b>Câu chữ nằm ở đây, không nhận từ tham số</b>. Cùng một loại thì luôn cùng một câu, và đọc
 * một chỗ này là biết hết hệ thống nói gì với người dùng.
 *
 * <p><b>Ghi trong transaction, bắn socket sau commit</b>. Bản ghi đi cùng số phận với hành động
 * sinh ra nó — thích bài mà rollback thì không còn thông báo mồ côi nào. Còn sự kiện realtime thì
 * {@link NotificationSocketRelay} đẩy đi sau khi commit, nên người nhận không bao giờ thấy một
 * thông báo mà dữ liệu phía sau nó chưa tồn tại.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    private final NotificationRepository notificationRepository;
    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher events;

    /** Ai đó thích bài viết — {@code postId} để giao diện mở đúng bài */
    @Transactional
    public void postLiked(Integer actorId, Integer postAuthorId, Integer postId) {
        publish(actorId, postAuthorId, NotificationObjectType.POST_LIKE, postId);
    }

    /** Ai đó bình luận bài viết */
    @Transactional
    public void postCommented(Integer actorId, Integer postAuthorId, Integer postId) {
        publish(actorId, postAuthorId, NotificationObjectType.POST_COMMENT, postId);
    }

    /** Có tin nhắn mới — giao diện mở hội thoại với {@code actorId}, {@code messageId} chỉ để tra cứu */
    @Transactional
    public void messageSent(Integer actorId, Integer receiverId, Integer messageId) {
        publish(actorId, receiverId, NotificationObjectType.MESSAGE, messageId);
    }

    /** Lời mời kết bạn mới */
    @Transactional
    public void friendRequested(Integer actorId, Integer receiverId) {
        publish(actorId, receiverId, NotificationObjectType.FRIEND_REQUEST, actorId);
    }

    /** Lời mời kết bạn được chấp nhận — người nhận thông báo là người ĐÃ GỬI lời mời */
    @Transactional
    public void friendAccepted(Integer actorId, Integer requesterId) {
        publish(actorId, requesterId, NotificationObjectType.FRIEND_ACCEPTED, actorId);
    }

    /**
     * Ai đó xin vào nhóm PRIVATE. Gọi một lần cho MỖI quản trị nhóm: thông báo là bản ghi
     * một-người-nhận, không có khái niệm gửi cho một nhóm người.
     */
    @Transactional
    public void groupJoinRequested(Integer actorId, Integer managerAccountId, Integer groupId) {
        publish(actorId, managerAccountId, NotificationObjectType.GROUP_JOIN_REQUESTED, groupId);
    }

    /** Yêu cầu vào nhóm được duyệt — người nhận là người đã xin vào */
    @Transactional
    public void groupJoinApproved(Integer actorId, Integer requesterAccountId, Integer groupId) {
        publish(actorId, requesterAccountId, NotificationObjectType.GROUP_JOIN_APPROVED, groupId);
    }

    /** Lời mời vào nhóm — người nhận là người được mời */
    @Transactional
    public void groupInvited(Integer actorId, Integer inviteeAccountId, Integer groupId) {
        publish(actorId, inviteeAccountId, NotificationObjectType.GROUP_INVITED, groupId);
    }

    /** Lời mời vào nhóm được chấp nhận — người nhận là người đã mời */
    @Transactional
    public void groupInviteAccepted(Integer actorId, Integer inviterAccountId, Integer groupId) {
        publish(actorId, inviterAccountId, NotificationObjectType.GROUP_INVITE_ACCEPTED, groupId);
    }

    private void publish(Integer actorId, Integer receptorId, NotificationObjectType type,
            Integer objectId) {
        // Tự thích bài mình, tự nhắn cho mình: không ai cần được báo về việc mình vừa làm
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

    /**
     * Câu chữ hiển thị cho người nhận. Giữ nguyên văn phong của các chuỗi frontend từng gửi lên, để
     * thông báo cũ và mới đọc lên vẫn là một giọng.
     */
    private String contentOf(NotificationObjectType type, String actorName) {
        String actor = actorName == null || actorName.isBlank() ? "Ai đó" : actorName;
        return switch (type) {
            case POST_LIKE -> actor + " đã thích bài viết của bạn";
            case POST_COMMENT -> actor + " đã bình luận bài viết của bạn";
            case MESSAGE -> actor + " đã gửi cho bạn một tin nhắn";
            case FRIEND_REQUEST -> actor + " đã gửi cho bạn lời mời kết bạn";
            case FRIEND_ACCEPTED -> actor + " đã chấp nhận lời mời kết bạn";
            case GROUP_JOIN_REQUESTED -> actor + " muốn tham gia nhóm của bạn";
            case GROUP_JOIN_APPROVED -> actor + " đã duyệt yêu cầu tham gia nhóm của bạn";
            case GROUP_INVITED -> actor + " đã mời bạn tham gia một nhóm";
            case GROUP_INVITE_ACCEPTED -> actor + " đã chấp nhận lời mời tham gia nhóm";
            // Không sinh mới bao giờ; nhánh này chỉ để switch phủ hết enum
            case POST -> actor + " có một hoạt động mới";
        };
    }
}
