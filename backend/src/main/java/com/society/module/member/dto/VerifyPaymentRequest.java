package com.society.module.member.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class VerifyPaymentRequest {

    /**
     * Which gateway: RAZORPAY or CASHFREE
     */
    private String gateway;

    // Razorpay fields
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpaySignature;

    // Cashfree fields
    private String cashfreeOrderId;

    @NotNull(message = "Unit ID is required")
    private Long unitId;

    @NotNull(message = "Amount is required")
    private BigDecimal amount;

    /**
     * Optional: specific bill ID if payment was for a specific bill.
     */
    private Long billId;

    /**
     * Discount amount that was applied (set by backend, not frontend).
     * Used to adjust bill totals so discount doesn't become arrears.
     */
    private BigDecimal discountAmount;
}
