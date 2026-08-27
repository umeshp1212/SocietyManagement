package com.society.module.member.controller;

import com.society.common.ApiResponse;
import com.society.module.member.dto.ProfileUpdateRequestDTO;
import com.society.module.member.service.MemberProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/profile-requests")
@RequiredArgsConstructor
public class AdminProfileRequestController {

    private final MemberProfileService profileService;

    /**
     * Get all pending profile update requests.
     */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CHAIRMAN','SECRETARY')")
    public ResponseEntity<ApiResponse<List<ProfileUpdateRequestDTO>>> getPendingRequests() {
        List<ProfileUpdateRequestDTO> requests = profileService.getPendingRequests();
        return ResponseEntity.ok(ApiResponse.success("Pending requests", requests));
    }

    /**
     * Get all profile update requests (history).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CHAIRMAN','SECRETARY')")
    public ResponseEntity<ApiResponse<List<ProfileUpdateRequestDTO>>> getAllRequests() {
        List<ProfileUpdateRequestDTO> requests = profileService.getAllRequests();
        return ResponseEntity.ok(ApiResponse.success("All requests", requests));
    }

    /**
     * Approve a profile update request.
     */
    @PostMapping("/{requestId}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CHAIRMAN','SECRETARY')")
    public ResponseEntity<ApiResponse<ProfileUpdateRequestDTO>> approve(
            @PathVariable Long requestId,
            @RequestParam(defaultValue = "Admin") String adminName) {
        ProfileUpdateRequestDTO result = profileService.approveRequest(requestId, adminName);
        return ResponseEntity.ok(ApiResponse.success("Request approved. Owner details updated.", result));
    }

    /**
     * Reject a profile update request.
     */
    @PostMapping("/{requestId}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CHAIRMAN','SECRETARY')")
    public ResponseEntity<ApiResponse<ProfileUpdateRequestDTO>> reject(
            @PathVariable Long requestId,
            @RequestBody Map<String, String> body) {
        String adminName = body.getOrDefault("adminName", "Admin");
        String reason = body.getOrDefault("reason", "");
        ProfileUpdateRequestDTO result = profileService.rejectRequest(requestId, adminName, reason);
        return ResponseEntity.ok(ApiResponse.success("Request rejected.", result));
    }
}
