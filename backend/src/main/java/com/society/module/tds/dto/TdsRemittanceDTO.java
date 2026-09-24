package com.society.module.tds.dto;

import com.society.enums.TdsRemittanceStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A TDS remittance batch together with the deducted-TDS lines it groups. Represents both
 * remittance stages: creation (PAID_TO_ACCOUNTANT) and IT Department remittance
 * (PAID_TO_IT_DEPARTMENT).
 */
@Data
@Builder
public class TdsRemittanceDTO {

    /** Batch id. */
    private Long remittanceId;

    /** System-generated batch reference. */
    private String batchReference;

    /** Current remittance status of the batch. */
    private TdsRemittanceStatus status;

    /** Financial year the batch belongs to, derived from its linked vouchers. */
    private String financialYear;

    /** Total TDS amount across all lines in the batch. */
    private BigDecimal totalAmount;

    /** Accountant the deducted TDS was paid to. */
    private String accountantName;

    /** Date the amount was paid to the accountant. */
    private LocalDate paidToAccountantDate;

    /** Reference recorded for the accountant payment. */
    private String accountantReference;

    /** IT Department challan number; {@code null} until PAID_TO_IT_DEPARTMENT. */
    private String challanNumber;

    /** Date the batch was remitted to the IT Department. */
    private LocalDate paidToItDate;

    /** Free-text remarks. */
    private String remarks;

    /** Deducted-TDS lines grouped into this batch. */
    private List<TdsLineDTO> lines;
}
