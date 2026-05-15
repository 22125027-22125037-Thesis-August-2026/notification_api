package com.umatter.notification.exception;

/**
 * Wraps unexpected Redis failures during idempotency checks.
 */
public class IdempotencyException extends RuntimeException {
    public IdempotencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
