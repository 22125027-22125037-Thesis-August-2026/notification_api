package com.umatter.notification.dto;

import com.umatter.notification.persistence.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRegistrationRequest {

    @NotNull
    private UUID profileId;

    @NotBlank
    private String deviceToken;

    @NotNull
    private DevicePlatform platform;
}
