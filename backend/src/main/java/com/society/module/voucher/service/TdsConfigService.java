package com.society.module.voucher.service;

import com.society.exception.ResourceNotFoundException;
import com.society.module.voucher.dto.TdsConfigDTO;
import com.society.module.voucher.entity.TdsConfig;
import com.society.module.voucher.entity.Voucher;
import com.society.module.voucher.repository.TdsConfigRepository;
import com.society.module.vendor.entity.Vendor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TdsConfigService {

    private final TdsConfigRepository tdsConfigRepository;

    public List<TdsConfigDTO> getAllTdsConfigs() {
        return tdsConfigRepository.findAllByOrderByVendorCategoryAsc()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<TdsConfigDTO> getActiveTdsConfigs() {
        return tdsConfigRepository.findByIsActiveTrueOrderByVoucherCategoryAscVendorCategoryAsc()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public TdsConfigDTO getTdsConfigById(Long id) {
        TdsConfig config = tdsConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TdsConfig", "tdsConfigId", id));
        return mapToDTO(config);
    }

    /**
     * Get TDS config for a vendor category code. Returns null if not configured or inactive.
     * 
     * @deprecated Use getTdsConfigByVoucherCategory() as primary method.
     * Kept for backward compatibility with existing vendor-category-based configs.
     */
    @Deprecated
    public TdsConfig getTdsConfigForCategory(String vendorCategory) {
        if (vendorCategory == null || vendorCategory.isEmpty()) return null;
        return tdsConfigRepository.findByVendorCategoryAndIsActiveTrue(vendorCategory).orElse(null);
    }

    /**
     * Get TDS config for a voucher category code. Returns null if not configured or inactive.
     * This is the primary method for new voucher-category-based TDS configuration.
     */
    public TdsConfig getTdsConfigByVoucherCategory(String voucherCategory) {
        if (voucherCategory == null || voucherCategory.isEmpty()) return null;
        return tdsConfigRepository.findByVoucherCategoryAndIsActiveTrue(voucherCategory).orElse(null);
    }

    /**
     * Calculate TDS amount for a given vendor category and bill amount.
     * Returns null if TDS is not applicable (inactive or below threshold).
     * 
     * PRIORITY ORDER:
     * 1. First tries voucher_category (primary - for new voucher-based TDS config)
     * 2. Falls back to vendor_category (backward compatibility for existing configs)
     * 3. Returns null if no matching config found or below threshold
     * 
     * @param vendorCategory - Vendor category code (e.g., "SECURITY")
     * @param voucherCategory - Voucher category code (e.g., "SECURITY")
     * @param amount - The bill amount
     */
    public TdsCalculation calculateTds(String vendorCategory, String voucherCategory, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return null;

        // First, try to find by voucher_category (primary method)
        TdsConfig config = null;
        if (voucherCategory != null && !voucherCategory.isEmpty()) {
            config = tdsConfigRepository.findByVoucherCategoryAndIsActiveTrue(voucherCategory).orElse(null);
        }

        // If not found by voucher_category, fall back to vendor_category (backward compatibility)
        if (config == null && vendorCategory != null && !vendorCategory.isEmpty()) {
            config = tdsConfigRepository.findByVendorCategoryAndIsActiveTrue(vendorCategory).orElse(null);
        }

        if (config == null) return null;

        // Check threshold
        if (config.getThresholdAmount() != null && amount.compareTo(config.getThresholdAmount()) < 0) {
            return null;  // Amount below threshold, no TDS
        }

        BigDecimal tdsAmount = amount.multiply(config.getTdsRate())
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal netPayable = amount.subtract(tdsAmount);

        return new TdsCalculation(config.getTdsSection(), config.getTdsRate(), tdsAmount, netPayable);
    }

    /**
     * Recalculate TDS for a specific voucher based on its vendor's category code.
     * This can be used to backfill TDS for existing vouchers.
     */
    @Transactional
    public Voucher recalculateTdsForVoucher(Voucher voucher) {
        Vendor vendor = voucher.getVendor();
        if (vendor == null || vendor.getCategory() == null) {
            voucher.setTdsApplicable(false);
            voucher.setTdsAmount(null);
            voucher.setTdsRate(null);
            voucher.setTdsSection(null);
            voucher.setNetPayable(voucher.getAmount());
            return voucher;
        }

        String categoryCode = vendor.getCategory().getCode();
        TdsCalculation tds = calculateTds(categoryCode, null, voucher.getAmount());

        if (tds != null) {
            voucher.setTdsApplicable(true);
            voucher.setTdsSection(tds.tdsSection());
            voucher.setTdsRate(tds.tdsRate());
            voucher.setTdsAmount(tds.tdsAmount());
            voucher.setNetPayable(tds.netPayable());
        } else {
            voucher.setTdsApplicable(false);
            voucher.setTdsAmount(null);
            voucher.setTdsRate(null);
            voucher.setTdsSection(null);
            voucher.setNetPayable(voucher.getAmount());
        }

        return voucher;
    }

    @Transactional
    public TdsConfigDTO updateTdsConfig(Long id, TdsConfigDTO dto) {
        TdsConfig config = tdsConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TdsConfig", "tdsConfigId", id));

        // Note: vendorCategory is kept for backward compatibility, voucherCategory is primary.
        // Normalize blanks to NULL so an edit never writes '' into a uniquely-constrained
        // category column (see createTdsConfig for why '' collides but NULL does not).
        config.setVendorCategory(blankToNull(dto.getVendorCategory()));
        config.setVoucherCategory(blankToNull(dto.getVoucherCategory()));
        config.setTdsSection(dto.getTdsSection());
        config.setTdsRate(dto.getTdsRate());
        config.setThresholdAmount(dto.getThresholdAmount());
        config.setDescription(dto.getDescription());
        config.setIsActive(dto.getIsActive());

        config = tdsConfigRepository.save(config);
        return mapToDTO(config);
    }

    @Transactional
    public TdsConfigDTO createTdsConfig(TdsConfigDTO dto) {
        // Normalize blank category codes to NULL. The unique constraints on
        // vendor_category and voucher_category treat an empty string '' as a real,
        // collidable value (unlike NULL, which MySQL allows to repeat). Persisting ''
        // therefore succeeds the first time and then fails every subsequent insert with
        // a duplicate-key error on the '' value. NULL is the intended "not set" marker.
        String voucherCategory = blankToNull(dto.getVoucherCategory());
        String vendorCategory = blankToNull(dto.getVendorCategory());

        // A row may already exist for this voucher/vendor category -- including a
        // soft-disabled one (is_active = false) that is hidden from the active list.
        // The unique constraint on voucher_category/vendor_category does not consider
        // is_active, so a blind insert would fail with a duplicate-key error. Upsert
        // instead: reuse the existing row and update it in place.
        TdsConfig existing = null;
        if (voucherCategory != null) {
            existing = tdsConfigRepository.findByVoucherCategory(voucherCategory).orElse(null);
        }
        if (existing == null && vendorCategory != null) {
            existing = tdsConfigRepository.findByVendorCategory(vendorCategory).orElse(null);
        }

        TdsConfig config;
        if (existing != null) {
            config = existing;
            config.setVendorCategory(vendorCategory);
            config.setVoucherCategory(voucherCategory);
            config.setTdsSection(dto.getTdsSection());
            config.setTdsRate(dto.getTdsRate());
            config.setThresholdAmount(dto.getThresholdAmount());
            config.setDescription(dto.getDescription());
            config.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        } else {
            config = TdsConfig.builder()
                    .vendorCategory(vendorCategory)
                    .voucherCategory(voucherCategory)
                    .tdsSection(dto.getTdsSection())
                    .tdsRate(dto.getTdsRate())
                    .thresholdAmount(dto.getThresholdAmount())
                    .description(dto.getDescription())
                    .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                    .build();
        }

        config = tdsConfigRepository.save(config);
        return mapToDTO(config);
    }

    /** Treat null/blank/whitespace-only category codes as "not set" (NULL). */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private TdsConfigDTO mapToDTO(TdsConfig config) {
        return TdsConfigDTO.builder()
                .tdsConfigId(config.getTdsConfigId())
                .vendorCategory(config.getVendorCategory())
                .voucherCategory(config.getVoucherCategory())
                .tdsSection(config.getTdsSection())
                .tdsRate(config.getTdsRate())
                .thresholdAmount(config.getThresholdAmount())
                .description(config.getDescription())
                .isActive(config.getIsActive())
                .build();
    }

    // Inner class for TDS calculation result
    public record TdsCalculation(String tdsSection, BigDecimal tdsRate, BigDecimal tdsAmount, BigDecimal netPayable) {}
}
