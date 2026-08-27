package com.society.module.member.controller;

import com.society.common.ApiResponse;
import com.society.module.auth.security.JwtUtil;
import com.society.module.member.dto.MemberProfileDTO;
import com.society.module.member.dto.ProfileUpdateRequestDTO;
import com.society.module.member.dto.SubmitProfileUpdateRequest;
import com.society.module.member.service.MemberProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/member/profile")
@RequiredArgsConstructor
public class MemberProfileController {

    private final MemberProfileService profileService;
    private final JwtUtil jwtUtil;

    /**
     * Get member's own profile with masked personal info.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<MemberProfileDTO>> getProfile(
            @RequestHeader("Authorization") String authHeader) {
        Long ownerId = extractOwnerId(authHeader);
        MemberProfileDTO profile = profileService.getProfile(ownerId);
        return ResponseEntity.ok(ApiResponse.success("Profile loaded", profile));
    }

    /**
     * Submit a request to update mobile/email.
     */
    @PostMapping("/update-request")
    public ResponseEntity<ApiResponse<ProfileUpdateRequestDTO>> submitUpdateRequest(
            @RequestBody SubmitProfileUpdateRequest request,
            @RequestHeader("Authorization") String authHeader) {
        Long ownerId = extractOwnerId(authHeader);
        ProfileUpdateRequestDTO result = profileService.submitUpdateRequest(ownerId, request);
        return ResponseEntity.ok(ApiResponse.success(
                "Update request submitted. Admin will review and approve.", result));
    }

    private Long extractOwnerId(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtUtil.extractUserId(token);
    }
}
