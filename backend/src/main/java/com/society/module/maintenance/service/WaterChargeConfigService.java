package com.society.module.maintenance.service;

import com.society.enums.BhkType;
import com.society.exception.BusinessException;
import com.society.module.maintenance.dto.WaterChargeConfigDTO;
import com.society.module.maintenance.entity.WaterChargeConfig;
import com.society.module.maintenance.entity.WaterChargeConfig.MunicipalSplitType;
import com.society.module.maintenance.entity.WaterChargeConfig.WaterSource;
import com.society.module.maintenance.repository.WaterChargeConfigRepository;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WaterChargeConfigService {

    private final WaterChargeConfigRepository waterChargeConfigRepository;
    private final UnitRepository unitRepository;

    /**
     * Get the current active water charge config.
     */
    public WaterChargeConfigDTO getActiveConfig() {
        return waterChargeConfigRepository.findByIsActiveTrue()
                .map(this::mapToDTO)
                .orElse(null);
    }

    /**
     * Save or update water charge configuration.
     * Only one config can be active at a time.
     */
    @Transactional
    public WaterChargeConfigDTO saveConfig(WaterChargeConfigDTO dto) {
        // Deactivate any existing active config
        Optional<WaterChargeConfig> existingOpt = waterChargeConfigRepository.findByIsActiveTrue();
        existingOpt.ifPresent(existing -> {
            existing.setIsActive(false);
            waterChargeConfigRepository.save(existing);
        });

        WaterChargeConfig config = WaterChargeConfig.builder()
                .waterSource(WaterSource.valueOf(dto.getWaterSource()))
                .ratePerTank(dto.getRatePerTank())
                .fixedChargePerUnit(dto.getFixedChargePerUnit())
                .tanksRk1(dto.getTanksRk1() != null ? dto.getTanksRk1() : 2)
                .tanksBhk1(dto.getTanksBhk1() != null ? dto.getTanksBhk1() : 3)
                .tanksBhk2(dto.getTanksBhk2() != null ? dto.getTanksBhk2() : 3)
                .tanksBhk3(dto.getTanksBhk3() != null ? dto.getTanksBhk3() : 4)
                .tanksBhk4(dto.getTanksBhk4() != null ? dto.getTanksBhk4() : 5)
                .tanksShop(dto.getTanksShop() != null ? dto.getTanksShop() : 1)
                .municipalTaxAmount(dto.getMunicipalTaxAmount())
                .municipalSplitType(dto.getMunicipalSplitType() != null
                        ? MunicipalSplitType.valueOf(dto.getMunicipalSplitType()) : null)
                .municipalSurchargePerUnit(dto.getMunicipalSurchargePerUnit() != null
                        ? dto.getMunicipalSurchargePerUnit() : BigDecimal.ZERO)
                .isActive(true)
                .build();

        config = waterChargeConfigRepository.save(config);
        return mapToDTO(config);
    }

    /**
     * Compute water charge for a specific unit based on active config.
     * 
     * For PRIVATE_TANKER mode:
     *   charge = (ratePerTank × tanks for unit's BHK type) + fixedChargePerUnit
     * 
     * For MUNICIPAL mode:
     *   If EQUAL split: charge = (municipalTaxAmount / totalActiveUnits) + surcharge
     *   If BHK_BASED split: charge = (municipalTaxAmount × unitWeight / totalWeight) + surcharge
     */
    public BigDecimal computeWaterChargeForUnit(Unit unit) {
        Optional<WaterChargeConfig> configOpt = waterChargeConfigRepository.findByIsActiveTrue();
        if (configOpt.isEmpty()) {
            // No config — fall back to unit-level waterCharges if set
            return unit.getWaterCharges() != null ? unit.getWaterCharges() : BigDecimal.ZERO;
        }

        WaterChargeConfig config = configOpt.get();

        if (config.getWaterSource() == WaterSource.PRIVATE_TANKER) {
            return computePrivateTankerCharge(config, unit);
        } else {
            return computeMunicipalCharge(config, unit);
        }
    }

    private BigDecimal computePrivateTankerCharge(WaterChargeConfig config, Unit unit) {
        int tanks = getTankCountForUnit(config, unit);
        BigDecimal ratePerTank = config.getRatePerTank() != null ? config.getRatePerTank() : BigDecimal.ZERO;
        BigDecimal fixedCharge = config.getFixedChargePerUnit() != null ? config.getFixedChargePerUnit() : BigDecimal.ZERO;

        // charge = (ratePerTank × tanks) + fixedChargePerUnit
        BigDecimal tankCharge = ratePerTank.multiply(BigDecimal.valueOf(tanks));
        return tankCharge.add(fixedCharge).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal computeMunicipalCharge(WaterChargeConfig config, Unit unit) {
        BigDecimal totalTax = config.getMunicipalTaxAmount() != null ? config.getMunicipalTaxAmount() : BigDecimal.ZERO;
        BigDecimal surcharge = config.getMunicipalSurchargePerUnit() != null ? config.getMunicipalSurchargePerUnit() : BigDecimal.ZERO;

        if (totalTax.compareTo(BigDecimal.ZERO) <= 0) {
            return surcharge;
        }

        List<Unit> activeUnits = unitRepository.findAll().stream()
                .filter(u -> "ACTIVE".equals(u.getStatus()))
                .toList();

        if (activeUnits.isEmpty()) {
            return surcharge;
        }

        MunicipalSplitType splitType = config.getMunicipalSplitType() != null
                ? config.getMunicipalSplitType() : MunicipalSplitType.EQUAL;

        BigDecimal unitShare;

        if (splitType == MunicipalSplitType.EQUAL) {
            // Equal split across all active units
            unitShare = totalTax.divide(BigDecimal.valueOf(activeUnits.size()), 2, RoundingMode.HALF_UP);
        } else {
            // BHK_BASED weighted split — proportional to tank allocation
            int totalTanks = activeUnits.stream()
                    .mapToInt(u -> getTankCountForUnit(config, u))
                    .sum();

            if (totalTanks == 0) {
                unitShare = totalTax.divide(BigDecimal.valueOf(activeUnits.size()), 2, RoundingMode.HALF_UP);
            } else {
                int unitTanks = getTankCountForUnit(config, unit);
                unitShare = totalTax.multiply(BigDecimal.valueOf(unitTanks))
                        .divide(BigDecimal.valueOf(totalTanks), 2, RoundingMode.HALF_UP);
            }
        }

        return unitShare.add(surcharge).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Get tank count for a unit based on its BHK type.
     */
    public int getTankCountForUnit(WaterChargeConfig config, Unit unit) {
        BhkType bhk = unit.getBhkType();
        if (bhk == null) {
            return config.getTanksBhk1() != null ? config.getTanksBhk1() : 3; // default
        }

        return switch (bhk) {
            case RK_1 -> config.getTanksRk1() != null ? config.getTanksRk1() : 2;
            case BHK_1 -> config.getTanksBhk1() != null ? config.getTanksBhk1() : 3;
            case BHK_2 -> config.getTanksBhk2() != null ? config.getTanksBhk2() : 3;
            case BHK_3 -> config.getTanksBhk3() != null ? config.getTanksBhk3() : 4;
            case BHK_4 -> config.getTanksBhk4() != null ? config.getTanksBhk4() : 5;
            case SHOP -> config.getTanksShop() != null ? config.getTanksShop() : 1;
            case OTHER -> config.getTanksBhk1() != null ? config.getTanksBhk1() : 3;
        };
    }

    /**
     * Preview: compute water charges for all active units and return summary.
     */
    public List<WaterChargePreview> previewWaterCharges() {
        Optional<WaterChargeConfig> configOpt = waterChargeConfigRepository.findByIsActiveTrue();
        if (configOpt.isEmpty()) {
            throw new BusinessException("No active water charge configuration found");
        }

        List<Unit> activeUnits = unitRepository.findAll().stream()
                .filter(u -> "ACTIVE".equals(u.getStatus()))
                .toList();

        return activeUnits.stream().map(unit -> {
            BigDecimal charge = computeWaterChargeForUnit(unit);
            int tanks = getTankCountForUnit(configOpt.get(), unit);
            return new WaterChargePreview(
                    unit.getUnitId(),
                    unit.getUnitNumber(),
                    unit.getBhkType() != null ? unit.getBhkType().name() : "N/A",
                    tanks,
                    charge
            );
        }).toList();
    }

    // ===== MAPPER =====

    private WaterChargeConfigDTO mapToDTO(WaterChargeConfig config) {
        return WaterChargeConfigDTO.builder()
                .configId(config.getConfigId())
                .waterSource(config.getWaterSource().name())
                .ratePerTank(config.getRatePerTank())
                .fixedChargePerUnit(config.getFixedChargePerUnit())
                .tanksRk1(config.getTanksRk1())
                .tanksBhk1(config.getTanksBhk1())
                .tanksBhk2(config.getTanksBhk2())
                .tanksBhk3(config.getTanksBhk3())
                .tanksBhk4(config.getTanksBhk4())
                .tanksShop(config.getTanksShop())
                .municipalTaxAmount(config.getMunicipalTaxAmount())
                .municipalSplitType(config.getMunicipalSplitType() != null ? config.getMunicipalSplitType().name() : null)
                .municipalSurchargePerUnit(config.getMunicipalSurchargePerUnit())
                .isActive(config.getIsActive())
                .build();
    }

    // ===== INNER CLASS =====

    public record WaterChargePreview(
            Long unitId,
            String unitNumber,
            String bhkType,
            int tanks,
            BigDecimal waterCharge
    ) {}
}
