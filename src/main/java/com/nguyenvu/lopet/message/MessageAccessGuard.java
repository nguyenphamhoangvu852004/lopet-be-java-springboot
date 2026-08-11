package com.nguyenvu.lopet.message;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.message.entity.Message;
import com.nguyenvu.lopet.message.repository.MessageRepository;
import com.nguyenvu.lopet.security.authz.OwnershipGuard;

import lombok.RequiredArgsConstructor;

/**
 * Tin nhắn không có "một chủ sở hữu": cả người gửi lẫn người nhận đều là bên hợp lệ.
 *
 * <p>ADMIN cũng KHÔNG được bỏ qua — nội dung tin nhắn riêng tư không phải thứ kiểm duyệt viên tự ý
 * đọc được. Muốn xử lý vi phạm thì đi qua luồng báo cáo.
 *
 * <p>Trước bản vá, hai endpoint tin nhắn chỉ có verifyToken và service cũng không đối chiếu người
 * gọi: bất kỳ ai đăng nhập cũng đọc được nội dung tin nhắn riêng tư của người khác chỉ bằng cách
 * đoán id, và đổi được cả trạng thái của nó.
 */
@Component
@RequiredArgsConstructor
public class MessageAccessGuard {

    private final MessageRepository messageRepository;

    @Transactional(readOnly = true)
    public void requireParticipant(Integer messageId) {
        OwnershipGuard.check(
                () -> messageRepository.findDetailById(messageId).orElse(null),
                (Message message) -> OwnershipGuard.owners(
                        message.getSender() == null ? null : message.getSender().getId(),
                        message.getReceiver() == null ? null : message.getReceiver().getId()),
                OwnershipGuard.NO_BYPASS);
    }
}
