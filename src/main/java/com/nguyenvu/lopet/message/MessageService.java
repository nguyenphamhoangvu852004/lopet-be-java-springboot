package com.nguyenvu.lopet.message;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.message.dto.MessageDtos;
import com.nguyenvu.lopet.message.entity.Message;
import com.nguyenvu.lopet.message.entity.MessageStatus;
import com.nguyenvu.lopet.message.repository.MessageRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final AccountRepository accountRepository;

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

    @Transactional
    public MessageDtos.ChangeStatusResponse changeStatus(Integer id, MessageStatus status) {
        Message message = messageRepository.findDetailById(id)
                .orElseThrow(() -> new NotFoundException("Message not found"));
        message.setStatus(status);
        messageRepository.save(message);
        return new MessageDtos.ChangeStatusResponse(true, "Update message successfully");
    }

    /**
     * Trả về CHÍNH dữ liệu đầu vào chứ không phải bản ghi vừa lưu — đúng như bản TS. Nghĩa là client
     * không nhận được id tin nhắn từ response này, và payload socket cũng mang đúng hình dạng đó.
     */
    @Transactional
    public MessageDtos.CreateMessageResponse create(String senderId, String receiverId, String content,
                                                     String imageUrl) {
        Account sender = requireAccount(Integer.valueOf(senderId), "Sender not found");
        Account receiver = requireAccount(Integer.valueOf(receiverId), "Receiver not found");

        messageRepository.save(Message.builder()
                .content(content)
                .sender(sender)
                .receiver(receiver)
                .mediaUrl(imageUrl == null ? "" : imageUrl)
                .status(MessageStatus.SENT)
                .build());

        return new MessageDtos.CreateMessageResponse(senderId, receiverId, content, imageUrl);
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
                message.getCreatedAt(), message.getStatus());
    }
}
