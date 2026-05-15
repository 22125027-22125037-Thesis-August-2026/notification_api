package com.umatter.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification.firebase")
public class FirebaseProperties {

    /**
     * Absolute path to the Firebase Admin SDK service-account JSON file.
     */
    private String credentialsPath;

    /**
     * Whether the FCM dispatcher should be active. When false (typical in local dev
     * without credentials), push notifications are no-op'd with a warning log.
     */
    private boolean enabled = true;
}
