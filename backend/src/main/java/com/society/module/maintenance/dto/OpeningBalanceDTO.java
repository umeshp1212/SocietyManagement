package com.society.module.maintenance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpeningBalanceDTO {
    private Long openingBalanceId;
    private Long unitId;
    private String unitNumber;
    private String ownerName;
    private BigDecimal amount;
    private LocalDate asOfDate;
    private String remarks;
    private String enteredBy;
    private BigDecimal paidAmount;
    private BigDecimal balanceAmount;
    private LocalDateTime createdOn;
}
