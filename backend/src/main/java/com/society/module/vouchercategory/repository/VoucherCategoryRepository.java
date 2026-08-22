package com.society.module.vouchercategory.repository;

import com.society.module.vouchercategory.entity.VoucherCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherCategoryRepository extends JpaRepository<VoucherCategory, Long> {

    List<VoucherCategory> findByIsActiveTrueOrderByDisplayOrderAsc();

    List<VoucherCategory> findByTypeAndIsActiveTrueOrderByDisplayOrderAsc(String type);

    List<VoucherCategory> findAllByOrderByDisplayOrderAsc();

    Optional<VoucherCategory> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndCategoryIdNot(String code, Long categoryId);
}
