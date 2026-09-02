package com.society.module.member.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
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
import com.society.common.OptimisticRetry;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@Slf4j
public class RazorpayService {

    private final MaintenanceBillRepository billRepository;
    private final MaintenancePaymentRepository paymentRepository;
    private final OwnerRepository ownerRepository;
    private final UnitOwnerRepository unitOwnerRepository;

    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private final RazorpayClient razorpayClient;

    @Autowired
    private org.springframework.core.env.Environment environment;

    @Autowired
    private OptimisticRetry optimisticRetry;

    @Autowired
    private com.society.module.maintenance.service.ReceiptNumberService receiptNumberService;

    @Autowired
    private com.society.module.maintenance.service.MaintenanceLedgerService ledgerService;

    @Autowired
    private com.society.module.maintenance.service.SuspenseService suspenseService;

    /** Self-proxy so retry re-invokes the @Transactional core in a fresh transaction. */
    @Autowired
    @Lazy
    private RazorpayService self;

    public RazorpayService(
            MaintenanceBillRepository billRepository,
            MaintenancePaymentRepository paymentRepository,
            OwnerRepository ownerRepository,
            UnitOwnerRepository unitOwnerRepository,
            @Value("${app.razorpay.key-id:}") String keyId,
            @Value("${app.razorpay.key-secret:}") String keySecret,
            @Value("${app.razorpay.webhook-secret:}") String webhookSecret) {
        this.billRepository = billRepository;
        this.paymentRepository = paymentRepository;
        this.ownerRepository = ownerRepository;
        this.unitOwnerRepository = unitOwnerRepository;
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;

        RazorpayClient client = null;
        if (keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank()) {
            try {
                client = new RazorpayClient(keyId, keySecret);
                log.info("Razorpay client initialized successfully");
            } catch (RazorpayException e) {
                log.error("Failed to initialize Razorpay client: {}", e.getMessage());
            }
        } else {
            log.warn("Razorpay credentials not configured. Payment gateway will not work.");
        }
        this.razorpayClient = client;
    }

    /**
     * Create a Razorpay order for the given payment request.
     * Amount can be total outstanding or partial amount.
     */
    @Transactional
    public PaymentOrderResponse createOrder(Long ownerId, CreatePaymentOrderRequest request) {
        if (razorpayClient == null) {
            throw new BusinessException("Payment gateway is not configured. Contact society admin.");
        }

        // Validate owner has access to this unit
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

        // Validate amount against outstanding
        BigDecimal totalOutstanding = billRepository.getTotalOutstandingByUnit(request.getUnitId());
        if (totalOutstanding == null) totalOutstanding = BigDecimal.ZERO;

        if (request.getAmount().compareTo(totalOutstanding) > 0) {
            throw new BusinessException("Payment amount (₹" + request.getAmount()
                    + ") exceeds total outstanding (₹" + totalOutstanding + ")");
        }

        try {
            // Razorpay expects amount in paise (INR smallest unit)
            int amountInPaise = request.getAmount()
                    .multiply(new BigDecimal("100"))
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValueExact();

            String receipt = "RCP-" + unit.getUnitNumber() + "-" + System.currentTimeMillis();

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", receipt);
            orderRequest.put("payment_capture", 1); // Auto-capture

            JSONObject notes = new JSONObject();
            notes.put("unit_id", request.getUnitId());
            notes.put("unit_number", unit.getUnitNumber());
            notes.put("owner_id", ownerId);
            notes.put("owner_name", owner.getFullName());
            if (request.getBillId() != null) {
                notes.put("bill_id", request.getBillId());
            }
            orderRequest.put("notes", notes);

            Order order = razorpayClient.orders.create(orderRequest);
            String razorpayOrderId = order.get("id");

            log.info("Razorpay order created - orderId: {}, amount: ₹{}, unit: {}",
                    razorpayOrderId, request.getAmount(), unit.getUnitNumber());

            // If paying for a specific bill, store the razorpay order ID
            if (request.getBillId() != null) {
                MaintenanceBill bill = billRepository.findById(request.getBillId())
                        .orElseThrow(() -> new BusinessException("Bill not found"));
                bill.setRazorpayOrderId(razorpayOrderId);
                billRepository.save(bill);
            }

            return PaymentOrderResponse.builder()
                    .gateway("RAZORPAY")
                    .razorpayOrderId(razorpayOrderId)
                    .amount(request.getAmount())
                    .currency("INR")
                    .razorpayKeyId(keyId)
                    .receipt(receipt)
                    .ownerName(owner.getFullName())
                    .email(owner.getEmail())
                    .phone(owner.getContactNumber())
                    .unitNumber(unit.getUnitNumber())
                    .description("Maintenance Payment - " + unit.getUnitNumber())
                    .build();

        } catch (RazorpayException e) {
            log.error("Failed to create Razorpay order: {}", e.getMessage(), e);
            throw new BusinessException("Failed to create payment order. Please try again.");
        }
    }

    /**
     * Verify Razorpay payment signature and record the payment.
     */
    /**
     * Public entry: verifies the Razorpay signature (local, not retried) then applies the
     * allocation in a retried transaction so concurrent updates to the same bills can't
     * cause a lost update.
     */
    public MaintenancePayment verifyAndRecordPayment(Long ownerId, VerifyPaymentRequest request) {
        // Validate owner-unit access
        boolean owns = unitOwnerRepository.existsByUnit_UnitIdAndOwner_OwnerId(request.getUnitId(), ownerId);
        if (!owns) {
            throw new BusinessException("You don't have access to this unit");
        }

        // Verify Razorpay signature
        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", request.getRazorpayOrderId());
            attributes.put("razorpay_payment_id", request.getRazorpayPaymentId());
            attributes.put("razorpay_signature", request.getRazorpaySignature());

            boolean isValid = Utils.verifyPaymentSignature(attributes, keySecret);
            if (!isValid) {
                log.warn("Payment signature verification failed for order: {}", request.getRazorpayOrderId());
                throw new BusinessException("Payment verification failed. Invalid signature.");
            }
        } catch (RazorpayException e) {
            log.error("Razorpay signature verification error: {}", e.getMessage(), e);
            throw new BusinessException("Payment verification failed. Please contact support.");
        }

        return optimisticRetry.execute("record razorpay payment",
                () -> self.applyRazorpayPayment(ownerId, request));
    }

    @Transactional
    public MaintenancePayment applyRazorpayPayment(Long ownerId, VerifyPaymentRequest request) {
        // Check for duplicate payment (inside the transaction to close the race with commit)
        if (paymentRepository.findByRazorpayPaymentId(request.getRazorpayPaymentId()).isPresent()) {
            throw new BusinessException("This payment has already been recorded.");
        }

        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new BusinessException("Owner not found"));

        // Get all outstanding bills ordered oldest first
        List<MaintenanceBill> outstandingBills = billRepository.findOutstandingByUnit(request.getUnitId());
        if (outstandingBills.isEmpty()) {
            throw new BusinessException("No outstanding bills found to apply payment.");
        }

        BigDecimal remainingAmount = request.getAmount();
        MaintenancePayment lastPayment = null;

        // ========== PASS 1: Pay ALL principal across all bills (oldest first) ==========
        // Principal = current month charges (amount) + previous arrears
        for (MaintenanceBill bill : outstandingBills) {
            if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal principalOutstanding = getPrincipalOutstanding(bill);
            if (principalOutstanding.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal payForBill = remainingAmount.min(principalOutstanding);
            lastPayment = recordPaymentForBill(bill, payForBill, request, owner,
                    com.society.module.maintenance.entity.MaintenanceLedger.Source.RAZORPAY_MEMBER);
            remainingAmount = remainingAmount.subtract(payForBill);
        }

        // ========== PASS 2: Pay ALL interest across all bills (oldest first) ==========
        // Interest is settled only after all principal is cleared
        for (MaintenanceBill bill : outstandingBills) {
            if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal interestOutstanding = getInterestOutstanding(bill);
            if (interestOutstanding.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal payForBill = remainingAmount.min(interestOutstanding);
            lastPayment = recordPaymentForBill(bill, payForBill, request, owner,
                    com.society.module.maintenance.entity.MaintenanceLedger.Source.RAZORPAY_MEMBER);
            remainingAmount = remainingAmount.subtract(payForBill);
        }

        if (lastPayment == null) {
            throw new BusinessException("No outstanding bills found to apply payment.");
        }

        // Overpayment: any amount left after clearing all outstanding bills is parked in an
        // UNASSIGNED suspense entry rather than silently dropped, so it can be reconciled later.
        if (remainingAmount.compareTo(BigDecimal.ZERO) > 0) {
            suspenseService.createSuspenseEntry(
                    remainingAmount, LocalDate.now(),
                    com.society.enums.PaymentMode.ONLINE.name(), request.getRazorpayPaymentId(),
                    "Overpayment from Razorpay payment " + request.getRazorpayPaymentId()
                            + " (unit " + request.getUnitId() + ")",
                    owner.getFullName());
            log.info("Overpayment of ₹{} parked in suspense for razorpayPaymentId {}",
                    remainingAmount, request.getRazorpayPaymentId());
        }

        log.info("Payment verified and recorded - paymentId: {}, razorpayPaymentId: {}, amount: ₹{}, unit: {}",
                lastPayment.getPaymentId(), request.getRazorpayPaymentId(),
                request.getAmount(), request.getUnitId());

        return lastPayment;
    }

    /**
     * Get the outstanding principal for a bill.
     * Principal = current month charges (amount) + previous arrears, minus what's already been paid towards principal.
     */
    private BigDecimal getPrincipalOutstanding(MaintenanceBill bill) {
        BigDecimal currentCharges = bill.getAmount() != null ? bill.getAmount() : BigDecimal.ZERO;
        BigDecimal arrears = bill.getPreviousArrears() != null ? bill.getPreviousArrears() : BigDecimal.ZERO;
        BigDecimal lateFee = bill.getLateFee() != null ? bill.getLateFee() : BigDecimal.ZERO;
        // Late fee is a fixed charge, settled with principal (before interest).
        BigDecimal totalPrincipal = currentCharges.add(arrears).add(lateFee);

        BigDecimal paidSoFar = bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO;
        // Paid amount goes to principal first, so outstanding principal = max(0, totalPrincipal - paidSoFar)
        return totalPrincipal.subtract(paidSoFar).max(BigDecimal.ZERO);
    }

    /**
     * Get the outstanding interest for a bill.
     * Interest is only considered outstanding once all principal is paid off.
     */
    private BigDecimal getInterestOutstanding(MaintenanceBill bill) {
        BigDecimal interest = bill.getInterestOnArrears() != null ? bill.getInterestOnArrears() : BigDecimal.ZERO;
        if (interest.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        BigDecimal currentCharges = bill.getAmount() != null ? bill.getAmount() : BigDecimal.ZERO;
        BigDecimal arrears = bill.getPreviousArrears() != null ? bill.getPreviousArrears() : BigDecimal.ZERO;
        BigDecimal lateFee = bill.getLateFee() != null ? bill.getLateFee() : BigDecimal.ZERO;
        BigDecimal totalPrincipal = currentCharges.add(arrears).add(lateFee);

        BigDecimal paidSoFar = bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO;
        // Amount paid beyond principal goes towards interest
        BigDecimal paidTowardsInterest = paidSoFar.subtract(totalPrincipal).max(BigDecimal.ZERO);
        return interest.subtract(paidTowardsInterest).max(BigDecimal.ZERO);
    }

    /**
     * Record payment for a specific bill and update bill amounts.
     */
    private MaintenancePayment recordPaymentForBill(
            MaintenanceBill bill, BigDecimal payAmount,
            VerifyPaymentRequest request, Owner owner,
            com.society.module.maintenance.entity.MaintenanceLedger.Source source) {

        String receiptNumber = receiptNumberService.next();
        BigDecimal balanceBefore = bill.getBalanceAmount() != null ? bill.getBalanceAmount() : BigDecimal.ZERO;

        MaintenancePayment payment = MaintenancePayment.builder()
                .bill(bill)
                .unit(bill.getUnit())
                .amount(payAmount)
                .paymentDate(LocalDate.now())
                .paymentMode(MaintenancePayment.PaymentMode.RAZORPAY)
                .razorpayPaymentId(request.getRazorpayPaymentId())
                .razorpayOrderId(request.getRazorpayOrderId())
                .razorpaySignature(request.getRazorpaySignature())
                .payerName(owner.getFullName())
                .payerType("OWNER")
                .receiptNumber(receiptNumber)
                .status(MaintenancePayment.PaymentStatus.SUCCESS)
                .remarks(request.getDiscountAmount() != null && request.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0
                        ? "Online payment via Razorpay (discount applied)" : "Online payment via Razorpay")
                .originalAmount(request.getDiscountAmount() != null && request.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0
                        ? payAmount.add(request.getDiscountAmount()) : null)
                .discountPercent(request.getDiscountAmount() != null && request.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0
                        ? request.getDiscountAmount().multiply(new BigDecimal("100")).divide(payAmount.add(request.getDiscountAmount()), 2, java.math.RoundingMode.HALF_UP) : null)
                .discountAmount(request.getDiscountAmount() != null && request.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0
                        ? request.getDiscountAmount() : null)
                .build();
        paymentRepository.save(payment);

        // Update bill totals
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

        ledgerService.record(bill, payment.getPaymentId(),
                com.society.module.maintenance.entity.MaintenanceLedger.EntryType.PAYMENT_APPLIED, payAmount,
                balanceBefore, bill.getBalanceAmount(),
                source, request.getRazorpayPaymentId(), "Razorpay payment");

        return payment;
    }

    /**
     * Handle a Razorpay webhook for payment events.
     *
     * @param webhookBody      the raw request body (needed for signature verification)
     * @param webhookSignature the X-Razorpay-Signature header
     * @return true if authenticated and processed (or safely ignored); false if the
     *         signature is invalid so the caller can respond 401 (never a silent 200).
     */
    public boolean handleWebhook(String webhookBody, String webhookSignature) {
        // ---- 1. Authenticate BEFORE trusting the payload ----
        if (!isWebhookSignatureValid(webhookBody, webhookSignature)) {
            log.warn("Rejected Razorpay webhook: invalid or missing signature");
            return false;
        }

        try {
            JSONObject webhookJson = new JSONObject(webhookBody);
            String event = webhookJson.optString("event");

            if (!"payment.captured".equals(event) && !"payment.authorized".equals(event)) {
                return true; // authenticated; not an event we credit on
            }

            JSONObject payload = webhookJson.getJSONObject("payload");
            JSONObject paymentEntity = payload.getJSONObject("payment").getJSONObject("entity");

            String razorpayPaymentId = paymentEntity.optString("id");
            String orderId = paymentEntity.optString("order_id");
            int amountInPaise = paymentEntity.optInt("amount");
            BigDecimal amount = new BigDecimal(amountInPaise).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

            log.info("Webhook - Payment captured: paymentId={}, orderId={}, amount=₹{}",
                    razorpayPaymentId, orderId, amount);

            // ---- 2. Idempotency: never credit the same payment twice ----
            if (paymentRepository.findByRazorpayPaymentId(razorpayPaymentId).isPresent()) {
                log.info("Payment already recorded for razorpayPaymentId: {}", razorpayPaymentId);
                return true;
            }

            // ---- 3. Apply the credit in a retried transaction (optimistic-lock safe) ----
            optimisticRetry.executeVoid("apply razorpay webhook payment",
                    () -> self.applyWebhookPayment(orderId, razorpayPaymentId, amount));
            return true;

        } catch (Exception e) {
            // Re-throw so the controller does NOT return 200; Razorpay then retries
            // rather than us silently losing the payment.
            log.error("Error processing Razorpay webhook: {}", e.getMessage(), e);
            throw new BusinessException("Failed to process Razorpay webhook");
        }
    }

    /**
     * Transactional core of the Razorpay webhook credit. Re-reads the bill and re-checks
     * dedup inside the transaction so {@link OptimisticRetry} can safely re-run it.
     */
    @Transactional
    public void applyWebhookPayment(String orderId, String razorpayPaymentId, BigDecimal amount) {
        if (paymentRepository.findByRazorpayPaymentId(razorpayPaymentId).isPresent()) {
            log.info("Duplicate Razorpay webhook ignored (in-tx) - paymentId {} already recorded", razorpayPaymentId);
            return;
        }

        billRepository.findByRazorpayOrderId(orderId).ifPresentOrElse(bill -> {
            Owner owner = null;
            List<UnitOwner> unitOwners = unitOwnerRepository.findByUnit_UnitId(bill.getUnit().getUnitId());
            if (!unitOwners.isEmpty()) {
                owner = unitOwners.get(0).getOwner();
            }

            VerifyPaymentRequest vpRequest = new VerifyPaymentRequest();
            vpRequest.setRazorpayOrderId(orderId);
            vpRequest.setRazorpayPaymentId(razorpayPaymentId);
            vpRequest.setRazorpaySignature("");
            vpRequest.setUnitId(bill.getUnit().getUnitId());
            vpRequest.setAmount(amount);
            vpRequest.setBillId(bill.getBillId());

            recordPaymentForBill(bill, amount, vpRequest,
                    owner != null ? owner : Owner.builder().fullName("Online Payment").build(),
                    com.society.module.maintenance.entity.MaintenanceLedger.Source.RAZORPAY_WEBHOOK);

            log.info("Webhook payment recorded for bill: {}", bill.getBillId());
        }, () -> log.warn("No bill found for razorpay orderId: {}", orderId));
    }

    /**
     * Verify the Razorpay webhook signature: HMAC-SHA256 of the raw body using the webhook
     * secret, hex-encoded, compared to the X-Razorpay-Signature header.
     *
     * Non-prod: if no webhook secret is configured, verification is skipped so sandbox
     * testing works. In prod a missing secret or signature always fails closed.
     */
    private boolean isWebhookSignatureValid(String rawBody, String signature) {
        boolean secretConfigured = webhookSecret != null && !webhookSecret.isBlank();

        if (!secretConfigured) {
            if (isProdProfile()) {
                log.error("Razorpay webhook secret not configured in prod - refusing to trust webhook");
                return false;
            }
            log.warn("Razorpay webhook secret not configured (non-prod) - skipping signature check");
            return true;
        }

        if (signature == null || signature.isBlank()) {
            log.warn("Razorpay webhook missing signature header");
            return false;
        }

        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    webhookSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(rawBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit((b & 0xF), 16));
            }
            String computed = hex.toString();
            boolean matches = java.security.MessageDigest.isEqual(
                    computed.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    signature.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (!matches) {
                log.warn("Razorpay webhook signature mismatch");
            }
            return matches;
        } catch (Exception e) {
            log.error("Error verifying Razorpay webhook signature: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean isProdProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
