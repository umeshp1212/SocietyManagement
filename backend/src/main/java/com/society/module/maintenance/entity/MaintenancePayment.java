package com.society.module.maintenance.entity;

import com.society.common.BaseEntity;
import com.society.module.owner.entity.Unit;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "maintenance_payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_receipt_number", columnNames = {"receipt_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenancePayment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    /**
     * Optimistic-locking version. Payments are effectively insert-only today, but this
     * guards any future mutation (e.g. reversal/refund) against concurrent updates.
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    private MaintenanceBill bill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 30)
    private PaymentMode paymentMode;

    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "cashfree_payment_id", length = 100)
    private String cashfreePaymentId;

    @Column(name = "cashfree_order_id", length = 100)
    private String cashfreeOrderId;

    @Column(name = "razorpay_payment_id", length = 100)
    private String razorpayPaymentId;

    @Column(name = "razorpay_order_id", length = 100)
    private String razorpayOrderId;

    @Column(name = "razorpay_signature", length = 255)
    private String razorpaySignature;

    @Column(name = "payer_name", length = 150)
    private String payerName;

    @Column(name = "payer_type", length = 20)
    @Builder.Default
    private String payerType = "OWNER";

    @Column(name = "receipt_number", length = 50)
    private String receiptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "verified_on")
    private LocalDateTime verifiedOn;

    @Column(name = "verified_by", length = 100)
    private String verifiedBy;

    @Column(name = "reversed_on")
    private LocalDateTime reversedOn;

    @Column(name = "reversed_by", length = 100)
    private String reversedBy;

    @Column(name = "reversal_reason", length = 500)
    private String reversalReason;

    /**
     * Original amount before discount was applied
     */
    @Column(name = "original_amount", precision = 10, scale = 2)
    private BigDecimal originalAmount;

    /**
     * Discount percentage applied (e.g., 2.00 for 2%)
     */
    @Column(name = "discount_percent", precision = 5, scale = 2)
    private BigDecimal discountPercent;

    /**
     * Discount amount deducted
     */
    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal discountAmount;

    public enum PaymentMode {
        CASHFREE_LINK, CASHFREE_QR, RAZORPAY, UPI, GPAY, PHONEPE, NEFT, RTGS, IMPS, CHEQUE, CASH, BANK_TRANSFER
    }

    public enum PaymentStatus {
        PENDING, SUCCESS, FAILED, VERIFIED, REVERSED
    }
}
