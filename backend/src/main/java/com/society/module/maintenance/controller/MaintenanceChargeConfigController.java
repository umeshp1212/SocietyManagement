package com.society.module.maintenance.controller;

import com.society.common.ApiResponse;
import com.society.module.maintenance.dto.ChargeConfigDTO;
import com.society.module.maintenance.service.MaintenanceChargeConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/maintenance/charge-config")
@RequiredArgsConstructor
public class MaintenanceChargeConfigController {

    private final MaintenanceChargeConfigService chargeConfigService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChargeConfigDTO>>> getAllActiveChargeConfigs() {
        List<ChargeConfigDTO> configs = chargeConfigService.getAllChargeConfigs();
        return ResponseEntity.ok(ApiResponse.success("Charge configurations fetched", configs));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<ChargeConfigDTO>>> getAllChargeConfigs() {
        List<ChargeConfigDTO> configs = chargeConfigService.getAllChargeConfigsIncludeInactive();
        return ResponseEntity.ok(ApiResponse.success("All charge configurations fetched", configs));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ChargeConfigDTO>> getChargeConfigById(@PathVariable Long id) {
        ChargeConfigDTO config = chargeConfigService.getChargeConfigById(id);
        return ResponseEntity.ok(ApiResponse.success("Charge configuration fetched", config));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MAINTENANCE_CONFIG')")
    public ResponseEntity<ApiResponse<ChargeConfigDTO>> createChargeConfig(
            @Valid @RequestBody ChargeConfigDTO dto) {
        ChargeConfigDTO config = chargeConfigService.createChargeConfig(dto);
        return ResponseEntity.ok(ApiResponse.success("Charge configuration created", config));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MAINTENANCE_CONFIG')")
    public ResponseEntity<ApiResponse<ChargeConfigDTO>> updateChargeConfig(
            @PathVariable Long id, @Valid @RequestBody ChargeConfigDTO dto) {
        ChargeConfigDTO config = chargeConfigService.updateChargeConfig(id, dto);
        return ResponseEntity.ok(ApiResponse.success("Charge configuration updated", config));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MAINTENANCE_CONFIG')")
    public ResponseEntity<ApiResponse<Void>> deleteChargeConfig(@PathVariable Long id) {
        chargeConfigService.deleteChargeConfig(id);
        return ResponseEntity.ok(ApiResponse.success("Charge configuration deactivated", null));
    }
}
