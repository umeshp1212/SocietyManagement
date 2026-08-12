package com.society.module.maintenance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateBillsRequest {

    @NotNull(message = "Month is required")
    @Min(1) @Max(12)
    private Integer month;

    @NotNull(message = "Year is required")
    @Min(2020) @Max(2050)
    private Integer year;

    private Integer dueDayOfMonth;

    /**
     * If true, deletes existing UNPAID bills and regenerates them with current charge config.
     * Bills that are PAID or PARTIALLY_PAID will not be touched.
     */
    private Boolean regenerate;
}
