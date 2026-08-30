package com.nguyenvu.lopet.message;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.nguyenvu.lopet.message.dto.MessageDtos;
import com.nguyenvu.lopet.realtime.RealtimeGateway;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MessageStatusNotifier {

    private final RealtimeGateway realtimeGateway;

    public void broadcast(MessageDtos.StatusUpdateResult result) {
        if (result.isEmpty()) {
            return;
        }
        Map<Integer, List<Integer>> theoNguoiGui = result.changes().stream()
                .collect(Collectors.groupingBy(MessageDtos.StatusChange::senderId,
                        Collectors.mapping(MessageDtos.StatusChange::messageId, Collectors.toList())));

        theoNguoiGui.forEach((senderId, messageIds) -> realtimeGateway.emit(
                RealtimeGateway.userTopic(senderId, RealtimeGateway.CHANNEL_MESSAGE_STATUS),
                new MessageDtos.MessageStatusEvent(messageIds, result.status(), result.at(),
                        result.byUserId())));
    }
}
