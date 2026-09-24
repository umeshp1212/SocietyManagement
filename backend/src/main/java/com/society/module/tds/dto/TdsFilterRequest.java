package com.society.module.tds.dto;

import com.society.enums.TdsRemittanceStatus;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Plain request DTO used to bind TDS list/summary filter query parameters.
 * Absent/blank fields contribute no filter predicate downstream (see
 * {@code TdsSpecificationBuilder}).
 */
@Data
public class TdsFilterRequest {

    /** Filter on {@code deductionDate >=} (voucher date). */
    private LocalDate deductionStartDate;

    /** Filter on {@code deductionDate <=} (voucher date). */
    private LocalDate deductionEndDate;

    /** Filter on either stage remittance date {@code >=}. */
    private LocalDate remittanceStartDate;

    /** Filter on either stage remittance date {@code <=}. */
    private LocalDate remittanceEndDate;

    /** Vendor-wise exact match. */
    private Long vendorId;

    /** Case-insensitive contains on vendor name. */
    private String vendorSearch;

    /** Multi-value status filter, combined with {@code IN(...)}. */
    private List<TdsRemittanceStatus> statuses;

    /** TDS section, e.g. {@code "194C"}. */
    private String tdsSection;

    /** Financial year, e.g. {@code "2024-2025"}. */
    private String financialYear;
}
