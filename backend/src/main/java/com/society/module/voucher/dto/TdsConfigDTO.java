package com.society.module.voucher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TdsConfigDTO {
    private Long tdsConfigId;
    private String vendorCategory;
    private String tdsSection;
    private BigDecimal tdsRate;
    private BigDecimal thresholdAmount;
    private String description;
    private Boolean isActive;
}
