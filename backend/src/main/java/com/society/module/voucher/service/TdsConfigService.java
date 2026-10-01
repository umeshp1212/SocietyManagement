package com.society.module.voucher.service;

import com.society.exception.ResourceNotFoundException;
import com.society.module.voucher.dto.TdsConfigDTO;
import com.society.module.voucher.entity.TdsConfig;
import com.society.module.voucher.repository.TdsConfigRepository;
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
        return tdsConfigRepository.findByIsActiveTrueOrderByVendorCategoryAsc()
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
     */
    public TdsConfig getTdsConfigForCategory(String vendorCategory) {
        if (vendorCategory == null || vendorCategory.isEmpty()) return null;
        return tdsConfigRepository.findByVendorCategoryAndIsActiveTrue(vendorCategory).orElse(null);
    }

    /**
     * Calculate TDS amount for a given vendor category and bill amount.
     * Returns null if TDS is not applicable (inactive or below threshold).
     * 
     * @param vendorCategory - Can be category code (e.g., "FIRE_SAFETY") or name (e.g., "Fire Safety")
     */
    public TdsCalculation calculateTds(String vendorCategory, BigDecimal amount) {
        if (vendorCategory == null || vendorCategory.isEmpty()) return null;

        // First try exact match (code)
        TdsConfig config = getTdsConfigForCategory(vendorCategory);
        
        // If not found, try with underscore conversion for backwards compatibility
        // (handles cases where name with spaces was passed: "Fire Safety" → "FIRE_SAFETY")
        if (config == null) {
            String categoryCode = vendorCategory.toUpperCase().replace(" ", "_");
            config = getTdsConfigForCategory(categoryCode);
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
        TdsCalculation tds = calculateTds(categoryCode, voucher.getAmount());

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
        TdsConfig config = TdsConfig.builder()
                .vendorCategory(dto.getVendorCategory())
                .tdsSection(dto.getTdsSection())
                .tdsRate(dto.getTdsRate())
                .thresholdAmount(dto.getThresholdAmount())
                .description(dto.getDescription())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();

        config = tdsConfigRepository.save(config);
        return mapToDTO(config);
    }

    private TdsConfigDTO mapToDTO(TdsConfig config) {
        return TdsConfigDTO.builder()
                .tdsConfigId(config.getTdsConfigId())
                .vendorCategory(config.getVendorCategory())
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
