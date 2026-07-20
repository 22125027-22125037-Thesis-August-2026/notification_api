package com.umatter.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification.rabbit")
public class RabbitTopologyProperties {

    private Domain booking = new Domain();

    @Getter
    @Setter
    public static class Domain {
        private String exchange;
        private String routingKey;
        private String queue;
        private String dlxExchange;
        private String dlqQueue;
    }
}
