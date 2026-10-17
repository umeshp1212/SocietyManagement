package com.society.module.visitor.controller;

import com.society.common.ApiResponse;
import com.society.enums.VisitorStatus;
import com.society.module.visitor.dto.VisitorRequestDTO;
import com.society.module.visitor.service.VisitorRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Gate (security-guard) endpoints for the visitor/delivery approval workflow. All
 * endpoints require the SECURITY role or the VISITOR_CREATE/VISITOR_VIEW authority, so
 * access is controllable from the Roles &amp; Permissions module. The acting guard is
 * taken from the authenticated principal (recorded on the request via JPA auditing).
 */
@RestController
@RequestMapping("/gate/visitor-requests")
@RequiredArgsConstructor
public class GateController {

    private final VisitorRequestService service;

    /**
     * Register an incoming visitor/delivery for a flat. Multipart so the guard can
     * attach a photo of the person/ID. Either {@code unitId} or {@code unitNumber}
     * identifies the destination flat.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SECURITY') or hasAuthority('VISITOR_CREATE')")
    public ResponseEntity<ApiResponse<VisitorRequestDTO>> create(
            @RequestParam(value = "unitId", required = false) Long unitId,
            @RequestParam(value = "unitNumber", required = false) String unitNumber,
            @RequestParam("visitorName") String visitorName,
            @RequestParam(value = "company", required = false) String company,
            @RequestParam(value = "purpose", required = false) String purpose,
            @RequestParam(value = "photo", required = false) MultipartFile photo) {

        VisitorRequestDTO dto = service.createRequest(unitId, unitNumber, visitorName, company, purpose, photo);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Visitor request sent to the flat for approval.", dto));
    }

    /**
     * Active units for the gate's tappable Wing → Floor → Flat picker. Lets the guard
     * select a flat instead of typing it; stays in sync with the admin Units screen.
     */
    @GetMapping("/units")
    @PreAuthorize("hasRole('SECURITY') or hasAuthority('VISITOR_CREATE')")
    public ResponseEntity<ApiResponse<List<com.society.module.visitor.dto.GateUnitDTO>>> units() {
        return ResponseEntity.ok(ApiResponse.success(service.getActiveUnitsForGate()));
    }

    /** Poll a single request for its current decision (approved/rejected/timed out). */
    @GetMapping("/{requestId}")
    @PreAuthorize("hasRole('SECURITY') or hasAuthority('VISITOR_VIEW')")
    public ResponseEntity<ApiResponse<VisitorRequestDTO>> getById(@PathVariable Long requestId) {
        return ResponseEntity.ok(ApiResponse.success(service.getById(requestId)));
    }

    /**
     * List requests, optionally filtered by status. With no {@code status} this is the
     * full gate history/log (newest first).
     */
    @GetMapping
    @PreAuthorize("hasRole('SECURITY') or hasAuthority('VISITOR_VIEW')")
    public ResponseEntity<ApiResponse<List<VisitorRequestDTO>>> list(
            @RequestParam(value = "status", required = false) VisitorStatus status) {
        List<VisitorRequestDTO> result = (status != null)
                ? service.getByStatus(status)
                : service.getAll();
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
