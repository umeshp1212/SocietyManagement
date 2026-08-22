package com.society.module.vendorcategory.controller;

import com.society.common.ApiResponse;
import com.society.module.vendorcategory.dto.VendorCategoryCreateRequest;
import com.society.module.vendorcategory.dto.VendorCategoryDTO;
import com.society.module.vendorcategory.dto.VendorCategoryUpdateRequest;
import com.society.module.vendorcategory.service.VendorCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/vendor-categories")
@RequiredArgsConstructor
public class VendorCategoryController {

    private final VendorCategoryService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<VendorCategoryDTO>>> getAllCategories() {
        List<VendorCategoryDTO> categories = service.getAllCategories();
        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<VendorCategoryDTO>>> getActiveCategories() {
        List<VendorCategoryDTO> categories = service.getActiveCategories();
        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VendorCategoryDTO>> getCategoryById(@PathVariable Long id) {
        VendorCategoryDTO category = service.getCategoryById(id);
        return ResponseEntity.ok(ApiResponse.success(category));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<VendorCategoryDTO>> createCategory(
            @Valid @RequestBody VendorCategoryCreateRequest request) {
        VendorCategoryDTO category = service.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Vendor category created successfully", category));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VendorCategoryDTO>> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody VendorCategoryUpdateRequest request) {
        VendorCategoryDTO category = service.updateCategory(id, request);
        return ResponseEntity.ok(ApiResponse.success("Vendor category updated successfully", category));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        service.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Vendor category deleted successfully", null));
    }
}
