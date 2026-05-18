package com.umatter.notification.api;

import com.umatter.notification.dto.DeviceRegistrationRequest;
import com.umatter.notification.persistence.DeviceToken;
import com.umatter.notification.service.DeviceTokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceTokenController {

    private final DeviceTokenService service;

    public DeviceTokenController(DeviceTokenService service) {
        this.service = service;
    }

    /**
     * Mobile app calls this on login (and on token-refresh callbacks from FCM).
     * Idempotent — repeat calls with the same token re-bind / refresh in place.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody DeviceRegistrationRequest req) {
        DeviceToken saved = service.register(req.getProfileId(), req.getDeviceToken(), req.getPlatform());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "profileId", saved.getProfileId(),
                "platform", saved.getPlatform(),
                "lastSeenAt", saved.getLastSeenAt()
        ));
    }

    /**
     * Mobile app calls this on logout, or when Firebase tells the app the token
     * is invalidated.
     */
    @DeleteMapping("/{deviceToken}")
    public ResponseEntity<Void> deregister(@PathVariable String deviceToken) {
        boolean removed = service.deregister(deviceToken);
        return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
