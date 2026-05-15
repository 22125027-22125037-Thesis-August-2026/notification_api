package com.umatter.notification.consumer;

import com.umatter.notification.dto.StreakMilestoneEvent;
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
public class TrackingNotificationConsumer {

    private static final String SCOPE = "streak.milestone";

    private final IdempotencyService idempotency;
    private final NotificationHistoryService history;
    private final PushNotificationDispatcherService pushDispatcher;

    public TrackingNotificationConsumer(IdempotencyService idempotency,
                                        NotificationHistoryService history,
                                        PushNotificationDispatcherService pushDispatcher) {
        this.idempotency = idempotency;
        this.history = history;
        this.pushDispatcher = pushDispatcher;
    }

    @RabbitListener(queues = "${notification.rabbit.tracking.queue}", errorHandler = "rabbitListenerErrorHandler")
    public void onStreakMilestone(@Valid StreakMilestoneEvent event) {
        if (!idempotency.tryAcquire(SCOPE, event.getMessageId())) {
            return;
        }
        log.info("Processing streak milestone: profileId={} streak={} milestone={}",
                event.getProfileId(), event.getStreakCount(), event.getMilestoneName());

        String title = "You hit a " + event.getMilestoneName() + " streak!";
        String body = "Day " + event.getStreakCount() + ". Keep going — your future self thanks you.";

        history.save(UUID.fromString(event.getProfileId()), NotificationType.STREAK, title, body);

        pushDispatcher.sendPush(
                event.getDeviceToken(),
                title,
                body,
                Map.of(
                        "type", "streak.milestone",
                        "profileId", event.getProfileId(),
                        "streakCount", String.valueOf(event.getStreakCount()),
                        "milestoneName", event.getMilestoneName()
                )
        );
    }
}
