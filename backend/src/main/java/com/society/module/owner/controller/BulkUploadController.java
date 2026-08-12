package com.society.module.owner.controller;

import com.society.common.ApiResponse;
import com.society.module.owner.dto.BulkUploadResultDTO;
import com.society.module.owner.service.BulkUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/bulk-upload")
@RequiredArgsConstructor
public class BulkUploadController {

    private final BulkUploadService bulkUploadService;

    // ==================== UPLOAD ENDPOINTS ====================

    @PostMapping(value = "/owners", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<BulkUploadResultDTO>> uploadOwners(
            @RequestParam("file") MultipartFile file) {

        validateFile(file);
        BulkUploadResultDTO result = bulkUploadService.bulkUploadOwners(file);

        String message = String.format("Upload complete: %d success, %d failed out of %d records",
                result.getSuccessCount(), result.getFailedCount(), result.getTotalRecords());

        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    @PostMapping(value = "/tenants", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<BulkUploadResultDTO>> uploadTenants(
            @RequestParam("file") MultipartFile file) {

        validateFile(file);
        BulkUploadResultDTO result = bulkUploadService.bulkUploadTenants(file);

        String message = String.format("Upload complete: %d success, %d failed out of %d records",
                result.getSuccessCount(), result.getFailedCount(), result.getTotalRecords());

        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    // ==================== TEMPLATE DOWNLOADS ====================

    @GetMapping("/templates/owners")
    public ResponseEntity<Resource> downloadOwnersTemplate() {
        Resource resource = new ClassPathResource("templates/owners_template.csv");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=owners_template.csv")
                .body(resource);
    }

    @GetMapping("/templates/tenants")
    public ResponseEntity<Resource> downloadTenantsTemplate() {
        Resource resource = new ClassPathResource("templates/tenants_template.csv");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tenants_template.csv")
                .body(resource);
    }

    // ==================== VALIDATION ====================

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new com.society.exception.BusinessException("File is empty");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".csv") && !filename.endsWith(".CSV"))) {
            throw new com.society.exception.BusinessException(
                    "Invalid file format. Please upload a CSV file (.csv)");
        }

        // Max 5MB
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new com.society.exception.BusinessException("File size exceeds 5MB limit");
        }
    }
}
