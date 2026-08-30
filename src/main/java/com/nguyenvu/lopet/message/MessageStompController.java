package com.nguyenvu.lopet.message;

import java.security.Principal;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import com.nguyenvu.lopet.message.dto.MessageDtos;
import com.nguyenvu.lopet.realtime.WebSocketPrincipal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MessageStompController {

    private final MessageService messageService;
    private final MessageStatusNotifier messageStatusNotifier;

    @MessageMapping("/message.delivered")
    public void onDelivered(@Payload MessageDtos.DeliveredAckRequest payload, Principal principal) {
        handle(principal, "message.delivered",
                receiverId -> messageService.markDelivered(receiverId, payload.messageIds()));
    }

    @MessageMapping("/message.read")
    public void onRead(@Payload MessageDtos.ReadAckRequest payload, Principal principal) {
        handle(principal, "message.read", readerId -> {
            if (payload.partnerId() == null) {
                return null;
            }
            return messageService.markConversationRead(readerId, payload.partnerId());
        });
    }

    private void handle(Principal principal, String destination, StatusAction action) {
        Integer userId = WebSocketPrincipal.accountIdOf(principal);
        if (userId == null) {
            log.debug("Bỏ qua '{}' từ kết nối chưa xác thực", destination);
            return;
        }
        try {
            MessageDtos.StatusUpdateResult result = action.apply(userId);
            if (result != null) {
                messageStatusNotifier.broadcast(result);
            }
        } catch (RuntimeException exception) {
            log.warn("Xử lý '{}' của user {} thất bại: {}", destination, userId, exception.getMessage());
        }
    }

    @FunctionalInterface
    private interface StatusAction {
        MessageDtos.StatusUpdateResult apply(Integer userId);
    }
}
