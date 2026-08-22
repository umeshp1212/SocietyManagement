package com.society.module.vouchercategory.controller;

import com.society.common.ApiResponse;
import com.society.module.vouchercategory.dto.VoucherCategoryCreateRequest;
import com.society.module.vouchercategory.dto.VoucherCategoryDTO;
import com.society.module.vouchercategory.dto.VoucherCategoryUpdateRequest;
import com.society.module.vouchercategory.service.VoucherCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/voucher-categories")
@RequiredArgsConstructor
public class VoucherCategoryController {

    private final VoucherCategoryService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<VoucherCategoryDTO>>> getAllCategories() {
        List<VoucherCategoryDTO> categories = service.getAllCategories();
        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<VoucherCategoryDTO>>> getActiveCategories(
            @RequestParam(required = false) String type) {
        List<VoucherCategoryDTO> categories;
        if (type != null && !type.isEmpty()) {
            categories = service.getActiveCategoriesByType(type);
        } else {
            categories = service.getActiveCategories();
        }
        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VoucherCategoryDTO>> getCategoryById(@PathVariable Long id) {
        VoucherCategoryDTO category = service.getCategoryById(id);
        return ResponseEntity.ok(ApiResponse.success(category));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<VoucherCategoryDTO>> createCategory(
            @Valid @RequestBody VoucherCategoryCreateRequest request) {
        VoucherCategoryDTO category = service.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created successfully", category));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VoucherCategoryDTO>> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody VoucherCategoryUpdateRequest request) {
        VoucherCategoryDTO category = service.updateCategory(id, request);
        return ResponseEntity.ok(ApiResponse.success("Category updated successfully", category));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        service.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Category deleted successfully", null));
    }
}
