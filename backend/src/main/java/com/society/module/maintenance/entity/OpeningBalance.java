package com.society.module.maintenance.entity;

import com.society.common.BaseEntity;
import com.society.module.owner.entity.Unit;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "opening_balances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpeningBalance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "opening_balance_id")
    private Long openingBalanceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    /**
     * Principal portion of the legacy arrears carried in from before go-live.
     * The member-facing bill shows this as "Principal Arrears".
     */
    @Column(name = "principal_arrears", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal principalArrears = BigDecimal.ZERO;

    /**
     * Interest portion of the legacy arrears carried in from before go-live.
     * The member-facing bill shows this as "Interest Arrears".
     */
    @Column(name = "interest_arrears", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal interestArrears = BigDecimal.ZERO;

    /**
     * Total legacy arrears = principalArrears + interestArrears. Kept as the single
     * figure all existing dues/outstanding logic reads, so nothing downstream breaks.
     */
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "entered_by", length = 100)
    private String enteredBy;

    @Column(name = "paid_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "balance_amount", precision = 12, scale = 2)
    private BigDecimal balanceAmount;
}
