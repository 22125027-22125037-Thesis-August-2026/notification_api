package com.umatter.notification.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.umatter.notification.exception.NotificationDispatchException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class PushNotificationDispatcherService {

    private final FirebaseMessaging firebaseMessaging;

    public PushNotificationDispatcherService(@Autowired(required = false) @Nullable FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    public void sendPush(String deviceToken,
                         String title,
                         String body,
                         Map<String, String> data) {
        if (firebaseMessaging == null) {
            log.warn("FCM disabled — skipping push to token={} title={}", maskToken(deviceToken), title);
            return;
        }

        Message.Builder builder = Message.builder()
                .setToken(deviceToken)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build());

        if (data != null && !data.isEmpty()) {
            builder.putAllData(data);
        }

        try {
            String messageId = firebaseMessaging.send(builder.build());
            log.info("Push dispatched: token={} fcmId={} title={}", maskToken(deviceToken), messageId, title);
        } catch (FirebaseMessagingException ex) {
            throw new NotificationDispatchException(
                    "Failed to dispatch push notification to token " + maskToken(deviceToken), ex);
        }
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "***";
        }
        return token.substring(0, 6) + "…" + token.substring(token.length() - 4);
    }
}
