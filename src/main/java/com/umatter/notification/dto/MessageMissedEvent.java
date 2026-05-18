package com.umatter.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageMissedEvent implements NotificationEnvelope {

    @NotBlank
    private String messageId;

    private Instant occurredAt;

    @NotBlank
    private String profileId;

    @NotBlank
    private String senderName;

    @NotBlank
    private String channelId;
}
