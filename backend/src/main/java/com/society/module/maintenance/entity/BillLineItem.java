package com.society.module.maintenance.entity;

import com.society.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "bill_line_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillLineItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "line_item_id")
    private Long lineItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    private MaintenanceBill bill;

    @Column(name = "charge_code", nullable = false, length = 50)
    private String chargeCode;

    @Column(name = "charge_name", nullable = false, length = 100)
    private String chargeName;

    @Column(name = "calculation_type", length = 20)
    private String calculationType;

    /**
     * Rate used (per sqft or flat)
     */
    @Column(name = "rate", precision = 10, scale = 2)
    private BigDecimal rate;

    /**
     * Area used for calculation (from unit)
     */
    @Column(name = "area_sqft", precision = 10, scale = 2)
    private BigDecimal areaSqft;

    /**
     * Computed amount for this line item
     */
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /**
     * Display order matching charge config
     */
    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;
}
