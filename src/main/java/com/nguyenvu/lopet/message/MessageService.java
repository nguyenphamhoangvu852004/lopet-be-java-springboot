package com.nguyenvu.lopet.message;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.message.dto.MessageDtos;
import com.nguyenvu.lopet.message.entity.Message;
import com.nguyenvu.lopet.message.entity.MessageStatus;
import com.nguyenvu.lopet.message.repository.MessageRepository;
import com.nguyenvu.lopet.notification.NotificationPublisher;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final AccountRepository accountRepository;
    private final NotificationPublisher notificationPublisher;

    @Transactional(readOnly = true)
    public List<MessageDtos.MessageResponse> getConversation(Integer callerId, Integer targetId) {
        requireAccount(callerId, "Sender not found");
        requireAccount(targetId, "Receiver not found");

        return messageRepository.findConversation(callerId, targetId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Nhánh "không tìm thấy" ở đây không tới được qua route: guard tham gia hội thoại đã nạp tin
     * nhắn trước và trả 404 nếu không có. Vẫn giữ để service dùng độc lập vẫn an toàn.
     */
    @Transactional(readOnly = true)
    public MessageDtos.MessageResponse getDetail(Integer id) {
        return toResponse(messageRepository.findDetailById(id)
                .orElseThrow(() -> new NotFoundException("Message not found")));
    }

    /**
     * Đổi trạng thái MỘT tin nhắn.
     *
     * <p>Chặt hơn bản cũ ở hai điểm, và cả hai đều là lỗ hổng chứ không phải lựa chọn thiết kế:
     * <ul>
     *   <li>chỉ NGƯỜI NHẬN được đánh dấu — {@link MessageAccessGuard} chỉ kiểm "là một trong hai
     *       bên", nên trước đây người gửi tự đặt tin của mình thành READ và giả được dấu đã xem;</li>
     *   <li>không lùi trạng thái — nếu không, người nhận gửi một request là xoá sạch dấu đã xem
     *       vừa để lại.</li>
     * </ul>
     *
     * <p>Yêu cầu lùi trạng thái KHÔNG ném lỗi mà trả về kết quả rỗng: client thường gửi lại ack sau
     * mỗi lần reconnect, và một tin đã READ nhận lại ack DELIVERED là chuyện bình thường chứ không
     * phải lỗi cần báo cho người dùng.
     */
    @Transactional
    public MessageDtos.StatusUpdateResult changeStatus(Integer actorId, Integer id, MessageStatus status) {
        Message message = messageRepository.findDetailById(id)
                .orElseThrow(() -> new NotFoundException("Message not found"));

        if (message.getReceiver() == null || !message.getReceiver().getId().equals(actorId)) {
            throw new ForbiddenException("Chỉ người nhận mới đổi được trạng thái tin nhắn");
        }
        if (message.getStatus().isAtLeast(status)) {
            return MessageDtos.StatusUpdateResult.empty(message.getStatus(), actorId);
        }

        List<MessageDtos.StatusChange> changes =
                List.of(new MessageDtos.StatusChange(message.getId(), message.getSender().getId()));
        return applyStatus(changes, status, actorId);
    }

    /**
     * Ack "đã nhận" theo lô — người nhận báo lại rằng những tin này đã tới thiết bị của họ.
     *
     * <p>Id không hợp lệ (của người khác, đã xoá, hoặc đã DELIVERED/READ rồi) bị lọc âm thầm ở
     * {@code findPendingDelivery} thay vì ném lỗi. Ack là thông tin một chiều từ client: nó có thể
     * gửi trùng sau mỗi lần reconnect, và một lô hỗn hợp không nên làm hỏng cả request.
     */
    @Transactional
    public MessageDtos.StatusUpdateResult markDelivered(Integer receiverId, Collection<Integer> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return MessageDtos.StatusUpdateResult.empty(MessageStatus.DELIVERED, receiverId);
        }
        return applyStatus(
                toChanges(messageRepository.findPendingDelivery(messageIds, receiverId, MessageStatus.SENT)),
                MessageStatus.DELIVERED, receiverId);
    }

    /**
     * Đánh dấu đã xem TOÀN BỘ hội thoại với một người, trong một câu UPDATE.
     *
     * <p>Đây là ngữ nghĩa đúng của giao diện chat: người dùng mở cuộc trò chuyện là thấy hết, không
     * ai đọc lẻ từng tin. Làm theo từng id thì client phải gọi N request cho một lần mở hội thoại.
     */
    @Transactional
    public MessageDtos.StatusUpdateResult markConversationRead(Integer readerId, Integer partnerId) {
        requireAccount(partnerId, "Sender not found");
        return applyStatus(
                toChanges(messageRepository.findPendingRead(readerId, partnerId, MessageStatus.READ)),
                MessageStatus.READ, readerId);
    }

    /**
     * Id những tin đang chờ người này ack "đã nhận".
     *
     * <p>CHỈ đọc, không tự đánh dấu. Việc đánh dấu vẫn phải do client phát ack như mọi đường khác —
     * xem {@link MessageStompController}: server tự suy ra DELIVERED chỉ vì thấy có kết nối sống thì
     * dấu "đã nhận" mất hết ý nghĩa. Ở đây client tải danh sách id về máy mình rồi mới ack, nên nó
     * ack đúng thứ nó thật sự cầm trong tay.
     */
    @Transactional(readOnly = true)
    public List<Integer> awaitingDelivery(Integer receiverId) {
        return messageRepository.findAwaitingDelivery(receiverId, MessageStatus.SENT).stream()
                .map(MessageRepository.StatusTarget::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countUnread(Integer accountId) {
        return messageRepository.countUnread(accountId, MessageStatus.READ);
    }

    /** Chọn ứng viên rồi mới UPDATE theo đúng danh sách id đó — xem {@code MessageRepository.StatusTarget} */
    private MessageDtos.StatusUpdateResult applyStatus(List<MessageDtos.StatusChange> changes,
                                                        MessageStatus status, Integer actorId) {
        if (changes.isEmpty()) {
            return MessageDtos.StatusUpdateResult.empty(status, actorId);
        }
        LocalDateTime now = LocalDateTime.now();
        List<Integer> ids = changes.stream().map(MessageDtos.StatusChange::messageId).toList();

        if (status == MessageStatus.READ) {
            messageRepository.applyRead(ids, status, now);
        } else {
            messageRepository.applyDelivered(ids, status, now);
        }
        return new MessageDtos.StatusUpdateResult(status, now, actorId, changes);
    }

    private List<MessageDtos.StatusChange> toChanges(List<MessageRepository.StatusTarget> targets) {
        return targets.stream()
                .map(target -> new MessageDtos.StatusChange(target.getId(), target.getSenderId()))
                .toList();
    }

    /**
     * Bốn trường đầu của response là CHÍNH dữ liệu đầu vào, giữ nguyên hình dạng của bản TS; ba
     * trường cuối lấy từ bản ghi vừa lưu. {@code id} là bắt buộc cho luồng trạng thái: người nhận
     * cần nó để ack "đã nhận", người gửi cần nó để biết sự kiện {@code message status} nói về tin
     * nào trên màn hình.
     */
    @Transactional
    public MessageDtos.CreateMessageResponse create(String senderId, String receiverId, String content,
                                                     String imageUrl) {
        Account sender = requireAccount(Integer.valueOf(senderId), "Sender not found");
        Account receiver = requireAccount(Integer.valueOf(receiverId), "Receiver not found");

        Message saved = messageRepository.save(Message.builder()
                .content(content)
                .sender(sender)
                .receiver(receiver)
                .mediaUrl(imageUrl == null ? "" : imageUrl)
                .status(MessageStatus.SENT)
                .build());

        // Thông báo sinh ở đây chứ không ở controller: cùng transaction với bản ghi tin nhắn, nên
        // không bao giờ có thông báo trỏ tới một tin chưa được lưu.
        notificationPublisher.messageSent(sender.getId(), receiver.getId(), saved.getId());

        return new MessageDtos.CreateMessageResponse(senderId, receiverId, content, imageUrl,
                saved.getId(), saved.getStatus(), saved.getCreatedAt());
    }

    private Account requireAccount(Integer id, String message) {
        if (id == null) {
            throw new BadRequestException(message);
        }
        return accountRepository.findById(id).orElseThrow(() -> new NotFoundException(message));
    }

    private MessageDtos.MessageResponse toResponse(Message message) {
        return new MessageDtos.MessageResponse(message.getId(), message.getSender().getId(),
                message.getReceiver().getId(), message.getContent(), message.getMediaUrl(),
                message.getCreatedAt(), message.getStatus(),
                message.getDeliveredAt(), message.getReadAt());
    }
}
