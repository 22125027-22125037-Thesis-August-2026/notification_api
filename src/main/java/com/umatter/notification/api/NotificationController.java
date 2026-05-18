package com.umatter.notification.api;

import com.umatter.notification.dto.InAppNotificationDto;
import com.umatter.notification.service.NotificationHistoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationHistoryService history;

    public NotificationController(NotificationHistoryService history) {
        this.history = history;
    }

    @GetMapping("/{profileId}")
    @PreAuthorize("hasRole('ADMIN') or @authz.ownsProfile(authentication, #profileId)")
    public Page<InAppNotificationDto> listForProfile(
            @PathVariable UUID profileId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);
        return history.findForProfile(profileId, pageable).map(InAppNotificationDto::from);
    }

    @PutMapping("/{notificationId}/read")
    @PreAuthorize("hasRole('ADMIN') or @authz.ownsNotification(authentication, #notificationId)")
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable UUID notificationId) {
        boolean updated = history.markRead(notificationId);
        if (!updated) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "notificationId", notificationId,
                "read", true
        ));
    }

    @PutMapping("/{profileId}/read-all")
    @PreAuthorize("hasRole('ADMIN') or @authz.ownsProfile(authentication, #profileId)")
    public ResponseEntity<Map<String, Object>> markAllRead(@PathVariable UUID profileId) {
        int updated = history.markAllReadForProfile(profileId);
        return ResponseEntity.ok(Map.of(
                "profileId", profileId,
                "updatedCount", updated
        ));
    }
}
