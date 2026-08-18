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

    // ===== TDS Fields =====

    @Column(name = "tds_applicable")
    @Builder.Default
    private Boolean tdsApplicable = false;

    @Column(name = "tds_section", length = 20)
    private String tdsSection;  // e.g., "194C", "194J"

    @Column(name = "tds_rate", precision = 5, scale = 2)
    private BigDecimal tdsRate;  // e.g., 2.00

    @Column(name = "tds_amount", precision = 12, scale = 2)
    private BigDecimal tdsAmount;  // calculated: amount * tdsRate / 100

    @Column(name = "net_payable", precision = 12, scale = 2)
    private BigDecimal netPayable;  // amount - tdsAmount (cheque amount)

    // ===== Approval Workflow Fields =====

    @Column(name = "viewed_by_treasurer")
    @Builder.Default
    private Boolean viewedByTreasurer = false;

    @Column(name = "treasurer_name", length = 100)
    private String treasurerName;

    @Column(name = "treasurer_viewed_on")
    private LocalDateTime treasurerViewedOn;

    @Column(name = "verified_by_secretary")
    @Builder.Default
    private Boolean verifiedBySecretary = false;

    @Column(name = "secretary_name", length = 100)
    private String secretaryName;

    @Column(name = "secretary_verified_on")
    private LocalDateTime secretaryVerifiedOn;

    @Column(name = "approved_by_chairman")
    @Builder.Default
    private Boolean approvedByChairman = false;

    @Column(name = "chairman_name", length = 100)
    private String chairmanName;

    @Column(name = "chairman_approved_on")
    private LocalDateTime chairmanApprovedOn;

    @OneToMany(mappedBy = "voucher", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<VoucherDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "voucher", cascade = CascadeType.ALL)
    @Builder.Default
    private List<VoucherAuditTrail> auditTrails = new ArrayList<>();
}
