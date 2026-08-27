package com.society.module.member.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreatePaymentOrderRequest {

    @NotNull(message = "Unit ID is required")
    private Long unitId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Minimum payment amount is ₹1")
    private BigDecimal amount;

    /**
     * Optional: specific bill ID for full bill payment.
     * If null, payment is treated as partial/total outstanding payment.
     */
    private Long billId;
}
