package com.society.module.tds.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Aggregated TDS totals computed over the same filtered set as the deducted-TDS list.
 * All money values use scale 2 with {@code RoundingMode.HALF_UP}.
 */
@Data
@Builder
public class TdsSummaryDTO {

    /** Sum of TDS amounts for all lines in scope. */
    private BigDecimal totalDeducted;

    /** Sum of lines whose status is at least PAID_TO_ACCOUNTANT. */
    private BigDecimal totalPaidToAccountant;

    /** Sum of lines whose status is PAID_TO_IT_DEPARTMENT. */
    private BigDecimal totalPaidToItDept;

    /** Outstanding amount, i.e. {@code totalDeducted - totalPaidToItDept}. */
    private BigDecimal totalPending;

    /** Number of lines aggregated. */
    private long lineCount;
}
