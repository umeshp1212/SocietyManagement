package com.society.module.member.controller;

import com.society.common.ApiResponse;
import com.society.module.auth.security.JwtUtil;
import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.member.dto.CreatePaymentOrderRequest;
import com.society.module.member.dto.PaymentOrderResponse;
import com.society.module.member.dto.VerifyPaymentRequest;
import com.society.module.member.service.RazorpayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/member/payments")
@RequiredArgsConstructor
public class MemberPaymentController {

    private final RazorpayService razorpayService;
    private final JwtUtil jwtUtil;

    /**
     * Create a Razorpay payment order.
     * Member can pay total outstanding or a partial amount.
     */
    @PostMapping("/create-order")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> createOrder(
            @Valid @RequestBody CreatePaymentOrderRequest request,
            @RequestHeader("Authorization") String authHeader) {
        Long ownerId = extractOwnerId(authHeader);
        PaymentOrderResponse response = razorpayService.createOrder(ownerId, request);
        return ResponseEntity.ok(ApiResponse.success("Payment order created", response));
    }

    /**
     * Verify Razorpay payment after successful checkout.
     * Validates signature, records payment against bills.
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyPayment(
            @Valid @RequestBody VerifyPaymentRequest request,
            @RequestHeader("Authorization") String authHeader) {
        Long ownerId = extractOwnerId(authHeader);
        MaintenancePayment payment = razorpayService.verifyAndRecordPayment(ownerId, request);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paymentId", payment.getPaymentId());
        result.put("receiptNumber", payment.getReceiptNumber());
        result.put("amount", payment.getAmount());
        result.put("status", payment.getStatus().name());
        result.put("paymentDate", payment.getPaymentDate());
        result.put("razorpayPaymentId", payment.getRazorpayPaymentId());

        return ResponseEntity.ok(ApiResponse.success("Payment verified and recorded successfully", result));
    }

    /**
     * Razorpay webhook endpoint (server-to-server callback).
     * This must be PUBLIC — no auth required.
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String webhookBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        razorpayService.handleWebhook(webhookBody, signature);
        return ResponseEntity.ok("OK");
    }

    private Long extractOwnerId(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtUtil.extractUserId(token);
    }
}
