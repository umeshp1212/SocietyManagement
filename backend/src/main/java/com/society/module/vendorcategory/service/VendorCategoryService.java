package com.society.module.vendorcategory.service;

import com.society.exception.BusinessException;
import com.society.exception.ResourceNotFoundException;
import com.society.module.vendorcategory.dto.VendorCategoryCreateRequest;
import com.society.module.vendorcategory.dto.VendorCategoryDTO;
import com.society.module.vendorcategory.dto.VendorCategoryUpdateRequest;
import com.society.module.vendorcategory.entity.VendorCategoryEntity;
import com.society.module.vendorcategory.repository.VendorCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VendorCategoryService {

    private final VendorCategoryRepository repository;

    public List<VendorCategoryDTO> getAllCategories() {
        return repository.findAllByOrderByDisplayOrderAsc()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<VendorCategoryDTO> getActiveCategories() {
        return repository.findByIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public VendorCategoryDTO getCategoryById(Long id) {
        VendorCategoryEntity category = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor Category not found with id: " + id));
        return toDTO(category);
    }

    @Transactional
    public VendorCategoryDTO createCategory(VendorCategoryCreateRequest request) {
        if (repository.existsByCode(request.getCode())) {
            throw new BusinessException("Category with code '" + request.getCode() + "' already exists");
        }

        VendorCategoryEntity category = VendorCategoryEntity.builder()
                .code(request.getCode())
                .name(request.getName())
                .description(request.getDescription())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .isActive(true)
                .build();

        category = repository.save(category);
        return toDTO(category);
    }

    @Transactional
    public VendorCategoryDTO updateCategory(Long id, VendorCategoryUpdateRequest request) {
        VendorCategoryEntity category = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor Category not found with id: " + id));

        if (repository.existsByCodeAndCategoryIdNot(request.getCode(), id)) {
            throw new BusinessException("Category with code '" + request.getCode() + "' already exists");
        }

        category.setCode(request.getCode());
        category.setName(request.getName());
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
        VendorCategoryEntity category = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor Category not found with id: " + id));
        repository.delete(category);
    }

    private VendorCategoryDTO toDTO(VendorCategoryEntity entity) {
        return VendorCategoryDTO.builder()
                .categoryId(entity.getCategoryId())
                .code(entity.getCode())
                .name(entity.getName())
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
