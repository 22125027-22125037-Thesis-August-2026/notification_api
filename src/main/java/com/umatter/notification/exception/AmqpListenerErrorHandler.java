package com.umatter.notification.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.api.RabbitListenerErrorHandler;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.lang.Nullable;
import org.springframework.messaging.MessagingException;

/**
 * Final stop for any uncaught exception thrown by a {@code @RabbitListener}.
 * Logs and rethrows {@link AmqpRejectAndDontRequeueException} so the framework
 * routes the failed message to its configured DLQ instead of re-queuing it
 * into a poison-message loop.
 */
@Slf4j
public class AmqpListenerErrorHandler implements RabbitListenerErrorHandler {

    @Override
    @Nullable
    public Object handleError(Message amqpMessage,
                              org.springframework.messaging.Message<?> springMessage,
                              ListenerExecutionFailedException exception) throws MessagingException {
        String routingKey = amqpMessage.getMessageProperties() != null
                ? amqpMessage.getMessageProperties().getReceivedRoutingKey()
                : "unknown";
        log.error("Listener failed: routingKey={} cause={}", routingKey, exception.getMessage(), exception);
        throw new AmqpRejectAndDontRequeueException("Routing to DLQ", exception);
    }
}
