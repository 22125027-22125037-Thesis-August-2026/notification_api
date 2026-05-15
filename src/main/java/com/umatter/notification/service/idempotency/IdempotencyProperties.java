package com.umatter.notification.service.idempotency;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification.idempotency")
public class IdempotencyProperties {
    private long ttlHours = 24;
    private String keyPrefix = "notif:idemp";
}
