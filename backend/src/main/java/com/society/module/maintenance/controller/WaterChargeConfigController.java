package com.society.module.maintenance.controller;

import com.society.common.ApiResponse;
import com.society.module.maintenance.dto.WaterChargeConfigDTO;
import com.society.module.maintenance.service.WaterChargeConfigService;
import com.society.module.maintenance.service.WaterChargeConfigService.WaterChargePreview;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/maintenance/water-charge-config")
@RequiredArgsConstructor
public class WaterChargeConfigController {

    private final WaterChargeConfigService waterChargeConfigService;

    @GetMapping
    public ResponseEntity<ApiResponse<WaterChargeConfigDTO>> getActiveConfig() {
        WaterChargeConfigDTO config = waterChargeConfigService.getActiveConfig();
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SECRETARY', 'TREASURER')")
    public ResponseEntity<ApiResponse<WaterChargeConfigDTO>> saveConfig(@RequestBody WaterChargeConfigDTO dto) {
        WaterChargeConfigDTO saved = waterChargeConfigService.saveConfig(dto);
        return ResponseEntity.ok(ApiResponse.success("Water charge configuration saved successfully", saved));
    }

    @GetMapping("/preview")
    public ResponseEntity<ApiResponse<List<WaterChargePreview>>> previewCharges() {
        List<WaterChargePreview> preview = waterChargeConfigService.previewWaterCharges();
        return ResponseEntity.ok(ApiResponse.success(preview));
    }
}
