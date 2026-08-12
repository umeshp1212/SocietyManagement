package com.society.module.maintenance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaterChargeConfigDTO {
    private Long configId;
    private String waterSource; // PRIVATE_TANKER or MUNICIPAL

    // Private tanker fields
    private BigDecimal ratePerTank;
    private BigDecimal fixedChargePerUnit;
    private Integer tanksRk1;
    private Integer tanksBhk1;
    private Integer tanksBhk2;
    private Integer tanksBhk3;
    private Integer tanksBhk4;
    private Integer tanksShop;

    // Municipal fields
    private BigDecimal municipalTaxAmount;
    private String municipalSplitType; // EQUAL or BHK_BASED
    private BigDecimal municipalSurchargePerUnit;

    private Boolean isActive;
}
