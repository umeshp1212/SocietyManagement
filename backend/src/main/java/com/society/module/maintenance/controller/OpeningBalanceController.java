package com.society.module.maintenance.controller;

import com.society.common.ApiResponse;
import com.society.module.maintenance.dto.OpeningBalanceDTO;
import com.society.module.maintenance.service.OpeningBalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/opening-balances")
@RequiredArgsConstructor
public class OpeningBalanceController {

    private final OpeningBalanceService openingBalanceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<OpeningBalanceDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(openingBalanceService.getAllOpeningBalances()));
    }

    @GetMapping("/outstanding")
    public ResponseEntity<ApiResponse<List<OpeningBalanceDTO>>> getOutstanding() {
        return ResponseEntity.ok(ApiResponse.success(openingBalanceService.getOutstandingOpeningBalances()));
    }

    @GetMapping("/unit/{unitId}")
    public ResponseEntity<ApiResponse<OpeningBalanceDTO>> getByUnit(@PathVariable Long unitId) {
        return ResponseEntity.ok(ApiResponse.success(openingBalanceService.getByUnitId(unitId)));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(openingBalanceService.getSummary()));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MAINTENANCE_OPENING_BALANCE')")
    public ResponseEntity<ApiResponse<OpeningBalanceDTO>> createOrUpdate(
            @RequestBody Map<String, Object> body, Authentication authentication) {
        Long unitId = Long.valueOf(body.get("unitId").toString());
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        LocalDate asOfDate = body.get("asOfDate") != null ? LocalDate.parse(body.get("asOfDate").toString()) : null;
        String remarks = body.get("remarks") != null ? body.get("remarks").toString() : null;

        OpeningBalanceDTO result = openingBalanceService.createOrUpdateOpeningBalance(
                unitId, amount, asOfDate, remarks, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Opening balance saved", result));
    }
}
