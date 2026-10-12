package com.society.module.notification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Body for a member registering their device's FCM token so they can receive push
 * notifications (e.g. a new visitor awaiting approval).
 */
@Data
public class DeviceTokenRequest {

    @NotBlank(message = "token is required")
    private String token;

    /** Optional platform hint (WEB/ANDROID/IOS); defaults to WEB. */
    private String platform;
}
