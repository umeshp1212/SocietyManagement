package com.society.module.maintenance.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.society.module.maintenance.entity.MaintenanceBill;
import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.maintenance.repository.MaintenanceBillRepository;
import com.society.module.maintenance.repository.MaintenancePaymentRepository;
import com.society.module.settings.service.SocietySettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
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

    @Value("${app.cashfree.app-id:}")
    private String cashfreeAppId;

    @Value("${app.cashfree.secret-key:}")
    private String cashfreeSecretKey;

    @Value("${app.cashfree.api-version:2023-08-01}")
    private String apiVersion;

    @Value("${app.cashfree.environment:sandbox}")
    private String environment;

    @Value("${app.cashfree.return-url:http://localhost:4200/maintenance/payment-status}")
    private String returnUrl;

    @Value("${app.cashfree.notify-url:http://localhost:8080/api/maintenance/payments/webhook}")
    private String notifyUrl;

    @Value("${app.base-url:http://localhost:8080/api}")
    private String baseUrl;

    private String getBaseApiUrl() {
        if ("sandbox".equalsIgnoreCase(environment)) {
            return "https://sandbox.cashfree.com/pg";
        }
        return "https://api.cashfree.com/pg";
    }

    public Map<String, Object> createPaymentLink(MaintenanceBill bill) {
        if (cashfreeAppId == null || cashfreeAppId.isBlank()) {
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
                return createMockPaymentLink(bill);
            }

        } catch (Exception e) {
            log.error("Error creating Cashfree payment link for bill: {}. Error: {}", bill.getBillId(), e.getMessage(), e);
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

    @Transactional
    @SuppressWarnings("unchecked")
    public void handlePaymentWebhook(Map<String, Object> webhookData) {
        try {
            Map<String, Object> data = (Map<String, Object>) webhookData.get("data");
            if (data == null) {
                log.warn("Webhook data is null or missing 'data' field");
                return;
            }

            Map<String, Object> orderData = (Map<String, Object>) data.get("order");
            Map<String, Object> paymentData = (Map<String, Object>) data.get("payment");

            if (orderData == null || paymentData == null) {
                log.warn("Webhook missing order or payment data");
                return;
            }

            String orderId = (String) orderData.get("order_id");
            String paymentStatus = (String) paymentData.get("payment_status");
            Object cfPaymentIdObj = paymentData.get("cf_payment_id");
            String paymentId = cfPaymentIdObj != null ? cfPaymentIdObj.toString() : null;
            Object paymentAmountObj = paymentData.get("payment_amount");
            BigDecimal paymentAmount = paymentAmountObj != null
                    ? new BigDecimal(paymentAmountObj.toString()) : BigDecimal.ZERO;

            log.info("Processing webhook - orderId: {}, status: {}, paymentId: {}", orderId, paymentStatus, paymentId);

            Optional<MaintenanceBill> billOptional = billRepository.findByCashfreeOrderId(orderId);
            if (billOptional.isEmpty()) {
                log.warn("No bill found for cashfree orderId: {}", orderId);
                return;
            }

            MaintenanceBill bill = billOptional.get();

            if ("SUCCESS".equalsIgnoreCase(paymentStatus)) {
                MaintenancePayment payment = new MaintenancePayment();
                payment.setBill(bill);
                payment.setUnit(bill.getUnit());
                payment.setPaymentMode(MaintenancePayment.PaymentMode.CASHFREE_LINK);
                payment.setStatus(MaintenancePayment.PaymentStatus.SUCCESS);
                payment.setCashfreePaymentId(paymentId);
                payment.setCashfreeOrderId(orderId);
                payment.setAmount(paymentAmount);
                payment.setPaymentDate(LocalDate.now());
                payment.setReceiptNumber("RCP-" + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + new Random().nextInt(9000) + 1000);
                paymentRepository.save(payment);

                BigDecimal paidSoFar = bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO;
                BigDecimal newPaidAmount = paidSoFar.add(paymentAmount);
                bill.setPaidAmount(newPaidAmount);
                bill.setBalanceAmount(bill.getTotalAmount().subtract(newPaidAmount));

                if (bill.getBalanceAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    bill.setStatus(MaintenanceBill.BillStatus.PAID);
                } else {
                    bill.setStatus(MaintenanceBill.BillStatus.PARTIALLY_PAID);
                }

                billRepository.save(bill);
                log.info("Payment successful for bill: {}, amount: {}", bill.getBillId(), paymentAmount);

            } else if ("FAILED".equalsIgnoreCase(paymentStatus)) {
                log.warn("Payment failed for orderId: {}, bill: {}", orderId, bill.getBillId());
            }

        } catch (Exception e) {
            log.error("Error processing payment webhook: {}", e.getMessage(), e);
        }
    }

    public Map<String, Object> getPaymentStatus(String orderId) {
        if (cashfreeAppId == null || cashfreeAppId.isBlank()) {
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
}
