package com.society.module.maintenance.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillLineItemDTO {

    private Long lineItemId;
    private String chargeCode;
    private String chargeName;
    private String calculationType;
    private BigDecimal rate;
    private BigDecimal areaSqft;
    private BigDecimal amount;
    private Integer displayOrder;
}
