package com.society.module.voucher.service;

import com.society.enums.VendorCategory;
import com.society.exception.ResourceNotFoundException;
import com.society.module.voucher.dto.TdsConfigDTO;
import com.society.module.voucher.entity.TdsConfig;
import com.society.module.voucher.repository.TdsConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
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
     * Get TDS config for a vendor category. Returns null if not configured or inactive.
     */
    public TdsConfig getTdsConfigForCategory(VendorCategory category) {
        if (category == null) return null;
        return tdsConfigRepository.findByVendorCategoryAndIsActiveTrue(category).orElse(null);
    }

    /**
     * Calculate TDS amount for a given vendor category and bill amount.
     * Returns null if TDS is not applicable (inactive or below threshold).
     */
    public TdsCalculation calculateTds(VendorCategory category, BigDecimal amount) {
        TdsConfig config = getTdsConfigForCategory(category);
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
