package com.society.module.member.controller;

import com.society.common.ApiResponse;
import com.society.module.member.dto.MemberRegistrationRequestDTO;
import com.society.module.member.service.MemberRegistrationService;
import com.society.module.owner.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Public endpoints for member self-registration (no auth required).
 */
@RestController
@RequestMapping("/member/auth/register")
@RequiredArgsConstructor
public class MemberRegistrationController {

    private final MemberRegistrationService registrationService;
    private final UnitRepository unitRepository;

    /**
     * Get list of units for the registration dropdown (public).
     */
    @GetMapping("/units")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getUnits() {
        List<Map<String, Object>> units = unitRepository.findAll().stream()
                .filter(u -> "ACTIVE".equals(u.getStatus()))
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("unitId", u.getUnitId());
                    m.put("unitNumber", u.getUnitNumber());
                    m.put("wing", u.getWing());
                    m.put("floor", u.getFloor());
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Units", units));
    }

    /**
     * Step 1: Submit email + mobile + unit → sends OTP to email.
     */
    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<String>> sendOtp(@RequestBody Map<String, Object> body) {
        String email = (String) body.get("email");
        String mobile = (String) body.get("mobile");
        Long unitId = body.get("unitId") != null ? Long.valueOf(body.get("unitId").toString()) : null;
        registrationService.submitRegistration(email, mobile, unitId);
        String maskedEmail = email != null && email.contains("@")
                ? email.substring(0, Math.min(2, email.indexOf("@"))) + "****@" + email.split("@")[1]
                : email;
        return ResponseEntity.ok(ApiResponse.success(
                "OTP sent to " + maskedEmail,
                "Verify your email to complete registration"));
    }

    /**
     * Step 2: Verify OTP → creates pending registration request.
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<MemberRegistrationRequestDTO>> verifyOtp(
            @RequestBody Map<String, Object> body) {
        String email = (String) body.get("email");
        String mobile = (String) body.get("mobile");
        String otp = (String) body.get("otp");
        Long unitId = body.get("unitId") != null ? Long.valueOf(body.get("unitId").toString()) : null;
        MemberRegistrationRequestDTO result = registrationService.verifyAndCreateRequest(email, mobile, otp, unitId);
        return ResponseEntity.ok(ApiResponse.success(
                "Registration request submitted. Admin will review and approve your details.", result));
    }
}
