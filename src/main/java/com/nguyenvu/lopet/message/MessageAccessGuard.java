package com.nguyenvu.lopet.message;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.message.entity.Message;
import com.nguyenvu.lopet.message.repository.MessageRepository;
import com.nguyenvu.lopet.security.authz.OwnershipGuard;

import lombok.RequiredArgsConstructor;

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
