package com.society.module.voucher.entity;

import com.society.common.BaseEntity;
import com.society.enums.ExpenseCategory;
import com.society.enums.PaymentMode;
import com.society.enums.VoucherStatus;
import com.society.enums.VoucherType;
import com.society.module.vendor.entity.Vendor;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "vouchers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Voucher extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "voucher_id")
    private Long voucherId;

    @Column(name = "voucher_number", nullable = false, unique = true, length = 20)
    private String voucherNumber;

    @Column(name = "voucher_date", nullable = false)
    private LocalDate voucherDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "voucher_type", nullable = false)
    private VoucherType voucherType;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private ExpenseCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode")
    private PaymentMode paymentMode;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "bill_invoice_number", length = 100)
    private String billInvoiceNumber;

    @Column(name = "bill_date")
    private LocalDate billDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VoucherStatus status = VoucherStatus.DRAFT;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "cancelled_by", length = 100)
    private String cancelledBy;

    @Column(name = "cancelled_on")
    private LocalDateTime cancelledOn;

    @Column(name = "financial_year", nullable = false, length = 10)
    private String financialYear;

    @OneToMany(mappedBy = "voucher", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<VoucherDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "voucher", cascade = CascadeType.ALL)
    @Builder.Default
    private List<VoucherAuditTrail> auditTrails = new ArrayList<>();
}
