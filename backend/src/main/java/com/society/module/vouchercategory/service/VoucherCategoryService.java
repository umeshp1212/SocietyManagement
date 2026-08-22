package com.society.module.vouchercategory.service;

import com.society.exception.BusinessException;
import com.society.exception.ResourceNotFoundException;
import com.society.module.vouchercategory.dto.VoucherCategoryCreateRequest;
import com.society.module.vouchercategory.dto.VoucherCategoryDTO;
import com.society.module.vouchercategory.dto.VoucherCategoryUpdateRequest;
import com.society.module.vouchercategory.entity.VoucherCategory;
import com.society.module.vouchercategory.repository.VoucherCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VoucherCategoryService {

    private final VoucherCategoryRepository repository;

    public List<VoucherCategoryDTO> getAllCategories() {
        return repository.findAllByOrderByDisplayOrderAsc()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<VoucherCategoryDTO> getActiveCategories() {
        return repository.findByIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<VoucherCategoryDTO> getActiveCategoriesByType(String type) {
        return repository.findByTypeAndIsActiveTrueOrderByDisplayOrderAsc(type.toUpperCase())
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public VoucherCategoryDTO getCategoryById(Long id) {
        VoucherCategory category = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher Category not found with id: " + id));
        return toDTO(category);
    }

    @Transactional
    public VoucherCategoryDTO createCategory(VoucherCategoryCreateRequest request) {
        if (repository.existsByCode(request.getCode())) {
            throw new BusinessException("Category with code '" + request.getCode() + "' already exists");
        }

        VoucherCategory category = VoucherCategory.builder()
                .code(request.getCode())
                .name(request.getName())
                .type(request.getType().toUpperCase())
                .description(request.getDescription())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .isActive(true)
                .build();

        category = repository.save(category);
        return toDTO(category);
    }

    @Transactional
    public VoucherCategoryDTO updateCategory(Long id, VoucherCategoryUpdateRequest request) {
        VoucherCategory category = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher Category not found with id: " + id));

        if (repository.existsByCodeAndCategoryIdNot(request.getCode(), id)) {
            throw new BusinessException("Category with code '" + request.getCode() + "' already exists");
        }

        category.setCode(request.getCode());
        category.setName(request.getName());
        category.setType(request.getType().toUpperCase());
        category.setDescription(request.getDescription());
        if (request.getDisplayOrder() != null) {
            category.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getIsActive() != null) {
            category.setIsActive(request.getIsActive());
        }

        category = repository.save(category);
        return toDTO(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        VoucherCategory category = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher Category not found with id: " + id));
        repository.delete(category);
    }

    private VoucherCategoryDTO toDTO(VoucherCategory entity) {
        return VoucherCategoryDTO.builder()
                .categoryId(entity.getCategoryId())
                .code(entity.getCode())
                .name(entity.getName())
                .type(entity.getType())
                .description(entity.getDescription())
                .displayOrder(entity.getDisplayOrder())
                .isActive(entity.getIsActive())
                .createdBy(entity.getCreatedBy())
                .createdOn(entity.getCreatedOn())
                .modifiedBy(entity.getModifiedBy())
                .modifiedOn(entity.getModifiedOn())
                .build();
    }
}
