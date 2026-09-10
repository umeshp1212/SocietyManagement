package com.society.module.maintenance.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Read model for a unit's advance credit (prepaid money over a bill amount).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvanceCreditDTO {
    private Long advanceCreditId;
    private Long unitId;
    private String unitNumber;
    private BigDecimal amount;
    private BigDecimal appliedAmount;
    private BigDecimal balanceAmount;
    private LocalDate receivedDate;
    private String paymentMode;
    private String referenceNumber;
    private String payerName;
    private Long sourcePaymentId;
    private Long sourceBillId;
    private String remarks;
    private String status;
    private LocalDateTime createdOn;
    private String createdBy;
}
