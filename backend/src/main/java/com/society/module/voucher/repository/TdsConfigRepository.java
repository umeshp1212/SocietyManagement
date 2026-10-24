package com.society.module.voucher.repository;

import com.society.module.voucher.entity.TdsConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TdsConfigRepository extends JpaRepository<TdsConfig, Long> {

    Optional<TdsConfig> findByVendorCategoryAndIsActiveTrue(String vendorCategory);

    Optional<TdsConfig> findByVoucherCategoryAndIsActiveTrue(String voucherCategory);

    // Lookups that ignore is_active, used to detect existing rows (including soft-disabled
    // ones) before inserting, since the unique constraint on these columns does not care
    // about is_active.
    Optional<TdsConfig> findByVoucherCategory(String voucherCategory);

    Optional<TdsConfig> findByVendorCategory(String vendorCategory);

    List<TdsConfig> findAllByOrderByVendorCategoryAsc();

    List<TdsConfig> findByIsActiveTrueOrderByVendorCategoryAsc();

    List<TdsConfig> findByIsActiveTrueOrderByVoucherCategoryAsc();

    List<TdsConfig> findByIsActiveTrueOrderByVoucherCategoryAscVendorCategoryAsc();
}
