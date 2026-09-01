package com.society.module.member.controller;

import com.society.common.ApiResponse;
import com.society.module.auth.security.JwtUtil;
import com.society.module.tenant.dto.TenantCreateRequest;
import com.society.module.tenant.dto.TenantDTO;
import com.society.module.tenant.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Member-portal endpoint that lets an authenticated flat owner submit a tenant
 * registration for one of their own units. The submission is created in
 * PENDING_APPROVAL status and takes effect only after a SUPER_ADMIN approves it.
 */
@RestController
@RequestMapping("/member/tenants")
@RequiredArgsConstructor
public class MemberTenantController {

    private final TenantService tenantService;
    private final JwtUtil jwtUtil;

    /**
     * Owner submits a tenant registration for their unit.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TenantDTO>> submitTenantRegistration(
            @Valid @RequestBody TenantCreateRequest request,
            @RequestHeader("Authorization") String authHeader) {
        Long ownerId = extractOwnerId(authHeader);
        TenantDTO tenant = tenantService.submitTenantRegistration(request, ownerId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Tenant registration submitted. It will be recorded once the society admin approves it.",
                        tenant));
    }

    /**
     * In member tokens the userId claim stores the ownerId.
     */
    private Long extractOwnerId(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtUtil.extractUserId(token);
    }
}
