package com.umatter.notification.consumer;

import com.umatter.notification.dto.StreakMilestoneEvent;
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
public class TrackingNotificationConsumer {

    private static final String SCOPE = "streak.milestone";

    private final IdempotencyService idempotency;
    private final NotificationHistoryService history;
    private final DeviceTokenService deviceTokens;
    private final PushNotificationDispatcherService pushDispatcher;

    public TrackingNotificationConsumer(IdempotencyService idempotency,
                                        NotificationHistoryService history,
                                        DeviceTokenService deviceTokens,
                                        PushNotificationDispatcherService pushDispatcher) {
        this.idempotency = idempotency;
        this.history = history;
        this.deviceTokens = deviceTokens;
        this.pushDispatcher = pushDispatcher;
    }

    @RabbitListener(queues = "${notification.rabbit.tracking.queue}", errorHandler = "rabbitListenerErrorHandler")
    public void onStreakMilestone(@Valid StreakMilestoneEvent event) {
        if (!idempotency.tryAcquire(SCOPE, event.getMessageId())) {
            return;
        }
        log.info("Processing streak milestone: profileId={} streak={} milestone={}",
                event.getProfileId(), event.getStreakCount(), event.getMilestoneName());

        UUID profileId = UUID.fromString(event.getProfileId());
        String title = "You hit a " + event.getMilestoneName() + " streak!";
        String body = "Day " + event.getStreakCount() + ". Keep going — your future self thanks you.";

        history.save(profileId, NotificationType.STREAK, title, body);

        Map<String, String> data = Map.of(
                "type", "streak.milestone",
                "profileId", event.getProfileId(),
                "streakCount", String.valueOf(event.getStreakCount()),
                "milestoneName", event.getMilestoneName());

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
                // One dead device should not fail the whole event. Inbox row is already saved.
                log.warn("Push failed for profileId={} platform={} — continuing", profileId, d.getPlatform(), ex);
            }
        }
    }
}
