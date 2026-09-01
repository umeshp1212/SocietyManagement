package com.society.module.ownernoc.controller;

import com.society.common.ApiResponse;
import com.society.module.ownernoc.dto.OwnerNocApproveRequest;
import com.society.module.ownernoc.dto.OwnerNocRejectRequest;
import com.society.module.ownernoc.dto.OwnerNocRequestDTO;
import com.society.module.ownernoc.service.OwnerNocRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin endpoints to review owner NOC requests. Gated by OWNER_NOC_APPROVE
 * (or admin roles), so it is controllable from the Roles & Permissions module.
 */
@RestController
@RequestMapping("/owner-noc-requests")
@RequiredArgsConstructor
public class OwnerNocRequestController {

    private final OwnerNocRequestService service;

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CHAIRMAN','SECRETARY') or hasAuthority('OWNER_NOC_APPROVE')")
    public ResponseEntity<ApiResponse<List<OwnerNocRequestDTO>>> getPending() {
        return ResponseEntity.ok(ApiResponse.success(service.getPending()));
    }

    @PostMapping("/{requestId}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CHAIRMAN','SECRETARY') or hasAuthority('OWNER_NOC_APPROVE')")
    public ResponseEntity<ApiResponse<OwnerNocRequestDTO>> approve(
            @PathVariable Long requestId,
            @RequestBody(required = false) OwnerNocApproveRequest body,
            Authentication authentication) {
        String approver = authentication != null ? authentication.getName() : "ADMIN";
        String finalContent = body != null ? body.getFinalContent() : null;
        OwnerNocRequestDTO dto = service.approve(requestId, approver, finalContent);
        return ResponseEntity.ok(ApiResponse.success(
                "NOC request approved. Certificate emailed to the owner.", dto));
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CHAIRMAN','SECRETARY') or hasAuthority('OWNER_NOC_APPROVE')")
    public ResponseEntity<ApiResponse<OwnerNocRequestDTO>> reject(
            @PathVariable Long requestId,
            @RequestBody(required = false) OwnerNocRejectRequest body,
            Authentication authentication) {
        String approver = authentication != null ? authentication.getName() : "ADMIN";
        String reason = body != null ? body.getReason() : null;
        OwnerNocRequestDTO dto = service.reject(requestId, approver, reason);
        return ResponseEntity.ok(ApiResponse.success("NOC request rejected.", dto));
    }
}
