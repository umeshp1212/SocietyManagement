package com.society.module.maintenance.entity;

import com.society.common.BaseEntity;
import com.society.module.owner.entity.Unit;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "penalties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Penalty extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "penalty_id")
    private Long penaltyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    /**
     * Category of penalty: WATER_WASTAGE, WRONG_PARKING, NOISE_COMPLAINT, RULE_VIOLATION, OTHER
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "penalty_category", nullable = false, length = 30)
    private PenaltyCategory category;

    /**
     * Month for which penalty is to be charged (included in that month's bill)
     */
    @Column(name = "bill_month", nullable = false)
    private Integer billMonth;

    @Column(name = "bill_year", nullable = false)
    private Integer billYear;

    /**
     * Status: PENDING (not yet billed), BILLED (included in a bill), CANCELLED
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PenaltyStatus status = PenaltyStatus.PENDING;

    @Column(name = "imposed_by", length = 100)
    private String imposedBy;

    public enum PenaltyCategory {
        WATER_WASTAGE,
        WRONG_PARKING,
        NOISE_COMPLAINT,
        RULE_VIOLATION,
        DAMAGE_TO_PROPERTY,
        OTHER
    }

    public enum PenaltyStatus {
        PENDING,   // Not yet included in a bill
        BILLED,    // Included in generated bill
        CANCELLED  // Cancelled/waived off
    }
}
