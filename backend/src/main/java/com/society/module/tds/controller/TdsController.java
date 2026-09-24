package com.society.module.tds.controller;

import com.society.common.ApiResponse;
import com.society.common.PagedResponse;
import com.society.module.tds.dto.CreateRemittanceRequest;
import com.society.module.tds.dto.RecordItRemittanceRequest;
import com.society.module.tds.dto.TdsFilterRequest;
import com.society.module.tds.dto.TdsLineDTO;
import com.society.module.tds.dto.TdsRemittanceDTO;
import com.society.module.tds.dto.TdsSummaryDTO;
import com.society.module.tds.service.TdsRemittanceService;
import com.society.module.tds.service.TdsReportPdfService;
import com.society.module.tds.service.TdsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * REST endpoints for the Vendor TDS management feature.
 *
 * <p>Exposes the read model (filtered/paginated deducted-TDS lines plus summary totals) via
 * {@link TdsService} and the remittance lifecycle (batch listing, retrieval, stage-1 creation
 * and stage-2 IT remittance) via {@link TdsRemittanceService}. Filters bind from query
 * parameters through {@link TdsFilterRequest} using {@code @ModelAttribute}, mirroring the
 * {@code VendorController} conventions for {@link ApiResponse}/{@link PagedResponse} wrappers.
 *
 * <p>Validation and business-rule failures propagate to the shared
 * {@link com.society.exception.GlobalExceptionHandler}; no exception is swallowed here.
 */
@RestController
@RequestMapping("/tds")
@RequiredArgsConstructor
public class TdsController {

    private final TdsService tdsService;
    private final TdsRemittanceService tdsRemittanceService;
    private final TdsReportPdfService tdsReportPdfService;

    // ==================== READ MODEL ====================

    /**
     * Returns a filtered, paginated list of deducted-TDS lines across all vendors, ordered by
     * deduction date descending (Requirements 1.1, 2.1, 3.1).
     */
    @GetMapping("/lines")
    public ResponseEntity<ApiResponse<PagedResponse<TdsLineDTO>>> getTdsLines(
            @ModelAttribute TdsFilterRequest filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PagedResponse<TdsLineDTO> lines = tdsService.listTdsLines(filter, pageable);
        return ResponseEntity.ok(ApiResponse.success(lines));
    }

    /**
     * Returns aggregated totals over the same filtered set the list returns (Requirement 4.1).
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<TdsSummaryDTO>> getTdsSummary(
            @ModelAttribute TdsFilterRequest filter) {
        TdsSummaryDTO summary = tdsService.summarize(filter);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    /**
     * Exports the filtered deducted-TDS lines and summary totals as a downloadable PDF report,
     * reflecting exactly the rows the list returns for the same filter (Requirements 9.1-9.5).
     */
    @GetMapping("/lines/pdf")
    public ResponseEntity<byte[]> downloadTdsReportPdf(
            @ModelAttribute TdsFilterRequest filter) throws IOException {
        byte[] pdfBytes = tdsReportPdfService.generateTdsReportPdf(filter);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "tds-report.pdf");
        headers.setContentLength(pdfBytes.length);
        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    // ==================== REMITTANCE LIFECYCLE ====================

    /**
     * Returns a filtered, paginated list of remittance batches (Requirement 8.5).
     */
    @GetMapping("/remittances")
    public ResponseEntity<ApiResponse<PagedResponse<TdsRemittanceDTO>>> getRemittances(
            @ModelAttribute TdsFilterRequest filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PagedResponse<TdsRemittanceDTO> batches = tdsRemittanceService.listBatches(filter, pageable);
        return ResponseEntity.ok(ApiResponse.success(batches));
    }

    /**
     * Returns a single remittance batch together with its lines (Requirement 8.1).
     */
    @GetMapping("/remittances/{id}")
    public ResponseEntity<ApiResponse<TdsRemittanceDTO>> getRemittance(@PathVariable Long id) {
        TdsRemittanceDTO batch = tdsRemittanceService.getBatch(id);
        return ResponseEntity.ok(ApiResponse.success(batch));
    }

    /**
     * Stage 1: group the selected deducted-TDS vouchers into a new batch and record the
     * society-to-accountant payment (Requirement 5.1).
     */
    @PostMapping("/remittances")
    public ResponseEntity<ApiResponse<TdsRemittanceDTO>> createRemittance(
            @Valid @RequestBody CreateRemittanceRequest request) {
        TdsRemittanceDTO batch = tdsRemittanceService.createBatch(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Remittance batch created successfully", batch));
    }

    /**
     * Stage 2: advance an existing {@code PAID_TO_ACCOUNTANT} batch to
     * {@code PAID_TO_IT_DEPARTMENT}, recording the IT Department challan (Requirement 6.1).
     */
    @PatchMapping("/remittances/{id}/it-remittance")
    public ResponseEntity<ApiResponse<TdsRemittanceDTO>> recordItRemittance(
            @PathVariable Long id,
            @Valid @RequestBody RecordItRemittanceRequest request) {
        TdsRemittanceDTO batch = tdsRemittanceService.recordItRemittance(id, request);
        return ResponseEntity.ok(ApiResponse.success("IT remittance recorded successfully", batch));
    }
}
