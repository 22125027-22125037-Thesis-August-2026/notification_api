package com.umatter.notification.exception;

/**
 * Thrown by dispatcher services when an outbound notification fails for a
 * non-recoverable reason. Triggers nack -> DLQ via the listener container.
 */
public class NotificationDispatchException extends RuntimeException {
    public NotificationDispatchException(String message) {
        super(message);
    }

    public NotificationDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
