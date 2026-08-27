package com.society.module.member.controller;

import com.society.common.ApiResponse;
import com.society.module.member.dto.MemberRegistrationRequestDTO;
import com.society.module.member.service.MemberRegistrationService;
import com.society.module.owner.entity.Owner;
import com.society.module.owner.repository.OwnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/registration-requests")
@RequiredArgsConstructor
public class AdminRegistrationRequestController {

    private final MemberRegistrationService registrationService;
    private final OwnerRepository ownerRepository;

    /**
     * Get all pending registration requests.
     */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CHAIRMAN','SECRETARY')")
    public ResponseEntity<ApiResponse<List<MemberRegistrationRequestDTO>>> getPending() {
        return ResponseEntity.ok(ApiResponse.success("Pending requests",
                registrationService.getPendingRequests()));
    }

    /**
     * Get all registration requests (history).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CHAIRMAN','SECRETARY')")
    public ResponseEntity<ApiResponse<List<MemberRegistrationRequestDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("All requests",
                registrationService.getAllRequests()));
    }

    /**
     * Get list of owners (for admin to select which owner to link the request to).
     */
    @GetMapping("/owners")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CHAIRMAN','SECRETARY')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getOwnersList() {
        List<Map<String, Object>> owners = ownerRepository.findAll().stream()
                .filter(o -> o.getStatus() == com.society.enums.OwnerStatus.ACTIVE)
                .map(o -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("ownerId", o.getOwnerId());
                    m.put("fullName", o.getFullName());
                    m.put("contactNumber", o.getContactNumber());
                    m.put("email", o.getEmail());
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Owners list", owners));
    }

    /**
     * Get owners of a specific unit (for admin to select owner/co-owner).
     */
    @GetMapping("/{requestId}/unit-owners")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CHAIRMAN','SECRETARY')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getUnitOwners(@PathVariable Long requestId) {
        MemberRegistrationRequestDTO req = registrationService.getAllRequests().stream()
                .filter(r -> r.getRequestId().equals(requestId))
                .findFirst()
                .orElseThrow(() -> new com.society.exception.BusinessException("Request not found"));
        List<Map<String, Object>> owners = registrationService.getUnitOwners(req.getUnitId());
        return ResponseEntity.ok(ApiResponse.success("Unit owners", owners));
    }

    /**
     * Approve a registration request and link to an owner.
     */
    @PostMapping("/{requestId}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CHAIRMAN','SECRETARY')")
    public ResponseEntity<ApiResponse<MemberRegistrationRequestDTO>> approve(
            @PathVariable Long requestId,
            @RequestBody Map<String, Object> body) {
        Long ownerId = Long.valueOf(body.get("ownerId").toString());
        String adminName = body.getOrDefault("adminName", "Admin").toString();
        MemberRegistrationRequestDTO result = registrationService.approveRequest(requestId, ownerId, adminName);
        return ResponseEntity.ok(ApiResponse.success("Registration approved. Owner details updated.", result));
    }

    /**
     * Reject a registration request.
     */
    @PostMapping("/{requestId}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CHAIRMAN','SECRETARY')")
    public ResponseEntity<ApiResponse<MemberRegistrationRequestDTO>> reject(
            @PathVariable Long requestId,
            @RequestBody Map<String, String> body) {
        String adminName = body.getOrDefault("adminName", "Admin");
        String reason = body.getOrDefault("reason", "");
        MemberRegistrationRequestDTO result = registrationService.rejectRequest(requestId, adminName, reason);
        return ResponseEntity.ok(ApiResponse.success("Registration rejected.", result));
    }
}
