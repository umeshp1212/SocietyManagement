package com.society.module.maintenance.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.society.exception.BusinessException;
import com.society.module.maintenance.entity.MaintenanceBill;
import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.maintenance.repository.MaintenanceBillRepository;
import com.society.module.maintenance.repository.MaintenancePaymentRepository;
import com.society.module.member.dto.CreatePaymentOrderRequest;
import com.society.module.member.dto.PaymentOrderResponse;
import com.society.module.member.dto.VerifyPaymentRequest;
import com.society.module.owner.entity.Owner;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.entity.UnitOwner;
import com.society.module.owner.repository.OwnerRepository;
import com.society.module.owner.repository.UnitOwnerRepository;
import com.society.module.settings.service.SocietySettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Month;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashfreeService {

    private final MaintenanceBillRepository billRepository;
    private final MaintenancePaymentRepository paymentRepository;
    private final SocietySettingsService settingsService;
    private final OwnerRepository ownerRepository;
    private final UnitOwnerRepository unitOwnerRepository;
    private final Environment environment;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.cashfree.app-id:}")
    private String cashfreeAppId;

    @Value("${app.cashfree.secret-key:}")
    private String cashfreeSecretKey;

    @Value("${app.cashfree.api-version:2023-08-01}")
    private String apiVersion;

    @Value("${app.cashfree.environment:sandbox}")
    private String cashfreeEnv;

    @Value("${app.cashfree.return-url:http://localhost:4200/maintenance/payment-status}")
    private String returnUrl;

    @Value("${app.cashfree.notify-url:http://localhost:8080/api/maintenance/payments/webhook}")
    private String notifyUrl;

    @Value("${app.base-url:http://localhost:8080/api}")
    private String baseUrl;

    private String getBaseApiUrl() {
        if ("sandbox".equalsIgnoreCase(cashfreeEnv)) {
            return "https://sandbox.cashfree.com/pg";
        }
        return "https://api.cashfree.com/pg";
    }

    /**
     * True when the application is running under the "prod" Spring profile.
     * Mock/fallback behaviour (fake payment links, fake "PAID" statuses) is only
     * permitted outside prod so a misconfigured production deployment can never
     * fabricate a successful payment.
     */
    private boolean isProdProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    public Map<String, Object> createPaymentLink(MaintenanceBill bill) {
        if (cashfreeAppId == null || cashfreeAppId.isBlank()) {
            if (isProdProfile()) {
                throw new BusinessException("Cashfree payment gateway is not configured. Contact society admin.");
            }
            log.warn("Cashfree is not configured. Generating mock payment link for bill: {}", bill.getBillId());
            return createMockPaymentLink(bill);
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = getBaseApiUrl() + "/orders";

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-client-id", cashfreeAppId);
            headers.set("x-client-secret", cashfreeSecretKey);
            headers.set("x-api-version", apiVersion);
            headers.setContentType(MediaType.APPLICATION_JSON);

            String orderId = "MAINT-" + bill.getBillId() + "-" + System.currentTimeMillis();

            String customerName = bill.getUnit().getOwnerNames() != null
                    ? bill.getUnit().getOwnerNames() : "Owner";

            Map<String, Object> customerDetails = new LinkedHashMap<>();
            customerDetails.put("customer_id", "UNIT-" + bill.getUnit().getUnitId());
            customerDetails.put("customer_name", customerName);
            customerDetails.put("customer_phone", "9999999999");

            Map<String, Object> orderMeta = new LinkedHashMap<>();
            orderMeta.put("return_url", returnUrl + "?order_id={order_id}");
            orderMeta.put("notify_url", notifyUrl);

            String orderNote = "Maintenance for " + bill.getUnit().getUnitNumber()
                    + " - " + Month.of(bill.getBillMonth()) + " " + bill.getBillYear();

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("order_id", orderId);
            requestBody.put("order_amount", bill.getBalanceAmount().doubleValue());
            requestBody.put("order_currency", "INR");
            requestBody.put("customer_details", customerDetails);
            requestBody.put("order_meta", orderMeta);
            requestBody.put("order_note", orderNote);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED) {
                Map<String, Object> responseBody = response.getBody();

                String cashfreeOrderId = (String) responseBody.get("order_id");
                String paymentSessionId = (String) responseBody.get("payment_session_id");
                String paymentLink = (String) responseBody.get("payment_link");

                bill.setCashfreeOrderId(cashfreeOrderId);
                bill.setPaymentLink(paymentLink);
                billRepository.save(bill);

                String qrCode = generateQrCode(paymentLink);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("orderId", cashfreeOrderId);
                result.put("paymentLink", paymentLink);
                result.put("paymentSessionId", paymentSessionId);
                result.put("qrCode", qrCode);

                log.info("Payment link created successfully for bill: {}, orderId: {}", bill.getBillId(), cashfreeOrderId);
                return result;
            } else {
                log.error("Cashfree API returned non-success status: {}", response.getStatusCode());
                if (isProdProfile()) {
                    throw new BusinessException("Failed to generate payment link. Please try again.");
                }
                return createMockPaymentLink(bill);
            }

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating Cashfree payment link for bill: {}. Error: {}", bill.getBillId(), e.getMessage(), e);
            if (isProdProfile()) {
                throw new BusinessException("Failed to generate payment link. Please try again.");
            }
            return createMockPaymentLink(bill);
        }
    }

    private Map<String, Object> createMockPaymentLink(MaintenanceBill bill) {
        String mockOrderId = "MAINT-" + bill.getBillId() + "-" + System.currentTimeMillis();
        String mockPaymentLink = "http://localhost:4200/maintenance/bill/" + bill.getBillId();

        bill.setCashfreeOrderId(mockOrderId);
        bill.setPaymentLink(mockPaymentLink);
        billRepository.save(bill);

        String qrCode = generateQrCode(mockPaymentLink);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", mockOrderId);
        result.put("paymentLink", mockPaymentLink);
        result.put("paymentSessionId", "mock-session-" + bill.getBillId());
        result.put("qrCode", qrCode);

        return result;
    }

    public String generateQrCode(String paymentUrl) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(paymentUrl, BarcodeFormat.QR_CODE, 300, 300);

            BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "PNG", outputStream);

            String base64Image = Base64.getEncoder().encodeToString(outputStream.toByteArray());
            return "data:image/png;base64," + base64Image;

        } catch (WriterException | IOException e) {
            log.error("Error generating QR code for URL: {}. Error: {}", paymentUrl, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Handle a Cashfree payment webhook.
     *
     * @param rawBody   the exact raw JSON body Cashfree POSTed (needed for signature check)
     * @param signature the value of the x-webhook-signature header
     * @param timestamp the value of the x-webhook-timestamp header
     * @return true if the webhook was authenticated and processed (or safely ignored as a
     *         duplicate/non-success); false if the signature is invalid and the caller
     *         should respond with 401 so the sender is not told it succeeded.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public boolean handlePaymentWebhook(String rawBody, String signature, String timestamp) {
        // ---- 1. Authenticate the payload BEFORE trusting anything in it ----
        if (!isWebhookSignatureValid(rawBody, signature, timestamp)) {
            log.warn("Rejected Cashfree webhook: invalid or missing signature");
            return false;
        }

        try {
            Map<String, Object> webhookData =
                    objectMapper.readValue(rawBody, Map.class);

            Map<String, Object> data = (Map<String, Object>) webhookData.get("data");
            if (data == null) {
                log.warn("Webhook data is null or missing 'data' field");
                return true; // authenticated but nothing to do
            }

            Map<String, Object> orderData = (Map<String, Object>) data.get("order");
            Map<String, Object> paymentData = (Map<String, Object>) data.get("payment");

            if (orderData == null || paymentData == null) {
                log.warn("Webhook missing order or payment data");
                return true;
            }

            String orderId = (String) orderData.get("order_id");
            String paymentStatus = (String) paymentData.get("payment_status");
            Object cfPaymentIdObj = paymentData.get("cf_payment_id");
            String paymentId = cfPaymentIdObj != null ? cfPaymentIdObj.toString() : null;
            Object paymentAmountObj = paymentData.get("payment_amount");
            BigDecimal paymentAmount = paymentAmountObj != null
                    ? new BigDecimal(paymentAmountObj.toString()) : BigDecimal.ZERO;

            log.info("Processing webhook - orderId: {}, status: {}, paymentId: {}", orderId, paymentStatus, paymentId);

            if (!"SUCCESS".equalsIgnoreCase(paymentStatus)) {
                if ("FAILED".equalsIgnoreCase(paymentStatus)) {
                    log.warn("Payment failed for orderId: {}", orderId);
                }
                return true; // authenticated; only SUCCESS credits a bill
            }

            // ---- 2. Idempotency: never credit the same Cashfree payment twice ----
            // Cashfree retries webhooks and may deliver duplicates. Dedup on the gateway's
            // unique payment id (cf_payment_id) so a replayed/duplicate event is a no-op.
            if (paymentId != null && paymentRepository.findByCashfreePaymentId(paymentId).isPresent()) {
                log.info("Duplicate webhook ignored - cashfree paymentId {} already recorded", paymentId);
                return true;
            }

            Optional<MaintenanceBill> billOptional = billRepository.findByCashfreeOrderId(orderId);
            if (billOptional.isEmpty()) {
                log.warn("No bill found for cashfree orderId: {}", orderId);
                return true;
            }

            MaintenanceBill bill = billOptional.get();

            MaintenancePayment payment = new MaintenancePayment();
            payment.setBill(bill);
            payment.setUnit(bill.getUnit());
            payment.setPaymentMode(MaintenancePayment.PaymentMode.CASHFREE_LINK);
            payment.setStatus(MaintenancePayment.PaymentStatus.SUCCESS);
            payment.setCashfreePaymentId(paymentId);
            payment.setCashfreeOrderId(orderId);
            payment.setAmount(paymentAmount);
            payment.setPaymentDate(LocalDate.now());
            payment.setReceiptNumber(generateWebhookReceiptNumber());
            paymentRepository.save(payment);

            // ---- 3. Update bill totals, clamping balance so overpay can't go negative ----
            BigDecimal paidSoFar = bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal newPaidAmount = paidSoFar.add(paymentAmount);
            bill.setPaidAmount(newPaidAmount);
            BigDecimal newBalance = bill.getTotalAmount().subtract(newPaidAmount).max(BigDecimal.ZERO);
            bill.setBalanceAmount(newBalance);

            if (newBalance.compareTo(BigDecimal.ZERO) <= 0) {
                bill.setStatus(MaintenanceBill.BillStatus.PAID);
            } else {
                bill.setStatus(MaintenanceBill.BillStatus.PARTIALLY_PAID);
            }

            billRepository.save(bill);
            log.info("Payment successful for bill: {}, amount: {}", bill.getBillId(), paymentAmount);
            return true;

        } catch (Exception e) {
            // Re-throw so the surrounding @Transactional rolls back and the controller does
            // NOT return 200. Cashfree then retries, rather than us silently losing a payment.
            log.error("Error processing payment webhook: {}", e.getMessage(), e);
            throw new BusinessException("Failed to process payment webhook");
        }
    }

    /**
     * Verify the Cashfree webhook signature.
     *
     * Cashfree computes: base64( HMAC-SHA256( timestamp + rawBody, clientSecret ) )
     * and sends it in the x-webhook-signature header (x-webhook-timestamp holds the timestamp).
     *
     * Non-prod behaviour: if Cashfree is not configured (blank secret) OUTSIDE the prod
     * profile, verification is skipped so sandbox/local testing still works. In prod a
     * blank secret or missing signature always fails closed.
     */
    private boolean isWebhookSignatureValid(String rawBody, String signature, String timestamp) {
        boolean secretConfigured = cashfreeSecretKey != null && !cashfreeSecretKey.isBlank();

        if (!secretConfigured) {
            if (isProdProfile()) {
                log.error("Cashfree secret is not configured in prod - refusing to trust webhook");
                return false;
            }
            log.warn("Cashfree secret not configured (non-prod) - skipping webhook signature check");
            return true;
        }

        if (signature == null || signature.isBlank() || timestamp == null || timestamp.isBlank()) {
            log.warn("Webhook missing signature or timestamp header");
            return false;
        }

        try {
            String payload = timestamp + rawBody;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cashfreeSecretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computed = Base64.getEncoder().encodeToString(hash);
            // Constant-time comparison to avoid timing side-channels.
            boolean matches = MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
            if (!matches) {
                log.warn("Webhook signature mismatch");
            }
            return matches;
        } catch (Exception e) {
            log.error("Error verifying webhook signature: {}", e.getMessage(), e);
            return false;
        }
    }

    private String generateWebhookReceiptNumber() {
        return "RCP-"
                + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + (new Random().nextInt(9000) + 1000);
    }

    public Map<String, Object> getPaymentStatus(String orderId) {
        if (cashfreeAppId == null || cashfreeAppId.isBlank()) {
            if (isProdProfile()) {
                // Never fabricate a PAID status in production. Failing closed means a
                // misconfigured gateway blocks payment confirmation instead of auto-confirming.
                throw new BusinessException("Cashfree payment gateway is not configured. Contact society admin.");
            }
            log.warn("Cashfree is not configured. Returning mock payment status for orderId: {}", orderId);
            Map<String, Object> mockStatus = new LinkedHashMap<>();
            mockStatus.put("order_id", orderId);
            mockStatus.put("order_status", "PAID");
            mockStatus.put("message", "Mock payment status - Cashfree not configured");
            return mockStatus;
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = getBaseApiUrl() + "/orders/" + orderId;

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-client-id", cashfreeAppId);
            headers.set("x-client-secret", cashfreeSecretKey);
            headers.set("x-api-version", apiVersion);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                log.error("Cashfree API returned non-success status for getPaymentStatus: {}", response.getStatusCode());
                Map<String, Object> errorResult = new LinkedHashMap<>();
                errorResult.put("order_id", orderId);
                errorResult.put("error", "Failed to fetch payment status");
                return errorResult;
            }

        } catch (Exception e) {
            log.error("Error fetching payment status for orderId: {}. Error: {}", orderId, e.getMessage(), e);
            Map<String, Object> errorResult = new LinkedHashMap<>();
            errorResult.put("order_id", orderId);
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }

    public Map<String, Object> generateWhatsAppShareLink(MaintenanceBill bill) {
        String paymentLink = bill.getPaymentLink();

        if (paymentLink == null || paymentLink.isBlank()) {
            Map<String, Object> paymentData = createPaymentLink(bill);
            paymentLink = (String) paymentData.get("paymentLink");
        }

        String unitNumber = bill.getUnit().getUnitNumber();
        String month = Month.of(bill.getBillMonth()).name();
        int year = bill.getBillYear();
        BigDecimal amount = bill.getBalanceAmount();

        String message = String.format(
                "Dear Owner of %s, your maintenance bill for %s %d of Rs.%s is due. Pay online: %s",
                unitNumber, month, year, amount.toPlainString(), paymentLink
        );

        String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);

        // Get owner's phone number from unit owners
        String ownerPhone = null;
        com.society.module.owner.entity.Owner primaryOwner = bill.getUnit().getPrimaryOwner();
        if (primaryOwner != null && primaryOwner.getContactNumber() != null
                && !primaryOwner.getContactNumber().isBlank()) {
            ownerPhone = primaryOwner.getContactNumber();
            // Add country code if not present
            if (!ownerPhone.startsWith("+") && !ownerPhone.startsWith("91")) {
                ownerPhone = "91" + ownerPhone;
            }
        }

        // Build WhatsApp link with phone number (sends directly to that contact)
        String whatsappLink;
        if (ownerPhone != null) {
            whatsappLink = "https://wa.me/" + ownerPhone + "?text=" + encodedMessage;
        } else {
            whatsappLink = "https://wa.me/?text=" + encodedMessage;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("whatsappLink", whatsappLink);
        result.put("ownerPhone", ownerPhone);
        result.put("message", message);
        result.put("unitNumber", unitNumber);
        return result;
    }

    // ======================== MEMBER CHECKOUT FLOW ========================

    /**
     * Create a Cashfree order for member portal checkout.
     * Returns payment_session_id for Cashfree JS SDK.
     */
    @Transactional
    public PaymentOrderResponse createMemberOrder(Long ownerId, CreatePaymentOrderRequest request) {
        if (cashfreeAppId == null || cashfreeAppId.isBlank()) {
            throw new BusinessException("Cashfree payment gateway is not configured. Contact society admin.");
        }

        boolean owns = unitOwnerRepository.existsByUnit_UnitIdAndOwner_OwnerId(request.getUnitId(), ownerId);
        if (!owns) {
            throw new BusinessException("You don't have access to this unit");
        }

        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new BusinessException("Owner not found"));

        UnitOwner unitOwner = unitOwnerRepository.findByOwner_OwnerId(ownerId).stream()
                .filter(uo -> uo.getUnit().getUnitId().equals(request.getUnitId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Unit not found"));

        Unit unit = unitOwner.getUnit();

        BigDecimal totalOutstanding = billRepository.getTotalOutstandingByUnit(request.getUnitId());
        if (totalOutstanding == null) totalOutstanding = BigDecimal.ZERO;

        if (request.getAmount().compareTo(totalOutstanding) > 0) {
            throw new BusinessException("Payment amount exceeds total outstanding");
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = getBaseApiUrl() + "/orders";

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-client-id", cashfreeAppId);
            headers.set("x-client-secret", cashfreeSecretKey);
            headers.set("x-api-version", apiVersion);
            headers.setContentType(MediaType.APPLICATION_JSON);

            log.info("Cashfree createMemberOrder - URL: {}, appId: {}, secretKey: {}...",
                    url, cashfreeAppId, cashfreeSecretKey != null && cashfreeSecretKey.length() > 10
                            ? cashfreeSecretKey.substring(0, 10) + "***" : "null");

            String orderId = "MBR-" + unit.getUnitNumber() + "-" + System.currentTimeMillis();

            Map<String, Object> customerDetails = new LinkedHashMap<>();
            customerDetails.put("customer_id", "OWNER-" + ownerId);
            customerDetails.put("customer_name", owner.getFullName());
            customerDetails.put("customer_phone", owner.getContactNumber() != null ? owner.getContactNumber() : "9999999999");
            if (owner.getEmail() != null && !owner.getEmail().isBlank()) {
                customerDetails.put("customer_email", owner.getEmail());
            }

            Map<String, Object> orderMeta = new LinkedHashMap<>();
            orderMeta.put("return_url", returnUrl + "?order_id={order_id}");
            orderMeta.put("notify_url", notifyUrl);

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("order_id", orderId);
            requestBody.put("order_amount", request.getAmount().setScale(2, RoundingMode.HALF_UP).doubleValue());
            requestBody.put("order_currency", "INR");
            requestBody.put("customer_details", customerDetails);
            requestBody.put("order_meta", orderMeta);
            requestBody.put("order_note", "Maintenance Payment - " + unit.getUnitNumber());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED) {
                Map<String, Object> responseBody = response.getBody();
                String cfOrderId = (String) responseBody.get("order_id");
                String paymentSessionId = (String) responseBody.get("payment_session_id");

                // Store order ID on bill if specific bill
                if (request.getBillId() != null) {
                    MaintenanceBill bill = billRepository.findById(request.getBillId()).orElse(null);
                    if (bill != null) {
                        bill.setCashfreeOrderId(cfOrderId);
                        billRepository.save(bill);
                    }
                }

                log.info("Cashfree order created for member - orderId: {}, amount: ₹{}, unit: {}",
                        cfOrderId, request.getAmount(), unit.getUnitNumber());

                return PaymentOrderResponse.builder()
                        .gateway("CASHFREE")
                        .cashfreeOrderId(cfOrderId)
                        .cashfreePaymentSessionId(paymentSessionId)
                        .amount(request.getAmount())
                        .currency("INR")
                        .receipt(orderId)
                        .ownerName(owner.getFullName())
                        .email(owner.getEmail())
                        .phone(owner.getContactNumber())
                        .unitNumber(unit.getUnitNumber())
                        .description("Maintenance Payment - " + unit.getUnitNumber())
                        .build();
            } else {
                throw new BusinessException("Failed to create Cashfree order. Status: " + response.getStatusCode());
            }

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create Cashfree order: {}", e.getMessage(), e);
            throw new BusinessException("Failed to create payment order. Please try again.");
        }
    }

    /**
     * Verify Cashfree payment by checking order status and record the payment.
     */
    @Transactional
    public MaintenancePayment verifyAndRecordMemberPayment(Long ownerId, VerifyPaymentRequest request) {
        boolean owns = unitOwnerRepository.existsByUnit_UnitIdAndOwner_OwnerId(request.getUnitId(), ownerId);
        if (!owns) {
            throw new BusinessException("You don't have access to this unit");
        }

        // Check order status with Cashfree API
        Map<String, Object> orderStatus = getPaymentStatus(request.getCashfreeOrderId());
        String status = (String) orderStatus.get("order_status");

        if (!"PAID".equalsIgnoreCase(status)) {
            throw new BusinessException("Payment not confirmed. Current status: " + status);
        }

        // Check for duplicate
        if (paymentRepository.findByCashfreeOrderId(request.getCashfreeOrderId()).isPresent()) {
            throw new BusinessException("This payment has already been recorded.");
        }

        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new BusinessException("Owner not found"));

        // Use same principal-first, interest-second allocation as Razorpay
        List<MaintenanceBill> outstandingBills = billRepository.findOutstandingByUnit(request.getUnitId());
        if (outstandingBills.isEmpty()) {
            throw new BusinessException("No outstanding bills found to apply payment.");
        }

        BigDecimal remainingAmount = request.getAmount();
        MaintenancePayment lastPayment = null;

        // PASS 1: Pay ALL principal across all bills (oldest first)
        for (MaintenanceBill bill : outstandingBills) {
            if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal principalOutstanding = getPrincipalOutstanding(bill);
            if (principalOutstanding.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal payForBill = remainingAmount.min(principalOutstanding);
            lastPayment = recordCashfreePayment(bill, payForBill, request, owner);
            remainingAmount = remainingAmount.subtract(payForBill);
        }

        // PASS 2: Pay ALL interest across all bills (oldest first)
        for (MaintenanceBill bill : outstandingBills) {
            if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal interestOutstanding = getInterestOutstanding(bill);
            if (interestOutstanding.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal payForBill = remainingAmount.min(interestOutstanding);
            lastPayment = recordCashfreePayment(bill, payForBill, request, owner);
            remainingAmount = remainingAmount.subtract(payForBill);
        }

        if (lastPayment == null) {
            throw new BusinessException("No outstanding bills found to apply payment.");
        }

        log.info("Cashfree payment verified and recorded - orderId: {}, amount: ₹{}, unit: {}",
                request.getCashfreeOrderId(), request.getAmount(), request.getUnitId());

        return lastPayment;
    }

    private MaintenancePayment recordCashfreePayment(MaintenanceBill bill, BigDecimal payAmount,
                                                      VerifyPaymentRequest request, Owner owner) {
        String receiptNumber = "RCP-" + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + (new Random().nextInt(9000) + 1000);

        MaintenancePayment payment = MaintenancePayment.builder()
                .bill(bill)
                .unit(bill.getUnit())
                .amount(payAmount)
                .paymentDate(LocalDate.now())
                .paymentMode(MaintenancePayment.PaymentMode.CASHFREE_LINK)
                .cashfreeOrderId(request.getCashfreeOrderId())
                .payerName(owner.getFullName())
                .payerType("OWNER")
                .receiptNumber(receiptNumber)
                .status(MaintenancePayment.PaymentStatus.SUCCESS)
                .remarks(request.getDiscountAmount() != null && request.getDiscountAmount().compareTo(java.math.BigDecimal.ZERO) > 0
                        ? "Online payment via Cashfree (discount applied)" : "Online payment via Cashfree")
                .originalAmount(request.getDiscountAmount() != null && request.getDiscountAmount().compareTo(java.math.BigDecimal.ZERO) > 0
                        ? payAmount.add(request.getDiscountAmount()) : null)
                .discountPercent(request.getDiscountAmount() != null && request.getDiscountAmount().compareTo(java.math.BigDecimal.ZERO) > 0
                        ? request.getDiscountAmount().multiply(new java.math.BigDecimal("100")).divide(payAmount.add(request.getDiscountAmount()), 2, java.math.RoundingMode.HALF_UP) : null)
                .discountAmount(request.getDiscountAmount() != null && request.getDiscountAmount().compareTo(java.math.BigDecimal.ZERO) > 0
                        ? request.getDiscountAmount() : null)
                .build();
        paymentRepository.save(payment);

        BigDecimal paidSoFar = bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal newPaidAmount = paidSoFar.add(payAmount);
        bill.setPaidAmount(newPaidAmount);
        bill.setBalanceAmount(bill.getTotalAmount().subtract(newPaidAmount).max(BigDecimal.ZERO));

        if (bill.getBalanceAmount().compareTo(BigDecimal.ZERO) <= 0) {
            bill.setStatus(MaintenanceBill.BillStatus.PAID);
            bill.setBalanceAmount(BigDecimal.ZERO);
        } else {
            bill.setStatus(MaintenanceBill.BillStatus.PARTIALLY_PAID);
        }
        billRepository.save(bill);

        return payment;
    }

    private BigDecimal getPrincipalOutstanding(MaintenanceBill bill) {
        BigDecimal currentCharges = bill.getAmount() != null ? bill.getAmount() : BigDecimal.ZERO;
        BigDecimal arrears = bill.getPreviousArrears() != null ? bill.getPreviousArrears() : BigDecimal.ZERO;
        BigDecimal totalPrincipal = currentCharges.add(arrears);
        BigDecimal paidSoFar = bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO;
        return totalPrincipal.subtract(paidSoFar).max(BigDecimal.ZERO);
    }

    private BigDecimal getInterestOutstanding(MaintenanceBill bill) {
        BigDecimal interest = bill.getInterestOnArrears() != null ? bill.getInterestOnArrears() : BigDecimal.ZERO;
        if (interest.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        BigDecimal currentCharges = bill.getAmount() != null ? bill.getAmount() : BigDecimal.ZERO;
        BigDecimal arrears = bill.getPreviousArrears() != null ? bill.getPreviousArrears() : BigDecimal.ZERO;
        BigDecimal totalPrincipal = currentCharges.add(arrears);
        BigDecimal paidSoFar = bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal paidTowardsInterest = paidSoFar.subtract(totalPrincipal).max(BigDecimal.ZERO);
        return interest.subtract(paidTowardsInterest).max(BigDecimal.ZERO);
    }
}
