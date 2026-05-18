package com.umatter.notification.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, String> {

    List<DeviceToken> findByProfileId(UUID profileId);

    long deleteByDeviceToken(String deviceToken);
}
