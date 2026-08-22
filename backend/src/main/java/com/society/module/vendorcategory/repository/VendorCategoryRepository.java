package com.society.module.vendorcategory.repository;

import com.society.module.vendorcategory.entity.VendorCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorCategoryRepository extends JpaRepository<VendorCategoryEntity, Long> {

    List<VendorCategoryEntity> findByIsActiveTrueOrderByDisplayOrderAsc();

    List<VendorCategoryEntity> findAllByOrderByDisplayOrderAsc();

    Optional<VendorCategoryEntity> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndCategoryIdNot(String code, Long categoryId);
}
