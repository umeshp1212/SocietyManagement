package com.society.module.owner.entity;

import com.society.common.BaseEntity;
import com.society.enums.BhkType;
import com.society.enums.OccupancyStatus;
import com.society.enums.ParkingType;
import com.society.enums.UnitType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "units")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Unit extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "unit_id")
    private Long unitId;

    @Column(name = "unit_number", nullable = false, unique = true, length = 20)
    private String unitNumber;

    @Column(name = "wing", length = 10)
    private String wing;

    @Column(name = "floor", length = 10)
    private String floor;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false)
    private UnitType unitType;

    /**
     * BHK type: 1RK, 1BHK, 2BHK, 3BHK, etc.
     * Used to determine default water tank configuration.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "bhk_type", length = 20)
    private BhkType bhkType;

    @Column(name = "area_sqft", precision = 10, scale = 2)
    private BigDecimal areaSqft;

    @Column(name = "monthly_maintenance_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyMaintenanceAmount = BigDecimal.ZERO;

    /**
     * Water charges for this unit (varies by BHK/tank config).
     * Admin sets this per unit based on actual tank allocation.
     * e.g., 1RK=550L → ₹550, 1BHK/2BHK=850L → ₹850, 3BHK=1150L → ₹1150
     */
    @Column(name = "water_charges", precision = 10, scale = 2)
    private BigDecimal waterCharges;

    /**
     * Parking type: NONE, TWO_WHEELER, FOUR_WHEELER, BOTH
     * Determines whether parking charges apply and which rate.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "parking_type", length = 20)
    @Builder.Default
    private ParkingType parkingType = ParkingType.NONE;

    /**
     * Number of two-wheeler parking slots (₹30 each per month)
     */
    @Column(name = "two_wheeler_count")
    @Builder.Default
    private Integer twoWheelerCount = 0;

    /**
     * Number of four-wheeler parking slots (₹60 each per month)
     */
    @Column(name = "four_wheeler_count")
    @Builder.Default
    private Integer fourWheelerCount = 0;

    @OneToMany(mappedBy = "unit", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<UnitOwner> unitOwners = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "occupancy_status", nullable = false)
    private OccupancyStatus occupancyStatus = OccupancyStatus.VACANT;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "ACTIVE";

    /**
     * Get the primary owner (convenience method)
     */
    public Owner getPrimaryOwner() {
        return unitOwners.stream()
                .filter(UnitOwner::getIsPrimary)
                .map(UnitOwner::getOwner)
                .findFirst()
                .orElse(null);
    }

    /**
     * Get all owner names as comma-separated string
     */
    public String getOwnerNames() {
        if (unitOwners == null || unitOwners.isEmpty()) return null;
        return unitOwners.stream()
                .map(uo -> uo.getOwner().getFullName())
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
    }
}
