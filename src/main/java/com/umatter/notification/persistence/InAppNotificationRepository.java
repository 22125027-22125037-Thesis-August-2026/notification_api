package com.umatter.notification.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InAppNotificationRepository extends JpaRepository<InAppNotification, UUID> {

    Page<InAppNotification> findByProfileIdOrderByCreatedAtDesc(UUID profileId, Pageable pageable);

    @Modifying
    @Query("update InAppNotification n set n.read = true where n.notificationId = :id and n.read = false")
    int markRead(@Param("id") UUID notificationId);

    @Modifying
    @Query("update InAppNotification n set n.read = true where n.profileId = :profileId and n.read = false")
    int markAllReadForProfile(@Param("profileId") UUID profileId);
}
