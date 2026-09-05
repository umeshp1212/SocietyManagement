package com.society.module.transaction.dto;

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
public class TransactionDetailDTO {
    private Long paymentId;
    private String unitNumber;
    private String payerName;
    private String payerType;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private String paymentMode;
    private String status;
    private String transactionId;
    private String receiptNumber;

    private BigDecimal originalAmount;
    private BigDecimal discountAmount;
    private BigDecimal discountPercent;
    private String remarks;
    private LocalDateTime verifiedOn;
    private String verifiedBy;
    private LocalDateTime reversedOn;
    private String reversedBy;
    private String reversalReason;
}
