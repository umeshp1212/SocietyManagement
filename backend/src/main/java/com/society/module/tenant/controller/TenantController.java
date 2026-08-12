package com.society.module.tenant.controller;

import com.society.common.ApiResponse;
import com.society.common.PagedResponse;
import com.society.enums.PoliceVerificationStatus;
import com.society.module.tenant.dto.*;
import com.society.module.tenant.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    // ==================== REGISTRATION & UPDATE ====================

    @PostMapping
    public ResponseEntity<ApiResponse<TenantDTO>> registerTenant(
            @Valid @RequestBody TenantCreateRequest request) {
        TenantDTO tenant = tenantService.registerTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tenant registered successfully", tenant));
    }

    @PutMapping("/{tenantId}")
    public ResponseEntity<ApiResponse<TenantDTO>> updateTenant(
            @PathVariable Long tenantId,
            @Valid @RequestBody TenantUpdateRequest request) {
        TenantDTO tenant = tenantService.updateTenant(tenantId, request);
        return ResponseEntity.ok(ApiResponse.success("Tenant updated successfully", tenant));
    }

    // ==================== NOC & VERIFICATION ====================

    @PatchMapping("/{tenantId}/noc")
    public ResponseEntity<ApiResponse<TenantDTO>> updateNocStatus(
            @PathVariable Long tenantId,
            @Valid @RequestBody NocApprovalRequest request) {
        TenantDTO tenant = tenantService.updateNocStatus(tenantId, request);
        return ResponseEntity.ok(ApiResponse.success("NOC status updated successfully", tenant));
    }

    @PatchMapping("/{tenantId}/police-verification")
    public ResponseEntity<ApiResponse<TenantDTO>> updatePoliceVerification(
            @PathVariable Long tenantId,
            @RequestParam PoliceVerificationStatus status) {
        TenantDTO tenant = tenantService.updatePoliceVerificationStatus(tenantId, status);
        return ResponseEntity.ok(ApiResponse.success("Police verification status updated", tenant));
    }

    // ==================== MOVE OUT ====================

    @PatchMapping("/{tenantId}/notice-period")
    public ResponseEntity<ApiResponse<TenantDTO>> markNoticePeriod(@PathVariable Long tenantId) {
        TenantDTO tenant = tenantService.markNoticePeriod(tenantId);
        return ResponseEntity.ok(ApiResponse.success("Tenant marked for notice period", tenant));
    }

    @PatchMapping("/{tenantId}/move-out")
    public ResponseEntity<ApiResponse<TenantDTO>> moveOutTenant(
            @PathVariable Long tenantId,
            @Valid @RequestBody MoveOutRequest request) {
        TenantDTO tenant = tenantService.moveOutTenant(tenantId, request);
        return ResponseEntity.ok(ApiResponse.success("Tenant moved out successfully", tenant));
    }

    // ==================== QUERIES ====================

    @GetMapping("/{tenantId}")
    public ResponseEntity<ApiResponse<TenantDTO>> getTenantById(@PathVariable Long tenantId) {
        TenantDTO tenant = tenantService.getTenantById(tenantId);
        return ResponseEntity.ok(ApiResponse.success(tenant));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<TenantDTO>>> getAllTenants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String nocStatus,
            @RequestParam(required = false) String search) {
        PagedResponse<TenantDTO> tenants = tenantService.getAllTenants(page, size, status, nocStatus, search);
        return ResponseEntity.ok(ApiResponse.success(tenants));
    }

    @GetMapping("/unit/{unitId}/history")
    public ResponseEntity<ApiResponse<List<TenantDTO>>> getTenantHistoryByUnit(@PathVariable Long unitId) {
        List<TenantDTO> tenants = tenantService.getTenantHistoryByUnit(unitId);
        return ResponseEntity.ok(ApiResponse.success(tenants));
    }

    @GetMapping("/unit/{unitId}/active")
    public ResponseEntity<ApiResponse<TenantDTO>> getActiveTenantByUnit(@PathVariable Long unitId) {
        TenantDTO tenant = tenantService.getActiveTenantByUnit(unitId);
        return ResponseEntity.ok(ApiResponse.success(tenant));
    }

    // ==================== ALERTS ====================

    @GetMapping("/expiring-agreements")
    public ResponseEntity<ApiResponse<List<TenantDTO>>> getExpiringAgreements(
            @RequestParam(defaultValue = "30") int days) {
        List<TenantDTO> tenants = tenantService.getTenantsWithExpiringAgreements(days);
        return ResponseEntity.ok(ApiResponse.success(tenants));
    }

    @GetMapping("/pending-police-verification")
    public ResponseEntity<ApiResponse<List<TenantDTO>>> getPendingPoliceVerification() {
        List<TenantDTO> tenants = tenantService.getTenantsWithPendingPoliceVerification();
        return ResponseEntity.ok(ApiResponse.success(tenants));
    }

    // ==================== SUMMARY ====================

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTenantSummary() {
        Map<String, Object> summary = tenantService.getTenantSummary();
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    // ==================== DOCUMENTS ====================

    @GetMapping("/{tenantId}/documents")
    public ResponseEntity<ApiResponse<List<TenantDocumentDTO>>> getTenantDocuments(
            @PathVariable Long tenantId) {
        List<TenantDocumentDTO> documents = tenantService.getTenantDocuments(tenantId);
        return ResponseEntity.ok(ApiResponse.success(documents));
    }

    @PostMapping("/{tenantId}/documents")
    public ResponseEntity<ApiResponse<TenantDocumentDTO>> addDocument(
            @PathVariable Long tenantId,
            @RequestParam String documentName,
            @RequestParam String documentType,
            @RequestParam String filePath) {
        TenantDocumentDTO document = tenantService.addDocument(tenantId, documentName, documentType, filePath);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Document added successfully", document));
    }
}
