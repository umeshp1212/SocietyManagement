package com.society.module.maintenance.entity;

import com.society.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Configuration for water charges.
 * 
 * Supports two water source modes:
 * 1. PRIVATE_TANKER - Society orders water tankers from a vendor.
 *    Charge formula: (ratePerTank × numberOfTanks) + fixedChargePerUnit
 *    Tanks per BHK: 1RK=2, 1BHK=3, 2BHK=3, 3BHK=4, 4BHK=5
 *    
 * 2. MUNICIPAL - Municipal corporation supplies water and charges society-level tax.
 *    Charge formula: Society's total municipal tax is split across units.
 *    Split can be EQUAL (divide equally) or BHK_BASED (proportional to tank allocation).
 */
@Entity
@Table(name = "water_charge_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaterChargeConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id")
    private Long configId;

    /**
     * Water source type: PRIVATE_TANKER or MUNICIPAL
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "water_source", nullable = false, length = 30)
    private WaterSource waterSource;

    // ===== PRIVATE TANKER fields =====

    /**
     * Rate charged per tank (e.g., ₹300)
     */
    @Column(name = "rate_per_tank", precision = 10, scale = 2)
    private BigDecimal ratePerTank;

    /**
     * Fixed charge per flat/unit per month (e.g., ₹500)
     */
    @Column(name = "fixed_charge_per_unit", precision = 10, scale = 2)
    private BigDecimal fixedChargePerUnit;

    /**
     * Number of tanks for 1RK (default: 2)
     */
    @Column(name = "tanks_rk1")
    @Builder.Default
    private Integer tanksRk1 = 2;

    /**
     * Number of tanks for 1BHK (default: 3)
     */
    @Column(name = "tanks_bhk1")
    @Builder.Default
    private Integer tanksBhk1 = 3;

    /**
     * Number of tanks for 2BHK (default: 3)
     */
    @Column(name = "tanks_bhk2")
    @Builder.Default
    private Integer tanksBhk2 = 3;

    /**
     * Number of tanks for 3BHK (default: 4)
     */
    @Column(name = "tanks_bhk3")
    @Builder.Default
    private Integer tanksBhk3 = 4;

    /**
     * Number of tanks for 4BHK (default: 5)
     */
    @Column(name = "tanks_bhk4")
    @Builder.Default
    private Integer tanksBhk4 = 5;

    /**
     * Number of tanks for SHOP (default: 1)
     */
    @Column(name = "tanks_shop")
    @Builder.Default
    private Integer tanksShop = 1;

    // ===== MUNICIPAL fields =====

    /**
     * Total monthly municipal water tax charged to the society
     */
    @Column(name = "municipal_tax_amount", precision = 12, scale = 2)
    private BigDecimal municipalTaxAmount;

    /**
     * How municipal tax is split across units: EQUAL or BHK_BASED
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "municipal_split_type", length = 20)
    private MunicipalSplitType municipalSplitType;

    /**
     * Additional fixed surcharge per unit on top of municipal split (e.g., pump maintenance)
     */
    @Column(name = "municipal_surcharge_per_unit", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal municipalSurchargePerUnit = BigDecimal.ZERO;

    /**
     * Whether this config is currently active (only one active config at a time)
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // ===== ENUMS =====

    public enum WaterSource {
        PRIVATE_TANKER,
        MUNICIPAL
    }

    public enum MunicipalSplitType {
        EQUAL,      // Total tax ÷ number of units
        BHK_BASED   // Proportional to tank allocation (weighted split)
    }
}
