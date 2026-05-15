package com.umatter.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.api.RabbitListenerErrorHandler;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.umatter.notification.exception.AmqpListenerErrorHandler;

@Configuration
@EnableRabbit
@EnableConfigurationProperties(RabbitTopologyProperties.class)
public class RabbitConfig {

    private final RabbitTopologyProperties topology;

    public RabbitConfig(RabbitTopologyProperties topology) {
        this.topology = topology;
    }

    // ---- Message converter ----
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMessageConverter(converter);
        return template;
    }

    // ---- Listener container factory (manual ack handled per-listener via AUTO + DLQ on throw) ----
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory cf,
            MessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(cf);
        factory.setMessageConverter(converter);
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        factory.setPrefetchCount(20);
        factory.setDefaultRequeueRejected(false); // failed messages go to DLQ, not requeued
        return factory;
    }

    @Bean
    public RabbitListenerErrorHandler rabbitListenerErrorHandler() {
        return new AmqpListenerErrorHandler();
    }

    // -------------------- BOOKING DOMAIN --------------------
    @Bean
    public TopicExchange bookingExchange() {
        return new TopicExchange(topology.getBooking().getExchange(), true, false);
    }

    @Bean
    public TopicExchange bookingDlx() {
        return new TopicExchange(topology.getBooking().getDlxExchange(), true, false);
    }

    @Bean
    public Queue bookingQueue() {
        return QueueBuilder.durable(topology.getBooking().getQueue())
                .withArgument("x-dead-letter-exchange", topology.getBooking().getDlxExchange())
                .withArgument("x-dead-letter-routing-key", topology.getBooking().getRoutingKey())
                .build();
    }

    @Bean
    public Queue bookingDlq() {
        return QueueBuilder.durable(topology.getBooking().getDlqQueue()).build();
    }

    @Bean
    public Binding bookingBinding() {
        return BindingBuilder.bind(bookingQueue())
                .to(bookingExchange())
                .with(topology.getBooking().getRoutingKey());
    }

    @Bean
    public Binding bookingDlqBinding() {
        return BindingBuilder.bind(bookingDlq())
                .to(bookingDlx())
                .with(topology.getBooking().getRoutingKey());
    }

    // -------------------- TRACKING DOMAIN --------------------
    @Bean
    public TopicExchange trackingExchange() {
        return new TopicExchange(topology.getTracking().getExchange(), true, false);
    }

    @Bean
    public TopicExchange trackingDlx() {
        return new TopicExchange(topology.getTracking().getDlxExchange(), true, false);
    }

    @Bean
    public Queue trackingQueue() {
        return QueueBuilder.durable(topology.getTracking().getQueue())
                .withArgument("x-dead-letter-exchange", topology.getTracking().getDlxExchange())
                .withArgument("x-dead-letter-routing-key", topology.getTracking().getRoutingKey())
                .build();
    }

    @Bean
    public Queue trackingDlq() {
        return QueueBuilder.durable(topology.getTracking().getDlqQueue()).build();
    }

    @Bean
    public Binding trackingBinding() {
        return BindingBuilder.bind(trackingQueue())
                .to(trackingExchange())
                .with(topology.getTracking().getRoutingKey());
    }

    @Bean
    public Binding trackingDlqBinding() {
        return BindingBuilder.bind(trackingDlq())
                .to(trackingDlx())
                .with(topology.getTracking().getRoutingKey());
    }

    // -------------------- SOCIAL DOMAIN --------------------
    @Bean
    public TopicExchange socialExchange() {
        return new TopicExchange(topology.getSocial().getExchange(), true, false);
    }

    @Bean
    public TopicExchange socialDlx() {
        return new TopicExchange(topology.getSocial().getDlxExchange(), true, false);
    }

    @Bean
    public Queue socialQueue() {
        return QueueBuilder.durable(topology.getSocial().getQueue())
                .withArgument("x-dead-letter-exchange", topology.getSocial().getDlxExchange())
                .withArgument("x-dead-letter-routing-key", topology.getSocial().getRoutingKey())
                .build();
    }

    @Bean
    public Queue socialDlq() {
        return QueueBuilder.durable(topology.getSocial().getDlqQueue()).build();
    }

    @Bean
    public Binding socialBinding() {
        return BindingBuilder.bind(socialQueue())
                .to(socialExchange())
                .with(topology.getSocial().getRoutingKey());
    }

    @Bean
    public Binding socialDlqBinding() {
        return BindingBuilder.bind(socialDlq())
                .to(socialDlx())
                .with(topology.getSocial().getRoutingKey());
    }
}
