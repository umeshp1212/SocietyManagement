package com.society.module.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class VerifyPaymentRequest {

    @NotBlank(message = "Razorpay order ID is required")
    private String razorpayOrderId;

    @NotBlank(message = "Razorpay payment ID is required")
    private String razorpayPaymentId;

    @NotBlank(message = "Razorpay signature is required")
    private String razorpaySignature;

    @NotNull(message = "Unit ID is required")
    private Long unitId;

    @NotNull(message = "Amount is required")
    private BigDecimal amount;

    /**
     * Optional: specific bill ID if payment was for a specific bill.
     */
    private Long billId;
}
