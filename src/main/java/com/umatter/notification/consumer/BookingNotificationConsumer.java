package com.umatter.notification.consumer;

import com.umatter.notification.dto.BookingConfirmedEvent;
import com.umatter.notification.exception.NotificationDispatchException;
import com.umatter.notification.persistence.DeviceToken;
import com.umatter.notification.persistence.NotificationType;
import com.umatter.notification.service.DeviceTokenService;
import com.umatter.notification.service.EmailDispatcherService;
import com.umatter.notification.service.NotificationHistoryService;
import com.umatter.notification.service.PushNotificationDispatcherService;
import com.umatter.notification.service.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class BookingNotificationConsumer {

    private static final String SCOPE = "appointment.booked";
    private static final DateTimeFormatter HUMAN_DT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy 'at' HH:mm XXX", Locale.ENGLISH);

    private final IdempotencyService idempotency;
    private final NotificationHistoryService history;
    private final EmailDispatcherService emailDispatcher;
    private final DeviceTokenService deviceTokens;
    private final PushNotificationDispatcherService pushDispatcher;

    public BookingNotificationConsumer(IdempotencyService idempotency,
                                       NotificationHistoryService history,
                                       EmailDispatcherService emailDispatcher,
                                       DeviceTokenService deviceTokens,
                                       PushNotificationDispatcherService pushDispatcher) {
        this.idempotency = idempotency;
        this.history = history;
        this.emailDispatcher = emailDispatcher;
        this.deviceTokens = deviceTokens;
        this.pushDispatcher = pushDispatcher;
    }

    @RabbitListener(queues = "${notification.rabbit.booking.queue}", errorHandler = "rabbitListenerErrorHandler")
    public void onAppointmentBooked(@Valid BookingConfirmedEvent event) {
        if (!idempotency.tryAcquire(SCOPE, event.getMessageId())) {
            return;
        }
        log.info("Processing booking confirmation: appointmentId={} messageId={}",
                event.getAppointmentId(), event.getMessageId());

        UUID profileId = UUID.fromString(event.getProfileId());
        String title = "Appointment confirmed";
        String message = String.format(
                "Your session with %s is confirmed for %s.",
                event.getTherapistName(),
                HUMAN_DT.format(event.getStartTime()));

        // Inbox row is the source of truth — must succeed before out-of-band delivery.
        history.save(profileId, NotificationType.BOOKING, title, message);

        // Email is best-effort; an SMTP failure must not poison the queue
        // because the inbox row already captures the event.
        try {
            emailDispatcher.sendBookingConfirmation(event);
        } catch (RuntimeException ex) {
            log.warn("Email dispatch failed for appointmentId={} — continuing",
                    event.getAppointmentId(), ex);
        }

        Map<String, String> data = Map.of(
                "type", "appointment.booked",
                "profileId", event.getProfileId(),
                "appointmentId", event.getAppointmentId(),
                "therapistName", event.getTherapistName());
        fanOutPush(profileId, title, message, data);
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
                log.warn("Push failed for profileId={} platform={} — continuing",
                        profileId, d.getPlatform(), ex);
            }
        }
    }
}
