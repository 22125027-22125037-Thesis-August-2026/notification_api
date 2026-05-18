package com.umatter.notification.service;

import com.umatter.notification.config.MailProperties;
import com.umatter.notification.dto.BookingConfirmedEvent;
import com.umatter.notification.exception.NotificationDispatchException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.UnsupportedEncodingException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Service
@EnableConfigurationProperties(MailProperties.class)
public class EmailDispatcherService {

    // Pattern uses `XXX` (offset like +07:00) instead of `z` (zone name), because
    // the incoming startTime is an OffsetDateTime — it carries an offset, not a ZoneId.
    private static final DateTimeFormatter HUMAN_DT =
            DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy 'at' HH:mm XXX", Locale.ENGLISH);

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final MailProperties mailProps;

    public EmailDispatcherService(JavaMailSender mailSender,
                                  SpringTemplateEngine templateEngine,
                                  MailProperties mailProps) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.mailProps = mailProps;
    }

    public void sendBookingConfirmation(BookingConfirmedEvent event) {
        try {
            Context ctx = new Context(Locale.ENGLISH);
            ctx.setVariable("userName", event.getUserName());
            ctx.setVariable("therapistName", event.getTherapistName());
            ctx.setVariable("appointmentId", event.getAppointmentId());
            ctx.setVariable("startTimeHuman", HUMAN_DT.format(event.getStartTime()));

            String html = templateEngine.process("email/booking-confirmation", ctx);

            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(mailProps.getFrom(), mailProps.getFromName());
            helper.setTo(event.getUserEmail());
            helper.setSubject("Your uMatter appointment is confirmed");
            helper.setText(html, true);

            mailSender.send(mime);
            log.info("Booking confirmation email dispatched: appointmentId={} to={}",
                    event.getAppointmentId(), event.getUserEmail());
        } catch (MessagingException | UnsupportedEncodingException ex) {
            throw new NotificationDispatchException(
                    "Failed to dispatch booking confirmation email for appointment " + event.getAppointmentId(), ex);
        }
    }
}
