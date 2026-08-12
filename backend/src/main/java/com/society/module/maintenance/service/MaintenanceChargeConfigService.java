package com.society.module.maintenance.service;

import com.society.exception.BusinessException;
import com.society.exception.ResourceNotFoundException;
import com.society.module.maintenance.dto.ChargeConfigDTO;
import com.society.module.maintenance.entity.MaintenanceChargeConfig;
import com.society.module.maintenance.entity.MaintenanceChargeConfig.ApplicableTo;
import com.society.module.maintenance.entity.MaintenanceChargeConfig.CalculationType;
import com.society.module.maintenance.repository.MaintenanceChargeConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaintenanceChargeConfigService {

    private final MaintenanceChargeConfigRepository chargeConfigRepository;

    public List<ChargeConfigDTO> getAllChargeConfigs() {
        return chargeConfigRepository.findByIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<ChargeConfigDTO> getAllChargeConfigsIncludeInactive() {
        return chargeConfigRepository.findAll()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public ChargeConfigDTO getChargeConfigById(Long id) {
        MaintenanceChargeConfig config = chargeConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Charge config not found with id: " + id));
        return mapToDTO(config);
    }

    @Transactional
    public ChargeConfigDTO createChargeConfig(ChargeConfigDTO dto) {
        if (chargeConfigRepository.existsByChargeCode(dto.getChargeCode())) {
            throw new BusinessException("Charge code already exists: " + dto.getChargeCode());
        }

        MaintenanceChargeConfig config = mapToEntity(dto);
        config = chargeConfigRepository.save(config);
        return mapToDTO(config);
    }

    @Transactional
    public ChargeConfigDTO updateChargeConfig(Long id, ChargeConfigDTO dto) {
        MaintenanceChargeConfig config = chargeConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Charge config not found with id: " + id));

        config.setChargeName(dto.getChargeName());
        config.setDescription(dto.getDescription());
        config.setCalculationType(CalculationType.valueOf(dto.getCalculationType()));
        config.setRatePerSqft(dto.getRatePerSqft());
        config.setFlatAmount(dto.getFlatAmount());
        config.setApplicableTo(dto.getApplicableTo() != null
                ? ApplicableTo.valueOf(dto.getApplicableTo()) : ApplicableTo.ALL);
        config.setDisplayOrder(dto.getDisplayOrder() != null ? dto.getDisplayOrder() : 0);
        config.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);

        config = chargeConfigRepository.save(config);
        return mapToDTO(config);
    }

    @Transactional
    public void deleteChargeConfig(Long id) {
        MaintenanceChargeConfig config = chargeConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Charge config not found with id: " + id));
        // Soft delete - mark as inactive
        config.setIsActive(false);
        chargeConfigRepository.save(config);
    }

    private ChargeConfigDTO mapToDTO(MaintenanceChargeConfig config) {
        return ChargeConfigDTO.builder()
                .chargeConfigId(config.getChargeConfigId())
                .chargeCode(config.getChargeCode())
                .chargeName(config.getChargeName())
                .description(config.getDescription())
                .calculationType(config.getCalculationType().name())
                .ratePerSqft(config.getRatePerSqft())
                .flatAmount(config.getFlatAmount())
                .applicableTo(config.getApplicableTo().name())
                .displayOrder(config.getDisplayOrder())
                .isActive(config.getIsActive())
                .build();
    }

    private MaintenanceChargeConfig mapToEntity(ChargeConfigDTO dto) {
        return MaintenanceChargeConfig.builder()
                .chargeCode(dto.getChargeCode())
                .chargeName(dto.getChargeName())
                .description(dto.getDescription())
                .calculationType(CalculationType.valueOf(dto.getCalculationType()))
                .ratePerSqft(dto.getRatePerSqft())
                .flatAmount(dto.getFlatAmount())
                .applicableTo(dto.getApplicableTo() != null
                        ? ApplicableTo.valueOf(dto.getApplicableTo()) : ApplicableTo.ALL)
                .displayOrder(dto.getDisplayOrder() != null ? dto.getDisplayOrder() : 0)
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();
    }
}
