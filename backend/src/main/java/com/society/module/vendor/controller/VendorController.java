package com.society.module.vendor.controller;

import com.society.common.ApiResponse;
import com.society.common.PagedResponse;
import com.society.module.vendor.dto.*;
import com.society.module.vendor.service.VendorLedgerPdfService;
import com.society.module.vendor.service.VendorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorService vendorService;
    private final VendorLedgerPdfService vendorLedgerPdfService;

    @PostMapping
    public ResponseEntity<ApiResponse<VendorDTO>> createVendor(@Valid @RequestBody VendorCreateRequest request) {
        VendorDTO vendor = vendorService.createVendor(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Vendor created successfully", vendor));
    }

    @PutMapping("/{vendorId}")
    public ResponseEntity<ApiResponse<VendorDTO>> updateVendor(
            @PathVariable Long vendorId,
            @Valid @RequestBody VendorUpdateRequest request) {
        VendorDTO vendor = vendorService.updateVendor(vendorId, request);
        return ResponseEntity.ok(ApiResponse.success("Vendor updated successfully", vendor));
    }

    @GetMapping("/{vendorId}")
    public ResponseEntity<ApiResponse<VendorDTO>> getVendorById(@PathVariable Long vendorId) {
        VendorDTO vendor = vendorService.getVendorById(vendorId);
        return ResponseEntity.ok(ApiResponse.success(vendor));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<VendorDTO>>> getAllVendors(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {
        PagedResponse<VendorDTO> vendors = vendorService.getAllVendors(page, size, status, category, search);
        return ResponseEntity.ok(ApiResponse.success(vendors));
    }

    @GetMapping("/active-list")
    public ResponseEntity<ApiResponse<List<VendorDTO>>> getActiveVendorsList() {
        List<VendorDTO> vendors = vendorService.getActiveVendorsList();
        return ResponseEntity.ok(ApiResponse.success(vendors));
    }

    // ==================== CONTRACT ALERTS ====================

    @GetMapping("/expiring")
    public ResponseEntity<ApiResponse<List<VendorDTO>>> getExpiringContracts(
            @RequestParam(defaultValue = "30") int days) {
        List<VendorDTO> vendors = vendorService.getVendorsWithExpiringContracts(days);
        return ResponseEntity.ok(ApiResponse.success(vendors));
    }

    @GetMapping("/expired")
    public ResponseEntity<ApiResponse<List<VendorDTO>>> getExpiredContracts() {
        List<VendorDTO> vendors = vendorService.getVendorsWithExpiredContracts();
        return ResponseEntity.ok(ApiResponse.success(vendors));
    }

    // ==================== SUMMARY ====================

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVendorSummary() {
        Map<String, Object> summary = vendorService.getVendorSummary();
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    // ==================== DOCUMENTS ====================

    @GetMapping("/{vendorId}/documents")
    public ResponseEntity<ApiResponse<List<VendorDocumentDTO>>> getVendorDocuments(@PathVariable Long vendorId) {
        List<VendorDocumentDTO> documents = vendorService.getVendorDocuments(vendorId);
        return ResponseEntity.ok(ApiResponse.success(documents));
    }

    @PostMapping("/{vendorId}/documents")
    public ResponseEntity<ApiResponse<VendorDocumentDTO>> addDocument(
            @PathVariable Long vendorId,
            @RequestParam String documentName,
            @RequestParam String documentType,
            @RequestParam String filePath) {
        VendorDocumentDTO document = vendorService.addDocument(vendorId, documentName, documentType, filePath);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Document added successfully", document));
    }

    // ==================== VENDOR LEDGER ====================

    @GetMapping("/{vendorId}/ledger")
    public ResponseEntity<ApiResponse<VendorLedgerDTO>> getVendorLedger(
            @PathVariable Long vendorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        VendorLedgerDTO ledger = vendorService.getVendorLedger(vendorId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(ledger));
    }

    @GetMapping("/{vendorId}/ledger/pdf")
    public ResponseEntity<byte[]> downloadLedgerPdf(
            @PathVariable Long vendorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) throws IOException {
        byte[] pdfBytes = vendorLedgerPdfService.generateLedgerPdf(vendorId, startDate, endDate);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "vendor-ledger-" + vendorId + ".pdf");
        headers.setContentLength(pdfBytes.length);

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }
}
