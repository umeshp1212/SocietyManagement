package com.society.module.maintenance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntryDTO {
    private Long ledgerId;
    private Long billId;
    private Long unitId;
    private Long paymentId;
    private String entryType;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String source;
    private String reference;
    private String performedBy;
    private LocalDateTime performedOn;
    private String reason;
}
