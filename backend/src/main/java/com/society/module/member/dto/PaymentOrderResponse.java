package com.society.module.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrderResponse {

    /**
     * Which gateway created this order: RAZORPAY or CASHFREE
     */
    private String gateway;

    // Razorpay fields
    private String razorpayOrderId;
    private String razorpayKeyId;

    // Cashfree fields
    private String cashfreeOrderId;
    private String cashfreePaymentSessionId;

    // Common fields
    private BigDecimal amount;
    private String currency;
    private String receipt;

    // Discount info
    private BigDecimal originalAmount;
    private BigDecimal discountAmount;
    private BigDecimal discountPercent;
    private Boolean discountApplied;

    // Prefill info
    private String ownerName;
    private String email;
    private String phone;
    private String unitNumber;
    private String description;
}
