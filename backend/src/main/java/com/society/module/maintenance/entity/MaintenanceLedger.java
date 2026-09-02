package com.society.module.maintenance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Append-only audit ledger of every money mutation on a maintenance bill.
 *
 * <p>One row is written for each event that changes what an owner owes or has paid:
 * a bill being generated, a payment applied, or a payment reversed. Each row captures the
 * signed amount, the bill balance before and after, who did it, from which channel, and
 * why. Rows are NEVER updated or deleted — corrections are made by writing a new entry
 * (e.g. a reversal). This is the source of truth when an owner disputes a charge.
 */
@Entity
@Table(name = "maintenance_ledger", indexes = {
        @Index(name = "idx_ledger_bill", columnList = "bill_id"),
        @Index(name = "idx_ledger_unit", columnList = "unit_id"),
        @Index(name = "idx_ledger_payment", columnList = "payment_id"),
        @Index(name = "idx_ledger_performed", columnList = "performed_on")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ledger_id")
    private Long ledgerId;

    @Column(name = "bill_id")
    private Long billId;

    @Column(name = "unit_id")
    private Long unitId;

    /** The payment this entry relates to, if any (null for BILL_GENERATED). */
    @Column(name = "payment_id")
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 30)
    private EntryType entryType;

    /**
     * Signed amount of the mutation: positive when it increases what the owner owes or has
     * paid (bill generated, payment applied), negative when it reverses (payment reversed).
     */
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Bill balance (balance_amount) immediately BEFORE this mutation. */
    @Column(name = "balance_before", precision = 12, scale = 2)
    private BigDecimal balanceBefore;

    /** Bill balance (balance_amount) immediately AFTER this mutation. */
    @Column(name = "balance_after", precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    /** Channel the mutation came through. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private Source source;

    /** Optional gateway/reference id (cashfree/razorpay payment id, receipt number, etc.). */
    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;

    @Column(name = "performed_on", nullable = false)
    @Builder.Default
    private LocalDateTime performedOn = LocalDateTime.now();

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    public enum EntryType {
        BILL_GENERATED,
        PAYMENT_APPLIED,
        PAYMENT_REVERSED
    }

    public enum Source {
        OFFLINE,
        CASHFREE_WEBHOOK,
        CASHFREE_MEMBER,
        RAZORPAY_MEMBER,
        RAZORPAY_WEBHOOK,
        SYSTEM,
        ADMIN
    }
}
