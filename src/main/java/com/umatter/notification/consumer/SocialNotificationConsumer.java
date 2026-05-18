package com.umatter.notification.consumer;

import com.umatter.notification.dto.MessageMissedEvent;
import com.umatter.notification.exception.NotificationDispatchException;
import com.umatter.notification.persistence.DeviceToken;
import com.umatter.notification.persistence.NotificationType;
import com.umatter.notification.service.DeviceTokenService;
import com.umatter.notification.service.NotificationHistoryService;
import com.umatter.notification.service.PushNotificationDispatcherService;
import com.umatter.notification.service.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class SocialNotificationConsumer {

    private static final String SCOPE = "message.missed";

    private final IdempotencyService idempotency;
    private final NotificationHistoryService history;
    private final DeviceTokenService deviceTokens;
    private final PushNotificationDispatcherService pushDispatcher;

    public SocialNotificationConsumer(IdempotencyService idempotency,
                                      NotificationHistoryService history,
                                      DeviceTokenService deviceTokens,
                                      PushNotificationDispatcherService pushDispatcher) {
        this.idempotency = idempotency;
        this.history = history;
        this.deviceTokens = deviceTokens;
        this.pushDispatcher = pushDispatcher;
    }

    @RabbitListener(queues = "${notification.rabbit.social.queue}", errorHandler = "rabbitListenerErrorHandler")
    public void onMessageMissed(@Valid MessageMissedEvent event) {
        if (!idempotency.tryAcquire(SCOPE, event.getMessageId())) {
            return;
        }
        log.info("Processing missed message: profileId={} channel={} sender={}",
                event.getProfileId(), event.getChannelId(), event.getSenderName());

        UUID profileId = UUID.fromString(event.getProfileId());
        String title = "New message from " + event.getSenderName();
        String body = "Open the chat to read what you missed.";

        history.save(profileId, NotificationType.CHAT, title, body);

        Map<String, String> data = Map.of(
                "type", "message.missed",
                "profileId", event.getProfileId(),
                "channelId", event.getChannelId(),
                "senderName", event.getSenderName());

        fanOutPush(profileId, title, body, data);
    }

    private void fanOutPush(UUID profileId, String title, String body, Map<String, String> data) {
        List<DeviceToken> tokens = deviceTokens.findActiveTokensForProfile(profileId);
        if (tokens.isEmpty()) {
            log.info("No registered devices for profileId={} — inbox row saved, push skipped", profileId);
            return;
        }
        for (DeviceToken d : tokens) {
            try {
                pushDispatcher.sendPush(d.getDeviceToken(), title, body, data);
            } catch (NotificationDispatchException ex) {
                log.warn("Push failed for profileId={} platform={} — continuing", profileId, d.getPlatform(), ex);
            }
        }
    }
}
