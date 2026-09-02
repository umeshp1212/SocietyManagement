package com.society.module.maintenance.controller;

import com.society.common.ApiResponse;
import com.society.common.PagedResponse;
import com.society.exception.ResourceNotFoundException;
import com.society.module.maintenance.dto.BillDTO;
import com.society.module.maintenance.dto.GenerateBillsRequest;
import com.society.module.maintenance.dto.PaymentDTO;
import com.society.module.maintenance.dto.RecordOfflinePaymentRequest;
import com.society.module.maintenance.entity.MaintenanceBill;
import com.society.module.maintenance.repository.MaintenanceBillRepository;
import com.society.module.maintenance.service.CashfreeService;
import com.society.module.maintenance.service.MaintenanceBillPdfService;
import com.society.module.maintenance.service.MaintenanceBillService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/maintenance")
@RequiredArgsConstructor
public class MaintenanceController {

    private final MaintenanceBillService billService;
    private final MaintenanceBillPdfService billPdfService;
    private final CashfreeService cashfreeService;
    private final MaintenanceBillRepository billRepository;

    // ======================== BILL MANAGEMENT ========================

    @PostMapping("/bills/generate")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MAINTENANCE_CREATE')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateBills(
            @Valid @RequestBody GenerateBillsRequest request) {
        Map<String, Object> result = billService.generateMonthlyBills(request);
        return ResponseEntity.ok(ApiResponse.success("Bills generated successfully", result));
    }

    @GetMapping("/bills")
    public ResponseEntity<ApiResponse<PagedResponse<BillDTO>>> getBillsByMonth(
            @RequestParam int month,
            @RequestParam int year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PagedResponse<BillDTO> bills = billService.getBillsByMonth(month, year, page, size);
        return ResponseEntity.ok(ApiResponse.success("Bills fetched", bills));
    }

    @GetMapping("/bills/{billId}")
    public ResponseEntity<ApiResponse<BillDTO>> getBillById(@PathVariable Long billId) {
        BillDTO bill = billService.getBillById(billId);
        return ResponseEntity.ok(ApiResponse.success("Bill fetched", bill));
    }

    @GetMapping("/bills/unit/{unitId}")
    public ResponseEntity<ApiResponse<List<BillDTO>>> getBillsByUnit(@PathVariable Long unitId) {
        List<BillDTO> bills = billService.getBillsByUnit(unitId);
        return ResponseEntity.ok(ApiResponse.success("Bills fetched", bills));
    }

    @GetMapping("/bills/outstanding/{unitId}")
    public ResponseEntity<ApiResponse<List<BillDTO>>> getOutstandingBills(@PathVariable Long unitId) {
        List<BillDTO> bills = billService.getOutstandingByUnit(unitId);
        return ResponseEntity.ok(ApiResponse.success("Outstanding bills fetched", bills));
    }

    @GetMapping("/bills/outstanding-amount/{unitId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOutstandingAmount(@PathVariable Long unitId) {
        BigDecimal totalOutstanding = billService.getTotalOutstanding(unitId);
        Map<String, Object> result = new HashMap<>();
        result.put("unitId", unitId);
        result.put("totalOutstanding", totalOutstanding);
        return ResponseEntity.ok(ApiResponse.success("Outstanding amount fetched", result));
    }

    @GetMapping("/bills/defaulters")
    public ResponseEntity<ApiResponse<List<BillDTO>>> getDefaulters(
            @RequestParam int month,
            @RequestParam int year) {
        List<BillDTO> defaulters = billService.getDefaulters(month, year);
        return ResponseEntity.ok(ApiResponse.success("Defaulters fetched", defaulters));
    }

    @GetMapping("/bills/collection-summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCollectionSummary(
            @RequestParam int month,
            @RequestParam int year) {
        Map<String, Object> summary = billService.getCollectionSummary(month, year);
        return ResponseEntity.ok(ApiResponse.success("Collection summary fetched", summary));
    }

    // ======================== PAYMENT ========================

    @PostMapping("/payments/offline")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MAINTENANCE_PAYMENT')")
    public ResponseEntity<ApiResponse<PaymentDTO>> recordOfflinePayment(
            @Valid @RequestBody RecordOfflinePaymentRequest request) {
        PaymentDTO payment = billService.recordOfflinePayment(request);
        return ResponseEntity.ok(ApiResponse.success("Payment recorded", payment));
    }

    @GetMapping("/payments/unit/{unitId}")
    public ResponseEntity<ApiResponse<PagedResponse<PaymentDTO>>> getPaymentsByUnit(
            @PathVariable Long unitId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PagedResponse<PaymentDTO> payments = billService.getPaymentsByUnit(unitId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Payments fetched", payments));
    }

    @GetMapping("/payments/bill/{billId}")
    public ResponseEntity<ApiResponse<List<PaymentDTO>>> getPaymentsByBill(@PathVariable Long billId) {
        List<PaymentDTO> payments = billService.getPaymentsByBill(billId);
        return ResponseEntity.ok(ApiResponse.success("Payments fetched", payments));
    }

    /**
     * Reverse (void) a recorded payment. Admin-only, mandatory reason, fully audited.
     */
    @PostMapping("/payments/{paymentId}/reverse")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('MAINTENANCE_PAYMENT_REVERSE')")
    public ResponseEntity<ApiResponse<PaymentDTO>> reversePayment(
            @PathVariable Long paymentId,
            @Valid @RequestBody com.society.module.maintenance.dto.ReversePaymentRequest request) {
        PaymentDTO reversed = billService.reversePayment(paymentId, request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Payment reversed", reversed));
    }

    // ======================== LEDGER / AUDIT ========================

    @GetMapping("/ledger/bill/{billId}")
    public ResponseEntity<ApiResponse<List<com.society.module.maintenance.dto.LedgerEntryDTO>>> getLedgerByBill(
            @PathVariable Long billId) {
        return ResponseEntity.ok(ApiResponse.success("Ledger fetched", billService.getLedgerByBill(billId)));
    }

    @GetMapping("/ledger/unit/{unitId}")
    public ResponseEntity<ApiResponse<List<com.society.module.maintenance.dto.LedgerEntryDTO>>> getLedgerByUnit(
            @PathVariable Long unitId) {
        return ResponseEntity.ok(ApiResponse.success("Ledger fetched", billService.getLedgerByUnit(unitId)));
    }

    // ======================== CASHFREE / PAYMENT LINK ========================

    @PostMapping("/bills/{billId}/payment-link")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generatePaymentLink(@PathVariable Long billId) {
        MaintenanceBill bill = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found with id: " + billId));
        Map<String, Object> paymentLinkResponse = cashfreeService.createPaymentLink(bill);
        Map<String, Object> result = new HashMap<>();
        result.put("orderId", paymentLinkResponse.get("orderId"));
        result.put("paymentLink", paymentLinkResponse.get("paymentLink"));
        result.put("qrCode", paymentLinkResponse.get("qrCode"));
        return ResponseEntity.ok(ApiResponse.success("Payment link generated", result));
    }

    @GetMapping("/bills/{billId}/whatsapp-link")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWhatsAppShareLink(@PathVariable Long billId) {
        MaintenanceBill bill = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found with id: " + billId));
        Map<String, Object> result = cashfreeService.generateWhatsAppShareLink(bill);
        return ResponseEntity.ok(ApiResponse.success("WhatsApp link generated", result));
    }

    @GetMapping("/bills/{billId}/qr-code")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getQrCode(@PathVariable Long billId) {
        MaintenanceBill bill = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found with id: " + billId));
        Map<String, Object> result = new HashMap<>();
        if (bill.getPaymentLink() != null && !bill.getPaymentLink().isEmpty()) {
            String qrCode = cashfreeService.generateQrCode(bill.getPaymentLink());
            result.put("qrCode", qrCode);
            result.put("paymentLink", bill.getPaymentLink());
        } else {
            Map<String, Object> paymentLinkResponse = cashfreeService.createPaymentLink(bill);
            result.put("qrCode", paymentLinkResponse.get("qrCode"));
            result.put("paymentLink", paymentLinkResponse.get("paymentLink"));
        }
        return ResponseEntity.ok(ApiResponse.success("QR code fetched", result));
    }

    // ======================== WEBHOOK ========================

    /**
     * Cashfree payment webhook callback.
     * This endpoint is PUBLIC (no auth), so the payload MUST be authenticated via the
     * Cashfree webhook signature before it is trusted. We take the RAW request body
     * (not a parsed Map) because the signature is computed over the exact bytes Cashfree
     * sent; re-serializing a Map would change the string and break verification.
     *
     * Returns 200 only when the webhook was accepted (valid signature). On an invalid
     * signature we return 401 so a forged/replayed call is never silently acknowledged.
     */
    @PostMapping("/payments/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "x-webhook-signature", required = false) String signature,
            @RequestHeader(value = "x-webhook-timestamp", required = false) String timestamp) {
        boolean accepted = cashfreeService.handlePaymentWebhook(rawBody, signature, timestamp);
        if (!accepted) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body("INVALID_SIGNATURE");
        }
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/payments/status/{orderId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPaymentStatus(@PathVariable String orderId) {
        Map<String, Object> status = cashfreeService.getPaymentStatus(orderId);
        return ResponseEntity.ok(ApiResponse.success("Payment status fetched", status));
    }

    // ======================== PDF DOWNLOAD ========================

    @GetMapping("/bills/{billId}/pdf")
    public ResponseEntity<byte[]> downloadBillPdf(@PathVariable Long billId) throws IOException {
        byte[] pdf = billPdfService.generateBillPdf(billId);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=maintenance-bill-" + billId + ".pdf")
                .body(pdf);
    }

    @GetMapping("/bills/pdf/bulk")
    public ResponseEntity<byte[]> downloadBulkBillsPdf(
            @RequestParam int month, @RequestParam int year) throws IOException {
        byte[] pdf = billPdfService.generateBulkBillsPdf(month, year);
        String filename = "maintenance-bills-" + month + "-" + year + ".pdf";
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=" + filename)
                .body(pdf);
    }
}
