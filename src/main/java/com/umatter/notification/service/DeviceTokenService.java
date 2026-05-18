package com.umatter.notification.service;

import com.umatter.notification.persistence.DevicePlatform;
import com.umatter.notification.persistence.DeviceToken;
import com.umatter.notification.persistence.DeviceTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Upserts and lookups for FCM device tokens. The mobile app calls
 * {@link #register} on login (and again on token refresh); the consumers
 * call {@link #findActiveTokensForProfile} when they need to fan out a push.
 */
@Slf4j
@Service
public class DeviceTokenService {

    private final DeviceTokenRepository repository;

    public DeviceTokenService(DeviceTokenRepository repository) {
        this.repository = repository;
    }

    /**
     * Idempotent register: if the token already exists, re-bind it to the given
     * profile and refresh {@code lastSeenAt} (handles the "user signs out and a
     * different user signs in on the same device" case). Otherwise insert.
     */
    @Transactional
    public DeviceToken register(UUID profileId, String deviceToken, DevicePlatform platform) {
        OffsetDateTime now = OffsetDateTime.now();
        Optional<DeviceToken> existing = repository.findById(deviceToken);

        DeviceToken entity = existing
                .map(d -> {
                    d.setProfileId(profileId);
                    d.setPlatform(platform);
                    d.setLastSeenAt(now);
                    return d;
                })
                .orElseGet(() -> DeviceToken.builder()
                        .deviceToken(deviceToken)
                        .profileId(profileId)
                        .platform(platform)
                        .createdAt(now)
                        .lastSeenAt(now)
                        .build());

        DeviceToken saved = repository.save(entity);
        log.info("Device token registered: profileId={} platform={} new={}",
                profileId, platform, existing.isEmpty());
        return saved;
    }

    @Transactional
    public boolean deregister(String deviceToken) {
        long removed = repository.deleteByDeviceToken(deviceToken);
        log.info("Device token deregistered: removed={}", removed);
        return removed > 0;
    }

    @Transactional(readOnly = true)
    public List<DeviceToken> findActiveTokensForProfile(UUID profileId) {
        return repository.findByProfileId(profileId);
    }
}
