package com.society.module.tds.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Stage 1 request: group selected deducted-TDS vouchers into a batch and mark them
 * PAID_TO_ACCOUNTANT.
 */
@Data
public class CreateRemittanceRequest {

    /** Ids of the deducted-TDS vouchers to include; must be non-empty. */
    @NotEmpty
    private List<Long> voucherIds;

    /** Accountant the deducted TDS is paid to. */
    @NotBlank
    private String accountantName;

    /** Date the amount was paid to the accountant. */
    @NotNull
    private LocalDate paidToAccountantDate;

    /** Optional reference for the accountant payment. */
    private String accountantReference;

    /** Optional free-text remarks. */
    private String remarks;
}
