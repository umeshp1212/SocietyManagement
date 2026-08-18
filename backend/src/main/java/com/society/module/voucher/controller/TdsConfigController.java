package com.society.module.voucher.controller;

import com.society.common.ApiResponse;
import com.society.module.voucher.dto.TdsConfigDTO;
import com.society.module.voucher.service.TdsConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tds-config")
@RequiredArgsConstructor
public class TdsConfigController {

    private final TdsConfigService tdsConfigService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TdsConfigDTO>>> getAllTdsConfigs() {
        List<TdsConfigDTO> configs = tdsConfigService.getAllTdsConfigs();
        return ResponseEntity.ok(ApiResponse.success(configs));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<TdsConfigDTO>>> getActiveTdsConfigs() {
        List<TdsConfigDTO> configs = tdsConfigService.getActiveTdsConfigs();
        return ResponseEntity.ok(ApiResponse.success(configs));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TdsConfigDTO>> getTdsConfigById(@PathVariable Long id) {
        TdsConfigDTO config = tdsConfigService.getTdsConfigById(id);
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SECRETARY', 'TREASURER')")
    public ResponseEntity<ApiResponse<TdsConfigDTO>> createTdsConfig(@RequestBody TdsConfigDTO dto) {
        TdsConfigDTO config = tdsConfigService.createTdsConfig(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("TDS configuration created", config));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SECRETARY', 'TREASURER')")
    public ResponseEntity<ApiResponse<TdsConfigDTO>> updateTdsConfig(
            @PathVariable Long id, @RequestBody TdsConfigDTO dto) {
        TdsConfigDTO config = tdsConfigService.updateTdsConfig(id, dto);
        return ResponseEntity.ok(ApiResponse.success("TDS configuration updated", config));
    }
}
