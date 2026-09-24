package com.society.module.tds.dto;

import com.society.enums.TdsRemittanceStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of the deducted-TDS list, projecting a voucher's TDS deduction along with its
 * (optional) remittance status. When a line has no associated remittance batch its
 * {@link #status} is {@link TdsRemittanceStatus#DEDUCTED} and both {@link #remittanceId}
 * and {@link #challanNumber} are {@code null}.
 */
@Data
@Builder
public class TdsLineDTO {

    /** Source voucher id from which this TDS was deducted. */
    private Long voucherId;

    /** Human-readable voucher number. */
    private String voucherNumber;

    /** Date the TDS was deducted (voucher date). */
    private LocalDate deductionDate;

    /** Financial year of the deduction, e.g. {@code "2024-2025"}. */
    private String financialYear;

    /** Vendor from whom the TDS was deducted. */
    private Long vendorId;

    /** Vendor display name. */
    private String vendorName;

    /** Applicable TDS section, e.g. {@code "194C"}. */
    private String tdsSection;

    /** TDS rate applied. */
    private BigDecimal tdsRate;

    /** TDS amount deducted. */
    private BigDecimal tdsAmount;

    /** Remittance status; {@link TdsRemittanceStatus#DEDUCTED} when no batch is attached. */
    private TdsRemittanceStatus status;

    /** Associated remittance batch id; {@code null} when {@link #status} is DEDUCTED. */
    private Long remittanceId;

    /** IT Department challan number; {@code null} until PAID_TO_IT_DEPARTMENT. */
    private String challanNumber;

    /** Date the batch was paid to the accountant. */
    private LocalDate paidToAccountantDate;

    /** Date the batch was remitted to the IT Department. */
    private LocalDate paidToItDate;
}
