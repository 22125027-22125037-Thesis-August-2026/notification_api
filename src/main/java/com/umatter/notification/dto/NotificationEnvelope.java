package com.umatter.notification.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/**
 * Common envelope fields every inbound event must carry so the idempotency
 * layer can dedupe consistently across producers.
 */
public interface NotificationEnvelope {

    @NotBlank
    String getMessageId();

    Instant getOccurredAt();
}
