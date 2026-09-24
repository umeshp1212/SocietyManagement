package com.society.module.tds.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * Stage 2 request: record the IT Department remittance for an existing batch, advancing it
 * to PAID_TO_IT_DEPARTMENT.
 */
@Data
public class RecordItRemittanceRequest {

    /** IT Department challan number. */
    @NotBlank
    private String challanNumber;

    /** Date the batch was remitted to the IT Department. */
    @NotNull
    private LocalDate paidToItDate;

    /** Optional free-text remarks. */
    private String remarks;
}
