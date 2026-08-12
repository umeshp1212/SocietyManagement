package com.society.module.settings.controller;

import com.society.common.ApiResponse;
import com.society.module.settings.entity.SocietySettings;
import com.society.module.settings.service.SocietySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SocietySettingsController {

    private final SocietySettingsService settingsService;

    @GetMapping
    public ResponseEntity<ApiResponse<SocietySettings>> getSettings() {
        SocietySettings settings = settingsService.getSettings();
        return ResponseEntity.ok(ApiResponse.success(settings));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<SocietySettings>> saveSettings(@RequestBody SocietySettings settings) {
        SocietySettings saved = settingsService.saveSettings(settings);
        return ResponseEntity.ok(ApiResponse.success("Settings saved successfully", saved));
    }
}
