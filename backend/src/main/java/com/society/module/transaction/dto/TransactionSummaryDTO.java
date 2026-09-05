package com.society.module.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSummaryDTO {
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
}
