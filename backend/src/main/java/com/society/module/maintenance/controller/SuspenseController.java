package com.society.module.maintenance.controller;

import com.society.common.ApiResponse;
import com.society.common.PagedResponse;
import com.society.module.maintenance.dto.SuspenseEntryDTO;
import com.society.module.maintenance.service.SuspenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/suspense")
@RequiredArgsConstructor
public class SuspenseController {

    private final SuspenseService suspenseService;

    // ==================== LIST / SEARCH ====================

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<SuspenseEntryDTO>>> getAllEntries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        PagedResponse<SuspenseEntryDTO> result = suspenseService.getAllEntries(page, size, status, search);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/unassigned")
    public ResponseEntity<ApiResponse<List<SuspenseEntryDTO>>> getUnassigned() {
        return ResponseEntity.ok(ApiResponse.success(suspenseService.getUnassignedEntries()));
    }

    @GetMapping("/unit/{unitId}")
    public ResponseEntity<ApiResponse<List<SuspenseEntryDTO>>> getByUnit(@PathVariable Long unitId) {
        return ResponseEntity.ok(ApiResponse.success(suspenseService.getEntriesAssignedToUnit(unitId)));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(suspenseService.getSummary()));
    }

    @GetMapping("/{suspenseId}")
    public ResponseEntity<ApiResponse<SuspenseEntryDTO>> getById(@PathVariable Long suspenseId) {
        return ResponseEntity.ok(ApiResponse.success(suspenseService.getById(suspenseId)));
    }

    // ==================== CREATE ====================

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MAINTENANCE_SUSPENSE')")
    public ResponseEntity<ApiResponse<SuspenseEntryDTO>> createEntry(
            @RequestBody Map<String, Object> body, Authentication authentication) {
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        LocalDate receivedDate = body.get("receivedDate") != null
                ? LocalDate.parse(body.get("receivedDate").toString()) : null;
        String paymentMode = body.get("paymentMode") != null ? body.get("paymentMode").toString() : null;
        String referenceNumber = body.get("referenceNumber") != null ? body.get("referenceNumber").toString() : null;
        String description = body.get("description") != null ? body.get("description").toString() : null;

        SuspenseEntryDTO result = suspenseService.createSuspenseEntry(
                amount, receivedDate, paymentMode, referenceNumber, description, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Suspense entry created", result));
    }

    // ==================== ASSIGN ====================

    @PatchMapping("/{suspenseId}/assign")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MAINTENANCE_SUSPENSE')")
    public ResponseEntity<ApiResponse<SuspenseEntryDTO>> assignToUnit(
            @PathVariable Long suspenseId,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        Long unitId = Long.valueOf(body.get("unitId").toString());
        String remarks = body.get("remarks") != null ? body.get("remarks").toString() : null;
        boolean applyToOpeningBalance = body.get("applyToOpeningBalance") != null
                && Boolean.parseBoolean(body.get("applyToOpeningBalance").toString());

        SuspenseEntryDTO result = suspenseService.assignToUnit(
                suspenseId, unitId, remarks, applyToOpeningBalance, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Suspense entry assigned to unit", result));
    }

    // ==================== REVERSE ====================

    @PatchMapping("/{suspenseId}/reverse")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MAINTENANCE_SUSPENSE')")
    public ResponseEntity<ApiResponse<SuspenseEntryDTO>> reverseAssignment(
            @PathVariable Long suspenseId,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        String reason = body.get("reason") != null ? body.get("reason").toString() : null;

        SuspenseEntryDTO result = suspenseService.reverseAssignment(
                suspenseId, reason, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Suspense assignment reversed", result));
    }

    // ==================== REASSIGN ====================

    @PatchMapping("/{suspenseId}/reassign")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MAINTENANCE_SUSPENSE')")
    public ResponseEntity<ApiResponse<SuspenseEntryDTO>> reassign(
            @PathVariable Long suspenseId,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        Long newUnitId = Long.valueOf(body.get("unitId").toString());
        String reason = body.get("reason") != null ? body.get("reason").toString() : null;
        String remarks = body.get("remarks") != null ? body.get("remarks").toString() : null;
        boolean applyToOpeningBalance = body.get("applyToOpeningBalance") != null
                && Boolean.parseBoolean(body.get("applyToOpeningBalance").toString());

        SuspenseEntryDTO result = suspenseService.reassign(
                suspenseId, newUnitId, reason, remarks, applyToOpeningBalance, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Suspense entry reassigned", result));
    }
}
