package com.nguyenvu.lopet.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RealtimeGateway {

    public static final String CHANNEL_CHAT = "chat";
    public static final String CHANNEL_NOTIFICATION = "notification";
    public static final String CHANNEL_MESSAGE_STATUS = "message-status";

    public static final String USER_TOPIC_PREFIX = "/topic/user.";
    public static final String NOTIFICATION_TOPIC_PREFIX = "/topic/notification.";

    private final SimpMessagingTemplate messagingTemplate;

    public static String userTopic(Object accountId, String channel) {
        return USER_TOPIC_PREFIX + accountId + "/" + channel;
    }

    public static String notificationTopic(Object notificationId) {
        return NOTIFICATION_TOPIC_PREFIX + notificationId;
    }

    public void emit(String destination, Object payload) {
        messagingTemplate.convertAndSend(destination, payload);
    }
}
