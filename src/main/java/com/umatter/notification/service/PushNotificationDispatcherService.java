package com.umatter.notification.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.umatter.notification.exception.NotificationDispatchException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class PushNotificationDispatcherService {

    /**
     * FCM error codes that mean "this token is permanently dead, stop pushing
     * to it." When we see one of these we deregister the device row so we
     * don't keep dispatching to it on every event.
     */
    private static final Set<MessagingErrorCode> DEAD_TOKEN_CODES = EnumSet.of(
            MessagingErrorCode.UNREGISTERED,
            MessagingErrorCode.INVALID_ARGUMENT,
            MessagingErrorCode.SENDER_ID_MISMATCH);

    private final FirebaseMessaging firebaseMessaging;
    private final DeviceTokenService deviceTokenService;

    public PushNotificationDispatcherService(@Autowired(required = false) @Nullable FirebaseMessaging firebaseMessaging,
                                             DeviceTokenService deviceTokenService) {
        this.firebaseMessaging = firebaseMessaging;
        this.deviceTokenService = deviceTokenService;
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
            MessagingErrorCode code = ex.getMessagingErrorCode();
            if (code != null && DEAD_TOKEN_CODES.contains(code)) {
                purgeDeadToken(deviceToken, code);
            }
            throw new NotificationDispatchException(
                    "Failed to dispatch push notification to token " + maskToken(deviceToken), ex);
        }
    }

    private void purgeDeadToken(String deviceToken, MessagingErrorCode code) {
        try {
            boolean removed = deviceTokenService.deregister(deviceToken);
            log.info("Dead FCM token purged: token={} code={} removed={}",
                    maskToken(deviceToken), code, removed);
        } catch (Exception cleanupEx) {
            // Don't mask the original FCM failure if cleanup itself fails.
            log.error("Failed to deregister dead FCM token: token={} code={}",
                    maskToken(deviceToken), code, cleanupEx);
        }
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "***";
        }
        return token.substring(0, 6) + "…" + token.substring(token.length() - 4);
    }
}
