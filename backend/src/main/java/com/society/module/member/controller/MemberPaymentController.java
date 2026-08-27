package com.society.module.member.controller;

import com.society.common.ApiResponse;
import com.society.module.auth.security.JwtUtil;
import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.maintenance.service.CashfreeService;
import com.society.module.member.dto.CreatePaymentOrderRequest;
import com.society.module.member.dto.PaymentOrderResponse;
import com.society.module.member.dto.VerifyPaymentRequest;
import com.society.module.member.service.PaymentGatewayRouter;
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

    private final PaymentGatewayRouter gatewayRouter;
    private final RazorpayService razorpayService;
    private final CashfreeService cashfreeService;
    private final JwtUtil jwtUtil;

    /**
     * Get the active payment gateway so the frontend knows which SDK to load.
     */
    @GetMapping("/active-gateway")
    public ResponseEntity<ApiResponse<Map<String, String>>> getActiveGateway() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("gateway", gatewayRouter.getActiveGateway());
        return ResponseEntity.ok(ApiResponse.success("Active gateway", result));
    }

    /**
     * Create a payment order using the active gateway.
     */
    @PostMapping("/create-order")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> createOrder(
            @Valid @RequestBody CreatePaymentOrderRequest request,
            @RequestHeader("Authorization") String authHeader) {
        Long ownerId = extractOwnerId(authHeader);
        PaymentOrderResponse response = gatewayRouter.createOrder(ownerId, request);
        return ResponseEntity.ok(ApiResponse.success("Payment order created", response));
    }

    /**
     * Verify payment after successful checkout (works for both gateways).
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyPayment(
            @Valid @RequestBody VerifyPaymentRequest request,
            @RequestHeader("Authorization") String authHeader) {
        Long ownerId = extractOwnerId(authHeader);
        MaintenancePayment payment = gatewayRouter.verifyAndRecordPayment(ownerId, request);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paymentId", payment.getPaymentId());
        result.put("receiptNumber", payment.getReceiptNumber());
        result.put("amount", payment.getAmount());
        result.put("status", payment.getStatus().name());
        result.put("paymentDate", payment.getPaymentDate());
        result.put("razorpayPaymentId", payment.getRazorpayPaymentId());
        result.put("cashfreeOrderId", payment.getCashfreeOrderId());

        return ResponseEntity.ok(ApiResponse.success("Payment verified and recorded successfully", result));
    }

    /**
     * Razorpay webhook (server-to-server callback). PUBLIC — no auth.
     */
    @PostMapping("/webhook/razorpay")
    public ResponseEntity<String> handleRazorpayWebhook(
            @RequestBody String webhookBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        razorpayService.handleWebhook(webhookBody, signature);
        return ResponseEntity.ok("OK");
    }

    /**
     * Cashfree webhook (server-to-server callback). PUBLIC — no auth.
     */
    @PostMapping("/webhook/cashfree")
    public ResponseEntity<String> handleCashfreeWebhook(@RequestBody Map<String, Object> webhookData) {
        cashfreeService.handlePaymentWebhook(webhookData);
        return ResponseEntity.ok("OK");
    }

    /**
     * Legacy Razorpay webhook path for backward compatibility.
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleLegacyWebhook(
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
