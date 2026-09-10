package com.society.module.maintenance.entity;

import com.society.common.BaseEntity;
import com.society.module.owner.entity.Unit;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Advance credit held for a specific unit: money an owner paid ABOVE what a bill owed.
 *
 * <p>Unlike a {@link SuspenseEntry} (which is for UNIDENTIFIED money whose owner is not yet
 * known), advance credit has a known owner and a known source payment. It is prepaid money
 * that belongs to the unit and can later be applied to a future bill. It therefore lives in
 * its own table and never appears on the Suspense Account screen.
 */
@Entity
@Table(name = "unit_advance_credits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnitAdvanceCredit extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "advance_credit_id")
    private Long advanceCreditId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    /** Original credit amount (the surplus that was paid over the bill). */
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Amount of this credit already applied to later bills. */
    @Column(name = "applied_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal appliedAmount = BigDecimal.ZERO;

    /** Remaining, still-available credit ( = amount - appliedAmount ). */
    @Column(name = "balance_amount", precision = 12, scale = 2)
    private BigDecimal balanceAmount;

    /** Date the money was received. */
    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    /** Payment mode the surplus arrived through (mirrors the source payment). */
    @Column(name = "payment_mode", length = 30)
    private String paymentMode;

    /** Bank/UPI/cheque reference from the source payment. */
    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    /** Payer name (the owner who overpaid). */
    @Column(name = "payer_name", length = 150)
    private String payerName;

    /** The maintenance payment this credit originated from. */
    @Column(name = "source_payment_id")
    private Long sourcePaymentId;

    /** The bill the surplus was received against (for context/traceability). */
    @Column(name = "source_bill_id")
    private Long sourceBillId;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CreditStatus status = CreditStatus.AVAILABLE;

    public enum CreditStatus {
        /** Credit has remaining balance available to apply. */
        AVAILABLE,
        /** Credit has been fully applied to bills. */
        APPLIED,
        /** Credit was voided (e.g. its source payment was reversed). */
        REVERSED
    }
}
