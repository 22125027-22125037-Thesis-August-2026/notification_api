package com.umatter.notification.service;

import com.umatter.notification.persistence.InAppNotification;
import com.umatter.notification.persistence.InAppNotificationRepository;
import com.umatter.notification.persistence.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
public class NotificationHistoryService {

    private final InAppNotificationRepository repository;

    public NotificationHistoryService(InAppNotificationRepository repository) {
        this.repository = repository;
    }

    /**
     * Persist a new in-app notification record. The id is generated here so the
     * caller can correlate (or store it elsewhere) but the row is always new.
     */
    @Transactional
    public InAppNotification save(UUID profileId, NotificationType type, String title, String message) {
        InAppNotification entity = InAppNotification.builder()
                .notificationId(UUID.randomUUID())
                .profileId(profileId)
                .type(type)
                .title(title)
                .message(message)
                .read(false)
                .build();
        InAppNotification saved = repository.save(entity);
        log.debug("Saved in-app notification: id={} profileId={} type={}",
                saved.getNotificationId(), saved.getProfileId(), saved.getType());
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<InAppNotification> findForProfile(UUID profileId, Pageable pageable) {
        return repository.findByProfileIdOrderByCreatedAtDesc(profileId, pageable);
    }

    /**
     * @return true if the row existed and was updated from unread → read.
     */
    @Transactional
    public boolean markRead(UUID notificationId) {
        int updated = repository.markRead(notificationId);
        log.debug("markRead notificationId={} updated={}", notificationId, updated);
        return updated > 0;
    }

    /**
     * @return number of rows transitioned from unread → read.
     */
    @Transactional
    public int markAllReadForProfile(UUID profileId) {
        int updated = repository.markAllReadForProfile(profileId);
        log.debug("markAllReadForProfile profileId={} updated={}", profileId, updated);
        return updated;
    }
}
