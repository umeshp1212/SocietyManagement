package com.society.module.maintenance.controller;

import com.society.common.ApiResponse;
import com.society.module.maintenance.dto.UnitTagAssignmentRequest;
import com.society.module.maintenance.dto.UnitTagDTO;
import com.society.module.maintenance.service.UnitTagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD for admin-defined unit tags and the assignment of units to a tag.
 * A maintenance charge can reference a tag (via ChargeConfig.requiredTagId) to bill
 * only the units carrying it. Mutations reuse the existing MAINTENANCE_CONFIG authority
 * since managing tags is part of charge configuration.
 */
@RestController
@RequestMapping("/maintenance/unit-tags")
@RequiredArgsConstructor
public class UnitTagController {

    private final UnitTagService unitTagService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UnitTagDTO>>> getActiveTags() {
        return ResponseEntity.ok(ApiResponse.success("Unit tags fetched", unitTagService.getActiveTags()));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<UnitTagDTO>>> getAllTags() {
        return ResponseEntity.ok(ApiResponse.success("All unit tags fetched", unitTagService.getAllTags()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UnitTagDTO>> getTagById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Unit tag fetched", unitTagService.getTagById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MAINTENANCE_CONFIG')")
    public ResponseEntity<ApiResponse<UnitTagDTO>> createTag(@Valid @RequestBody UnitTagDTO dto) {
        return ResponseEntity.ok(ApiResponse.success("Unit tag created", unitTagService.createTag(dto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MAINTENANCE_CONFIG')")
    public ResponseEntity<ApiResponse<UnitTagDTO>> updateTag(@PathVariable Long id,
                                                             @Valid @RequestBody UnitTagDTO dto) {
        return ResponseEntity.ok(ApiResponse.success("Unit tag updated", unitTagService.updateTag(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MAINTENANCE_CONFIG')")
    public ResponseEntity<ApiResponse<Void>> deleteTag(@PathVariable Long id) {
        unitTagService.deleteTag(id);
        return ResponseEntity.ok(ApiResponse.success("Unit tag deleted", null));
    }

    // -------------------------------------------------------------- assignments

    @GetMapping("/{id}/units")
    public ResponseEntity<ApiResponse<List<Long>>> getAssignedUnits(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Assigned units fetched",
                unitTagService.getAssignedUnitIds(id)));
    }

    @PutMapping("/{id}/units")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MAINTENANCE_CONFIG')")
    public ResponseEntity<ApiResponse<List<Long>>> setAssignedUnits(
            @PathVariable Long id, @Valid @RequestBody UnitTagAssignmentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Unit assignments updated",
                unitTagService.setAssignedUnits(id, request.getUnitIds())));
    }
}
