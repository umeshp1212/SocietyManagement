package com.society.module.maintenance.entity;

import com.society.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "maintenance_charge_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceChargeConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "charge_config_id")
    private Long chargeConfigId;

    @Column(name = "charge_code", nullable = false, unique = true, length = 50)
    private String chargeCode;

    @Column(name = "charge_name", nullable = false, length = 100)
    private String chargeName;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_type", nullable = false, length = 20)
    private CalculationType calculationType;

    /**
     * Rate per sq.ft for AREA_BASED charges (up to 6 decimal places for precision)
     */
    @Column(name = "rate_per_sqft", precision = 10, scale = 6)
    private BigDecimal ratePerSqft;

    /**
     * Fixed amount for FLAT charges
     */
    @Column(name = "flat_amount", precision = 10, scale = 2)
    private BigDecimal flatAmount;

    /**
     * Condition: ALL - applies to all units, PARKING - units with parking,
     * RENTED - units that are rented/tenant-occupied, TWO_WHEELER/FOUR_WHEELER - parking type
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "applicable_to", nullable = false, length = 30)
    @Builder.Default
    private ApplicableTo applicableTo = ApplicableTo.ALL;

    /**
     * Display order in bill line items
     */
    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    /**
     * Whether this charge is active
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    public enum CalculationType {
        AREA_BASED,  // amount = ratePerSqft * unit.areaSqft
        FLAT         // amount = flatAmount
    }

    public enum ApplicableTo {
        ALL,             // Applies to all units
        PARKING,         // Only units with parking (any type)
        TWO_WHEELER,     // Only two-wheeler parking
        FOUR_WHEELER,    // Only four-wheeler parking
        RENTED,          // Only rented/tenant-occupied units (NOC charges)
        OWNER_OCCUPIED   // Only owner-occupied units
    }
}
