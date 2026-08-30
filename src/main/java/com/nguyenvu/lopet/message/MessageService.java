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

    @Transactional(readOnly = true)
    public MessageDtos.MessageResponse getDetail(Integer id) {
        return toResponse(messageRepository.findDetailById(id)
                .orElseThrow(() -> new NotFoundException("Message not found")));
    }

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

    @Transactional
    public MessageDtos.StatusUpdateResult markDelivered(Integer receiverId, Collection<Integer> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return MessageDtos.StatusUpdateResult.empty(MessageStatus.DELIVERED, receiverId);
        }
        return applyStatus(
                toChanges(messageRepository.findPendingDelivery(messageIds, receiverId, MessageStatus.SENT)),
                MessageStatus.DELIVERED, receiverId);
    }

    @Transactional
    public MessageDtos.StatusUpdateResult markConversationRead(Integer readerId, Integer partnerId) {
        requireAccount(partnerId, "Sender not found");
        return applyStatus(
                toChanges(messageRepository.findPendingRead(readerId, partnerId, MessageStatus.READ)),
                MessageStatus.READ, readerId);
    }

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
