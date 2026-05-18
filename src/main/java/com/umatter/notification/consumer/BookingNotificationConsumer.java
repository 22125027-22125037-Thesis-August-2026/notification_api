package com.umatter.notification.consumer;

import com.umatter.notification.dto.BookingConfirmedEvent;
import com.umatter.notification.persistence.NotificationType;
import com.umatter.notification.service.EmailDispatcherService;
import com.umatter.notification.service.NotificationHistoryService;
import com.umatter.notification.service.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
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

    public BookingNotificationConsumer(IdempotencyService idempotency,
                                       NotificationHistoryService history,
                                       EmailDispatcherService emailDispatcher) {
        this.idempotency = idempotency;
        this.history = history;
        this.emailDispatcher = emailDispatcher;
    }

    @RabbitListener(queues = "${notification.rabbit.booking.queue}", errorHandler = "rabbitListenerErrorHandler")
    public void onAppointmentBooked(@Valid BookingConfirmedEvent event) {
        if (!idempotency.tryAcquire(SCOPE, event.getMessageId())) {
            return;
        }
        log.info("Processing booking confirmation: appointmentId={} messageId={}",
                event.getAppointmentId(), event.getMessageId());

        String title = "Appointment confirmed";
        String message = String.format(
                "Your session with %s is confirmed for %s.",
                event.getTherapistName(),
                HUMAN_DT.format(event.getStartTime()));

        history.save(UUID.fromString(event.getProfileId()), NotificationType.BOOKING, title, message);
        emailDispatcher.sendBookingConfirmation(event);
    }
}
