package com.society.module.owner.controller;

import com.society.common.ApiResponse;
import com.society.common.PagedResponse;
import com.society.module.owner.dto.*;
import com.society.module.owner.service.OwnerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/owners")
@RequiredArgsConstructor
public class OwnerController {

    private final OwnerService ownerService;

    @PostMapping
    public ResponseEntity<ApiResponse<OwnerDTO>> createOwner(@Valid @RequestBody OwnerCreateRequest request) {
        OwnerDTO owner = ownerService.createOwner(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Owner created successfully", owner));
    }

    @PutMapping("/{ownerId}")
    public ResponseEntity<ApiResponse<OwnerDTO>> updateOwner(
            @PathVariable Long ownerId,
            @Valid @RequestBody OwnerUpdateRequest request) {
        OwnerDTO owner = ownerService.updateOwner(ownerId, request);
        return ResponseEntity.ok(ApiResponse.success("Owner updated successfully", owner));
    }

    @GetMapping("/{ownerId}")
    public ResponseEntity<ApiResponse<OwnerDTO>> getOwnerById(@PathVariable Long ownerId) {
        OwnerDTO owner = ownerService.getOwnerById(ownerId);
        return ResponseEntity.ok(ApiResponse.success(owner));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<OwnerDTO>>> getAllOwners(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        PagedResponse<OwnerDTO> owners = ownerService.getAllOwners(page, size, status, search);
        return ResponseEntity.ok(ApiResponse.success(owners));
    }

    @GetMapping("/active-list")
    public ResponseEntity<ApiResponse<List<OwnerDTO>>> getActiveOwnersList() {
        List<OwnerDTO> owners = ownerService.getActiveOwnersList();
        return ResponseEntity.ok(ApiResponse.success(owners));
    }

    // ==================== TRANSFER ====================

    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<OwnershipHistoryDTO>> transferOwnership(
            @Valid @RequestBody OwnershipTransferRequest request) {
        OwnershipHistoryDTO history = ownerService.transferOwnership(request);
        return ResponseEntity.ok(ApiResponse.success("Ownership transferred successfully", history));
    }

    // ==================== HISTORY ====================

    @GetMapping("/history/unit/{unitId}")
    public ResponseEntity<ApiResponse<List<OwnershipHistoryDTO>>> getHistoryByUnit(@PathVariable Long unitId) {
        List<OwnershipHistoryDTO> history = ownerService.getOwnershipHistoryByUnit(unitId);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping("/history/owner/{ownerId}")
    public ResponseEntity<ApiResponse<List<OwnershipHistoryDTO>>> getHistoryByOwner(@PathVariable Long ownerId) {
        List<OwnershipHistoryDTO> history = ownerService.getOwnershipHistoryByOwner(ownerId);
        return ResponseEntity.ok(ApiResponse.success(history));
    }
}
