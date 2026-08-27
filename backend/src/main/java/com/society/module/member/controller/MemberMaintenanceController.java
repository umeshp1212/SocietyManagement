package com.society.module.member.controller;

import com.society.common.ApiResponse;
import com.society.common.PagedResponse;
import com.society.module.auth.security.JwtUtil;
import com.society.module.maintenance.dto.BillDTO;
import com.society.module.maintenance.dto.PaymentDTO;
import com.society.module.maintenance.service.MaintenancePdfService;
import com.society.module.member.dto.MemberDashboardResponse;
import com.society.module.member.service.MemberMaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/member/maintenance")
@RequiredArgsConstructor
public class MemberMaintenanceController {

    private final MemberMaintenanceService memberMaintenanceService;
    private final MaintenancePdfService maintenancePdfService;
    private final JwtUtil jwtUtil;

    /**
     * Get member dashboard with outstanding summary, bills, and recent payments.
     */
    @GetMapping("/dashboard/{unitId}")
    public ResponseEntity<ApiResponse<MemberDashboardResponse>> getDashboard(
            @PathVariable Long unitId,
            @RequestHeader("Authorization") String authHeader) {
        Long ownerId = extractOwnerId(authHeader);
        MemberDashboardResponse dashboard = memberMaintenanceService.getDashboard(ownerId, unitId);
        return ResponseEntity.ok(ApiResponse.success("Dashboard loaded", dashboard));
    }

    /**
     * Get all bills for a unit.
     */
    @GetMapping("/bills/{unitId}")
    public ResponseEntity<ApiResponse<List<BillDTO>>> getBills(
            @PathVariable Long unitId,
            @RequestHeader("Authorization") String authHeader) {
        Long ownerId = extractOwnerId(authHeader);
        List<BillDTO> bills = memberMaintenanceService.getBillsByUnit(ownerId, unitId);
        return ResponseEntity.ok(ApiResponse.success("Bills fetched", bills));
    }

    /**
     * Get outstanding bills for a unit.
     */
    @GetMapping("/outstanding/{unitId}")
    public ResponseEntity<ApiResponse<List<BillDTO>>> getOutstandingBills(
            @PathVariable Long unitId,
            @RequestHeader("Authorization") String authHeader) {
        Long ownerId = extractOwnerId(authHeader);
        List<BillDTO> bills = memberMaintenanceService.getOutstandingBills(ownerId, unitId);
        return ResponseEntity.ok(ApiResponse.success("Outstanding bills fetched", bills));
    }

    /**
     * Get payment history for a unit.
     */
    @GetMapping("/payments/{unitId}")
    public ResponseEntity<ApiResponse<PagedResponse<PaymentDTO>>> getPaymentHistory(
            @PathVariable Long unitId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("Authorization") String authHeader) {
        Long ownerId = extractOwnerId(authHeader);
        PagedResponse<PaymentDTO> payments = memberMaintenanceService.getPaymentHistory(ownerId, unitId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Payments fetched", payments));
    }

    /**
     * Get payments for a specific bill.
     */
    @GetMapping("/bill-payments/{unitId}/{billId}")
    public ResponseEntity<ApiResponse<List<PaymentDTO>>> getPaymentsByBill(
            @PathVariable Long unitId,
            @PathVariable Long billId,
            @RequestHeader("Authorization") String authHeader) {
        Long ownerId = extractOwnerId(authHeader);
        List<PaymentDTO> payments = memberMaintenanceService.getPaymentsByBill(ownerId, unitId, billId);
        return ResponseEntity.ok(ApiResponse.success("Bill payments fetched", payments));
    }

    /**
     * Download maintenance bill as PDF.
     */
    @GetMapping("/bill-pdf/{unitId}/{billId}")
    public ResponseEntity<byte[]> downloadBillPdf(
            @PathVariable Long unitId,
            @PathVariable Long billId,
            @RequestHeader("Authorization") String authHeader) throws IOException {
        Long ownerId = extractOwnerId(authHeader);
        // Validate access
        memberMaintenanceService.validateUnitAccess(ownerId, unitId);

        byte[] pdf = maintenancePdfService.generateBillPdf(billId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bill-" + billId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /**
     * Download payment receipt as PDF.
     */
    @GetMapping("/receipt-pdf/{unitId}/{paymentId}")
    public ResponseEntity<byte[]> downloadReceiptPdf(
            @PathVariable Long unitId,
            @PathVariable Long paymentId,
            @RequestHeader("Authorization") String authHeader) throws IOException {
        Long ownerId = extractOwnerId(authHeader);
        // Validate access
        memberMaintenanceService.validateUnitAccess(ownerId, unitId);

        byte[] pdf = maintenancePdfService.generateReceiptPdf(paymentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=receipt-" + paymentId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /**
     * Extract ownerId from JWT token.
     * In member tokens, userId claim stores the ownerId.
     */
    private Long extractOwnerId(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtUtil.extractUserId(token);
    }
}
