package com.umatter.notification.service.idempotency;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Redis-backed deduplication for inbound RabbitMQ events.
 *
 * Contract: {@link #tryAcquire(String, String)} returns true iff the
 * {@code messageId} has not been seen within the configured TTL window.
 * Duplicates return false and MUST be silently acked by the consumer.
 *
 * Implementation: SET key value NX EX <ttl-hours>  (atomic, single Redis op).
 */
@Slf4j
@Service
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyService {

    private final StringRedisTemplate redis;
    private final IdempotencyProperties props;

    public IdempotencyService(StringRedisTemplate redis, IdempotencyProperties props) {
        this.redis = redis;
        this.props = props;
    }

    /**
     * Atomically reserve the given message id for the configured TTL.
     *
     * @param scope     a logical namespace (e.g. routing key) so the same
     *                  messageId across two domains never collides
     * @param messageId the producer-supplied unique id for this event
     * @return true if first-seen (caller should process); false if duplicate (caller should ack & skip)
     */
    public boolean tryAcquire(String scope, String messageId) {
        if (!StringUtils.hasText(messageId)) {
            // Defensive: a missing id means the producer is misbehaving — fail closed
            // by allowing processing once, but log loudly.
            log.warn("Idempotency check skipped: blank messageId for scope={}", scope);
            return true;
        }

        String key = buildKey(scope, messageId);
        Duration ttl = Duration.ofHours(props.getTtlHours());

        Boolean firstSeen = redis.opsForValue().setIfAbsent(key, "1", ttl);

        if (Boolean.TRUE.equals(firstSeen)) {
            log.debug("Idempotency acquired: {}", key);
            return true;
        }

        log.info("Duplicate event suppressed: scope={} messageId={}", scope, messageId);
        return false;
    }

    private String buildKey(String scope, String messageId) {
        return props.getKeyPrefix() + ":" + scope + ":" + messageId;
    }
}
