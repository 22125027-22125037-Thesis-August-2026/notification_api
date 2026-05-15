package com.umatter.notification.consumer;

import com.umatter.notification.dto.MessageMissedEvent;
import com.umatter.notification.persistence.NotificationType;
import com.umatter.notification.service.NotificationHistoryService;
import com.umatter.notification.service.PushNotificationDispatcherService;
import com.umatter.notification.service.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class SocialNotificationConsumer {

    private static final String SCOPE = "message.missed";

    private final IdempotencyService idempotency;
    private final NotificationHistoryService history;
    private final PushNotificationDispatcherService pushDispatcher;

    public SocialNotificationConsumer(IdempotencyService idempotency,
                                      NotificationHistoryService history,
                                      PushNotificationDispatcherService pushDispatcher) {
        this.idempotency = idempotency;
        this.history = history;
        this.pushDispatcher = pushDispatcher;
    }

    @RabbitListener(queues = "${notification.rabbit.social.queue}", errorHandler = "rabbitListenerErrorHandler")
    public void onMessageMissed(@Valid MessageMissedEvent event) {
        if (!idempotency.tryAcquire(SCOPE, event.getMessageId())) {
            return;
        }
        log.info("Processing missed message: profileId={} channel={} sender={}",
                event.getProfileId(), event.getChannelId(), event.getSenderName());

        String title = "New message from " + event.getSenderName();
        String body = "Open the chat to read what you missed.";

        history.save(UUID.fromString(event.getProfileId()), NotificationType.CHAT, title, body);

        pushDispatcher.sendPush(
                event.getDeviceToken(),
                title,
                body,
                Map.of(
                        "type", "message.missed",
                        "profileId", event.getProfileId(),
                        "channelId", event.getChannelId(),
                        "senderName", event.getSenderName()
                )
        );
    }
}
