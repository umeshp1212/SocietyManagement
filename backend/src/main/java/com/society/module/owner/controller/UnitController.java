package com.society.module.owner.controller;

import com.society.common.ApiResponse;
import com.society.common.PagedResponse;
import com.society.module.owner.dto.AddCoOwnerRequest;
import com.society.module.owner.dto.UnitCreateRequest;
import com.society.module.owner.dto.UnitDTO;
import com.society.module.owner.dto.UnitOwnerDTO;
import com.society.module.owner.service.UnitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/units")
@RequiredArgsConstructor
public class UnitController {

    private final UnitService unitService;

    @PostMapping
    public ResponseEntity<ApiResponse<UnitDTO>> createUnit(@Valid @RequestBody UnitCreateRequest request) {
        UnitDTO unit = unitService.createUnit(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Unit created successfully", unit));
    }

    @PutMapping("/{unitId}")
    public ResponseEntity<ApiResponse<UnitDTO>> updateUnit(
            @PathVariable Long unitId,
            @Valid @RequestBody UnitCreateRequest request) {
        UnitDTO unit = unitService.updateUnit(unitId, request);
        return ResponseEntity.ok(ApiResponse.success("Unit updated successfully", unit));
    }

    @GetMapping("/{unitId}")
    public ResponseEntity<ApiResponse<UnitDTO>> getUnitById(@PathVariable Long unitId) {
        UnitDTO unit = unitService.getUnitById(unitId);
        return ResponseEntity.ok(ApiResponse.success(unit));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<UnitDTO>>> getAllUnits(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String wing,
            @RequestParam(required = false) String unitType,
            @RequestParam(required = false) String occupancyStatus) {
        PagedResponse<UnitDTO> units = unitService.getAllUnits(page, size, wing, unitType, occupancyStatus);
        return ResponseEntity.ok(ApiResponse.success(units));
    }

    @GetMapping("/by-owner/{ownerId}")
    public ResponseEntity<ApiResponse<List<UnitDTO>>> getUnitsByOwner(@PathVariable Long ownerId) {
        List<UnitDTO> units = unitService.getUnitsByOwner(ownerId);
        return ResponseEntity.ok(ApiResponse.success(units));
    }

    @GetMapping("/vacant")
    public ResponseEntity<ApiResponse<List<UnitDTO>>> getVacantUnits() {
        List<UnitDTO> units = unitService.getVacantUnits();
        return ResponseEntity.ok(ApiResponse.success(units));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getOccupancySummary() {
        Map<String, Long> summary = unitService.getOccupancySummary();
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    // ==================== CO-OWNER MANAGEMENT ====================

    @PostMapping("/owners")
    public ResponseEntity<ApiResponse<UnitDTO>> addOwnerToUnit(
            @Valid @RequestBody AddCoOwnerRequest request) {
        UnitDTO unit = unitService.addOwnerToUnit(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Owner added to unit successfully", unit));
    }

    @DeleteMapping("/{unitId}/owners/{ownerId}")
    public ResponseEntity<ApiResponse<UnitDTO>> removeOwnerFromUnit(
            @PathVariable Long unitId,
            @PathVariable Long ownerId) {
        UnitDTO unit = unitService.removeOwnerFromUnit(unitId, ownerId);
        return ResponseEntity.ok(ApiResponse.success("Owner removed from unit successfully", unit));
    }

    @GetMapping("/{unitId}/owners")
    public ResponseEntity<ApiResponse<List<UnitOwnerDTO>>> getUnitOwners(@PathVariable Long unitId) {
        List<UnitOwnerDTO> owners = unitService.getUnitOwners(unitId);
        return ResponseEntity.ok(ApiResponse.success(owners));
    }
}
