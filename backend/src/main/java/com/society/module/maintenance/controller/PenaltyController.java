package com.society.module.maintenance.controller;

import com.society.common.ApiResponse;
import com.society.module.maintenance.dto.PenaltyDTO;
import com.society.module.maintenance.service.PenaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/maintenance/penalties")
@RequiredArgsConstructor
public class PenaltyController {

    private final PenaltyService penaltyService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SECRETARY', 'TREASURER')")
    public ResponseEntity<ApiResponse<PenaltyDTO>> addPenalty(@RequestBody PenaltyDTO request) {
        PenaltyDTO penalty = penaltyService.addPenalty(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Penalty added successfully", penalty));
    }

    @GetMapping("/unit/{unitId}")
    public ResponseEntity<ApiResponse<List<PenaltyDTO>>> getPenaltiesByUnit(@PathVariable Long unitId) {
        List<PenaltyDTO> penalties = penaltyService.getPenaltiesByUnit(unitId);
        return ResponseEntity.ok(ApiResponse.success(penalties));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<PenaltyDTO>>> getPendingPenalties() {
        List<PenaltyDTO> penalties = penaltyService.getPendingPenalties();
        return ResponseEntity.ok(ApiResponse.success(penalties));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PenaltyDTO>>> getPenaltiesByMonth(
            @RequestParam int month, @RequestParam int year) {
        List<PenaltyDTO> penalties = penaltyService.getPenaltiesByMonthYear(month, year);
        return ResponseEntity.ok(ApiResponse.success(penalties));
    }

    @PutMapping("/{penaltyId}/cancel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SECRETARY', 'TREASURER')")
    public ResponseEntity<ApiResponse<PenaltyDTO>> cancelPenalty(@PathVariable Long penaltyId) {
        PenaltyDTO penalty = penaltyService.cancelPenalty(penaltyId);
        return ResponseEntity.ok(ApiResponse.success("Penalty cancelled", penalty));
    }
}
