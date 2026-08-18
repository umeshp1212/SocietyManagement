package com.society.module.voucher.controller;

import com.society.common.ApiResponse;
import com.society.common.PagedResponse;
import com.society.module.voucher.dto.*;
import com.society.module.voucher.service.VoucherPdfService;
import com.society.module.voucher.service.VoucherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/vouchers")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;
    private final VoucherPdfService voucherPdfService;

    // ==================== CRUD ====================

    @PostMapping
    public ResponseEntity<ApiResponse<VoucherDTO>> createVoucher(
            @Valid @RequestBody VoucherCreateRequest request) {
        VoucherDTO voucher = voucherService.createVoucher(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Voucher created successfully", voucher));
    }

    @PutMapping("/{voucherId}")
    public ResponseEntity<ApiResponse<VoucherDTO>> updateVoucher(
            @PathVariable Long voucherId,
            @Valid @RequestBody VoucherUpdateRequest request) {
        VoucherDTO voucher = voucherService.updateVoucher(voucherId, request);
        return ResponseEntity.ok(ApiResponse.success("Voucher updated successfully", voucher));
    }

    @PatchMapping("/{voucherId}/submit-for-approval")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'SECRETARY', 'TREASURER')")
    public ResponseEntity<ApiResponse<VoucherDTO>> submitForApproval(
            @PathVariable Long voucherId, Authentication authentication) {
        String username = authentication.getName();
        VoucherDTO voucher = voucherService.submitForApproval(voucherId, username);
        return ResponseEntity.ok(ApiResponse.success("Voucher submitted for approval", voucher));
    }

    @PatchMapping("/{voucherId}/treasurer-view")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TREASURER')")
    public ResponseEntity<ApiResponse<VoucherDTO>> treasurerView(
            @PathVariable Long voucherId, Authentication authentication) {
        String username = authentication.getName();
        VoucherDTO voucher = voucherService.treasurerView(voucherId, username);
        return ResponseEntity.ok(ApiResponse.success("Voucher viewed by treasurer", voucher));
    }

    @PatchMapping("/{voucherId}/secretary-verify")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SECRETARY')")
    public ResponseEntity<ApiResponse<VoucherDTO>> secretaryVerify(
            @PathVariable Long voucherId, Authentication authentication) {
        String username = authentication.getName();
        VoucherDTO voucher = voucherService.secretaryVerify(voucherId, username);
        return ResponseEntity.ok(ApiResponse.success("Voucher verified by secretary", voucher));
    }

    @PatchMapping("/{voucherId}/chairman-approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'CHAIRMAN')")
    public ResponseEntity<ApiResponse<VoucherDTO>> chairmanApprove(
            @PathVariable Long voucherId, Authentication authentication) {
        String username = authentication.getName();
        VoucherDTO voucher = voucherService.chairmanApprove(voucherId, username);
        return ResponseEntity.ok(ApiResponse.success("Voucher approved by chairman", voucher));
    }

    @PatchMapping("/{voucherId}/finalize")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<VoucherDTO>> finalizeVoucher(@PathVariable Long voucherId) {
        VoucherDTO voucher = voucherService.finalizeVoucher(voucherId);
        return ResponseEntity.ok(ApiResponse.success("Voucher finalized successfully", voucher));
    }

    @PatchMapping("/{voucherId}/cancel")
    public ResponseEntity<ApiResponse<VoucherDTO>> cancelVoucher(
            @PathVariable Long voucherId,
            @Valid @RequestBody VoucherCancelRequest request) {
        VoucherDTO voucher = voucherService.cancelVoucher(voucherId, request);
        return ResponseEntity.ok(ApiResponse.success("Voucher cancelled successfully", voucher));
    }

    // ==================== QUERIES ====================

    @GetMapping("/{voucherId}")
    public ResponseEntity<ApiResponse<VoucherDTO>> getVoucherById(@PathVariable Long voucherId) {
        VoucherDTO voucher = voucherService.getVoucherById(voucherId);
        return ResponseEntity.ok(ApiResponse.success(voucher));
    }

    @GetMapping("/by-number/{voucherNumber}")
    public ResponseEntity<ApiResponse<VoucherDTO>> getVoucherByNumber(@PathVariable String voucherNumber) {
        VoucherDTO voucher = voucherService.getVoucherByNumber(voucherNumber);
        return ResponseEntity.ok(ApiResponse.success(voucher));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<VoucherDTO>>> getAllVouchers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String financialYear,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String search) {
        PagedResponse<VoucherDTO> vouchers = voucherService.getAllVouchers(
                page, size, type, status, category, financialYear, startDate, endDate, search);
        return ResponseEntity.ok(ApiResponse.success(vouchers));
    }

    // ==================== AUDIT TRAIL ====================

    @GetMapping("/{voucherId}/audit-trail")
    public ResponseEntity<ApiResponse<List<VoucherAuditDTO>>> getAuditTrail(@PathVariable Long voucherId) {
        List<VoucherAuditDTO> auditTrail = voucherService.getAuditTrailByVoucher(voucherId);
        return ResponseEntity.ok(ApiResponse.success(auditTrail));
    }

    // ==================== REPORTS ====================

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVoucherSummary(
            @RequestParam(required = false) String financialYear) {
        Map<String, Object> summary = voucherService.getVoucherSummary(financialYear);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @GetMapping("/reports/category-wise")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getCategoryWiseExpenseReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<Map<String, Object>> report = voucherService.getCategoryWiseExpenseReport(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @GetMapping("/reports/vendor-wise")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getVendorWisePaymentReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<Map<String, Object>> report = voucherService.getVendorWisePaymentReport(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    // ==================== DOCUMENTS (FILE UPLOAD) ====================

    @PostMapping(value = "/{voucherId}/documents/upload", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<VoucherDocumentDTO>> uploadDocument(
            @PathVariable Long voucherId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(defaultValue = "BILL") String documentType) {
        VoucherDocumentDTO document = voucherService.uploadDocument(voucherId, file, documentType);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Document uploaded successfully", document));
    }

    @GetMapping("/{voucherId}/documents")
    public ResponseEntity<ApiResponse<java.util.List<VoucherDocumentDTO>>> getDocuments(
            @PathVariable Long voucherId) {
        java.util.List<VoucherDocumentDTO> documents = voucherService.getVoucherDocuments(voucherId);
        return ResponseEntity.ok(ApiResponse.success(documents));
    }

    // ==================== PDF DOWNLOAD / PRINT ====================

    @GetMapping("/{voucherId}/pdf")
    public ResponseEntity<byte[]> downloadVoucherPdf(@PathVariable Long voucherId) throws IOException {
        byte[] pdfBytes = voucherPdfService.generateVoucherPdf(voucherId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "Voucher_" + voucherId + ".pdf");
        headers.setContentLength(pdfBytes.length);

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    @GetMapping("/{voucherId}/pdf/view")
    public ResponseEntity<byte[]> viewVoucherPdf(@PathVariable Long voucherId) throws IOException {
        byte[] pdfBytes = voucherPdfService.generateVoucherPdf(voucherId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=Voucher_" + voucherId + ".pdf");
        headers.setContentLength(pdfBytes.length);

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    @GetMapping("/pdf/bulk")
    public ResponseEntity<byte[]> downloadBulkVoucherPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String financialYear,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) throws IOException {

        byte[] pdfBytes = voucherPdfService.generateBulkVoucherPdf(startDate, endDate, financialYear, type, status);

        String filename;
        if (financialYear != null && !financialYear.isBlank()) {
            filename = "Vouchers_FY_" + financialYear.replace("-", "_") + ".pdf";
        } else {
            filename = "Vouchers_" + startDate + "_to_" + endDate + ".pdf";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(pdfBytes.length);

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }
}
