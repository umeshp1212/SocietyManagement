package com.society.module.maintenance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargeConfigDTO {

    private Long chargeConfigId;

    @NotBlank(message = "Charge code is required")
    private String chargeCode;

    @NotBlank(message = "Charge name is required")
    private String chargeName;

    private String description;

    @NotNull(message = "Calculation type is required")
    private String calculationType; // AREA_BASED or FLAT

    private BigDecimal ratePerSqft;

    private BigDecimal flatAmount;

    private String applicableTo; // ALL, PARKING, TWO_WHEELER, FOUR_WHEELER, RENTED, OWNER_OCCUPIED

    private Integer displayOrder;

    private Boolean isActive;
}
