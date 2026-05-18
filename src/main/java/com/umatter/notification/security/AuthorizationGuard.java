package com.umatter.notification.security;

import com.umatter.notification.persistence.DeviceTokenRepository;
import com.umatter.notification.persistence.InAppNotificationRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Authorization helpers invoked from {@code @PreAuthorize} expressions. Bean name
 * {@code authz} keeps the SpEL terse: {@code @authz.ownsProfile(authentication, #profileId)}.
 *
 * <p>All checks return {@code false} when the token is malformed, the claim is missing,
 * or the referenced row doesn't exist — never throwing — so Spring Security maps the
 * outcome to a 403 instead of leaking 500s on bad input.
 */
@Component("authz")
public class AuthorizationGuard {

    private final InAppNotificationRepository notifications;
    private final DeviceTokenRepository deviceTokens;

    public AuthorizationGuard(InAppNotificationRepository notifications,
                              DeviceTokenRepository deviceTokens) {
        this.notifications = notifications;
        this.deviceTokens = deviceTokens;
    }

    public boolean ownsProfile(Authentication authentication, UUID profileId) {
        return jwtProfileId(authentication)
                .map(claimed -> claimed.equals(profileId))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean ownsNotification(Authentication authentication, UUID notificationId) {
        return notifications.findById(notificationId)
                .map(n -> ownsProfile(authentication, n.getProfileId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean ownsDeviceToken(Authentication authentication, String deviceToken) {
        return deviceTokens.findById(deviceToken)
                .map(d -> ownsProfile(authentication, d.getProfileId()))
                .orElse(false);
    }

    public static Optional<UUID> jwtProfileId(Authentication authentication) {
        return Optional.ofNullable(jwtFrom(authentication)).flatMap(AuthorizationGuard::profileIdFrom);
    }

    public static Optional<UUID> profileIdFrom(Jwt jwt) {
        if (jwt == null) {
            return Optional.empty();
        }
        String raw = jwt.getClaimAsString("profileId");
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Jwt jwtFrom(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }
}
