package com.society.module.maintenance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordOfflinePaymentRequest {

    @NotNull(message = "Bill ID is required")
    private Long billId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Payment date is required")
    private LocalDate paymentDate;

    @NotNull(message = "Payment mode is required")
    private String paymentMode;

    private String transactionId;

    private String payerName;

    private String payerType;

    private String remarks;
}
