package com.umatter.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingConfirmedEvent implements NotificationEnvelope {

    @NotBlank
    private String messageId;

    private Instant occurredAt;

    @NotBlank
    private String appointmentId;

    @NotBlank
    private String profileId;

    @Email
    @NotBlank
    private String userEmail;

    @NotBlank
    private String userName;

    @NotBlank
    private String therapistName;

    @NotNull
    private OffsetDateTime startTime;
}
