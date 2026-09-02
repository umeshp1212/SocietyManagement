package com.society.module.maintenance.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDTO {
    private Long paymentId;
    private Long billId;
    private Long unitId;
    private String unitNumber;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private String paymentMode;
    private String transactionId;
    private String cashfreePaymentId;
    private String cashfreeOrderId;
    private String payerName;
    private String payerType;
    private String receiptNumber;
    private String status;
    private String remarks;
    private LocalDateTime verifiedOn;
    private String verifiedBy;
    private LocalDateTime reversedOn;
    private String reversedBy;
    private String reversalReason;
}
