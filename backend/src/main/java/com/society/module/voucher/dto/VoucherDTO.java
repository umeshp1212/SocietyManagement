package com.society.module.voucher.dto;

import com.society.enums.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherDTO {
    private Long voucherId;
    private String voucherNumber;
    private LocalDate voucherDate;
    private VoucherType voucherType;
    private ExpenseCategory category;
    private Long vendorId;
    private String vendorName;
    private String description;
    private BigDecimal amount;
    private PaymentMode paymentMode;
    private String referenceNumber;
    private String billInvoiceNumber;
    private LocalDate billDate;
    private VoucherStatus status;
    private String cancellationReason;
    private String cancelledBy;
    private LocalDateTime cancelledOn;
    private String financialYear;

    // TDS fields
    private Boolean tdsApplicable;
    private String tdsSection;
    private BigDecimal tdsRate;
    private BigDecimal tdsAmount;
    private BigDecimal netPayable;

    // Approval workflow fields
    private Boolean viewedByTreasurer;
    private String treasurerName;
    private LocalDateTime treasurerViewedOn;
    private Boolean verifiedBySecretary;
    private String secretaryName;
    private LocalDateTime secretaryVerifiedOn;
    private Boolean approvedByChairman;
    private String chairmanName;
    private LocalDateTime chairmanApprovedOn;

    private List<VoucherDocumentDTO> documents;
    private String createdBy;
    private LocalDateTime createdOn;
    private String modifiedBy;
    private LocalDateTime modifiedOn;
}
