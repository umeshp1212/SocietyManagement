package com.society.module.notification.controller;

import com.society.common.ApiResponse;
import com.society.module.auth.security.JwtUtil;
import com.society.module.notification.dto.DeviceTokenRequest;
import com.society.module.notification.service.DeviceTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Member-portal endpoint to register a device's FCM token for push notifications. The
 * owner id is taken from the authenticated member JWT.
 */
@RestController
@RequestMapping("/member/device-tokens")
@RequiredArgsConstructor
public class MemberDeviceTokenController {

    private final DeviceTokenService deviceTokenService;
    private final JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody DeviceTokenRequest request,
            @RequestHeader("Authorization") String authHeader) {
        Long ownerId = jwtUtil.extractUserId(authHeader.replace("Bearer ", ""));
        deviceTokenService.register(ownerId, request.getToken(), request.getPlatform());
        return ResponseEntity.ok(ApiResponse.success("Device registered for notifications.", null));
    }
}
