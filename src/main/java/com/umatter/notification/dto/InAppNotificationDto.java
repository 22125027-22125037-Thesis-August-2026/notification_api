package com.umatter.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.umatter.notification.persistence.InAppNotification;
import com.umatter.notification.persistence.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InAppNotificationDto {

    private UUID notificationId;
    private UUID profileId;
    private String title;
    private String message;
    private NotificationType type;
    private boolean read;
    private OffsetDateTime createdAt;

    public static InAppNotificationDto from(InAppNotification entity) {
        return InAppNotificationDto.builder()
                .notificationId(entity.getNotificationId())
                .profileId(entity.getProfileId())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .type(entity.getType())
                .read(entity.isRead())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
