package com.society.module.member.controller;

import com.society.common.ApiResponse;
import com.society.module.member.dto.MemberLoginResponse;
import com.society.module.member.dto.SendOtpRequest;
import com.society.module.member.dto.VerifyOtpRequest;
import com.society.module.member.service.MemberAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/member/auth")
@RequiredArgsConstructor
public class MemberAuthController {

    private final MemberAuthService memberAuthService;

    /**
     * Send OTP to registered mobile number.
     * OTP will be sent to both mobile (console log) and email.
     */
    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<String>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        String maskedEmail = memberAuthService.sendOtp(request.getPhone());
        String maskedPhone = request.getPhone().substring(0, 2) + "******"
                + request.getPhone().substring(8);
        String message = maskedEmail != null
                ? "OTP sent to " + maskedPhone + " and " + maskedEmail
                : "OTP sent to " + maskedPhone;
        return ResponseEntity.ok(ApiResponse.success(message, message));
    }

    /**
     * Verify OTP and login.
     * Returns JWT token and member info with units.
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<MemberLoginResponse>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {
        MemberLoginResponse response = memberAuthService.verifyOtpAndLogin(
                request.getPhone(), request.getOtp());
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }
}
