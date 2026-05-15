package com.umatter.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification.mail")
public class MailProperties {
    private String from;
    private String fromName;
}
