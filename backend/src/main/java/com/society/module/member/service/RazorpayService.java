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
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

@Service
@Slf4j
public class RazorpayService {

    private final MaintenanceBillRepository billRepository;
    private final MaintenancePaymentRepository paymentRepository;
    private final OwnerRepository ownerRepository;
    private final UnitOwnerRepository unitOwnerRepository;

    private final String keyId;
    private final String keySecret;
    private final RazorpayClient razorpayClient;

    public RazorpayService(
            MaintenanceBillRepository billRepository,
            MaintenancePaymentRepository paymentRepository,
            OwnerRepository ownerRepository,
            UnitOwnerRepository unitOwnerRepository,
            @Value("${app.razorpay.key-id:}") String keyId,
            @Value("${app.razorpay.key-secret:}") String keySecret) {
        this.billRepository = billRepository;
        this.paymentRepository = paymentRepository;
        this.ownerRepository = ownerRepository;
        this.unitOwnerRepository = unitOwnerRepository;
        this.keyId = keyId;
        this.keySecret = keySecret;

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
    @Transactional
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

        // Check for duplicate payment
        if (paymentRepository.findByRazorpayPaymentId(request.getRazorpayPaymentId()).isPresent()) {
            throw new BusinessException("This payment has already been recorded.");
        }

        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new BusinessException("Owner not found"));

        // Apply payment to outstanding bills (oldest first)
        BigDecimal remainingAmount = request.getAmount();
        MaintenancePayment lastPayment = null;

        // If specific bill ID provided, pay that bill first
        if (request.getBillId() != null) {
            MaintenanceBill specificBill = billRepository.findById(request.getBillId())
                    .orElseThrow(() -> new BusinessException("Bill not found"));

            if (specificBill.getUnit().getUnitId().equals(request.getUnitId())) {
                BigDecimal billBalance = specificBill.getBalanceAmount() != null
                        ? specificBill.getBalanceAmount() : specificBill.getTotalAmount();
                BigDecimal payForBill = remainingAmount.min(billBalance);

                lastPayment = recordPaymentForBill(specificBill, payForBill, request, owner);
                remainingAmount = remainingAmount.subtract(payForBill);
            }
        }

        // Apply remaining to outstanding bills (oldest first)
        if (remainingAmount.compareTo(BigDecimal.ZERO) > 0) {
            List<MaintenanceBill> outstandingBills = billRepository.findOutstandingByUnit(request.getUnitId());

            for (MaintenanceBill bill : outstandingBills) {
                if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) break;

                // Skip already-paid-in-this-transaction bill
                if (request.getBillId() != null && bill.getBillId().equals(request.getBillId())) {
                    continue;
                }

                BigDecimal billBalance = bill.getBalanceAmount() != null
                        ? bill.getBalanceAmount() : bill.getTotalAmount();

                if (billBalance.compareTo(BigDecimal.ZERO) <= 0) continue;

                BigDecimal payForBill = remainingAmount.min(billBalance);
                lastPayment = recordPaymentForBill(bill, payForBill, request, owner);
                remainingAmount = remainingAmount.subtract(payForBill);
            }
        }

        if (lastPayment == null) {
            throw new BusinessException("No outstanding bills found to apply payment.");
        }

        log.info("Payment verified and recorded - paymentId: {}, razorpayPaymentId: {}, amount: ₹{}, unit: {}",
                lastPayment.getPaymentId(), request.getRazorpayPaymentId(),
                request.getAmount(), request.getUnitId());

        return lastPayment;
    }

    /**
     * Record payment for a specific bill and update bill amounts.
     */
    private MaintenancePayment recordPaymentForBill(
            MaintenanceBill bill, BigDecimal payAmount,
            VerifyPaymentRequest request, Owner owner) {

        String receiptNumber = "RCP-"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + (new Random().nextInt(9000) + 1000);

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
                .remarks("Online payment via Razorpay")
                .build();
        paymentRepository.save(payment);

        // Update bill amounts
        BigDecimal paidSoFar = bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal newPaidAmount = paidSoFar.add(payAmount);
        bill.setPaidAmount(newPaidAmount);
        bill.setBalanceAmount(bill.getTotalAmount().subtract(newPaidAmount));

        if (bill.getBalanceAmount().compareTo(BigDecimal.ZERO) <= 0) {
            bill.setStatus(MaintenanceBill.BillStatus.PAID);
            bill.setBalanceAmount(BigDecimal.ZERO);
        } else {
            bill.setStatus(MaintenanceBill.BillStatus.PARTIALLY_PAID);
        }
        billRepository.save(bill);

        return payment;
    }

    /**
     * Handle Razorpay webhook for payment events.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public void handleWebhook(String webhookBody, String webhookSignature) {
        // Razorpay webhook signature verification
        // In production, verify using webhook secret
        log.info("Received Razorpay webhook");

        try {
            JSONObject webhookJson = new JSONObject(webhookBody);
            String event = webhookJson.optString("event");

            if ("payment.captured".equals(event) || "payment.authorized".equals(event)) {
                JSONObject payload = webhookJson.getJSONObject("payload");
                JSONObject paymentEntity = payload.getJSONObject("payment").getJSONObject("entity");

                String razorpayPaymentId = paymentEntity.optString("id");
                String orderId = paymentEntity.optString("order_id");
                int amountInPaise = paymentEntity.optInt("amount");
                BigDecimal amount = new BigDecimal(amountInPaise).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

                log.info("Webhook - Payment captured: paymentId={}, orderId={}, amount=₹{}",
                        razorpayPaymentId, orderId, amount);

                // Check if payment already recorded (via verify-payment endpoint)
                if (paymentRepository.findByRazorpayPaymentId(razorpayPaymentId).isPresent()) {
                    log.info("Payment already recorded for razorpayPaymentId: {}", razorpayPaymentId);
                    return;
                }

                // Find bill by razorpay order ID
                billRepository.findByRazorpayOrderId(orderId).ifPresent(bill -> {
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
                            owner != null ? owner : Owner.builder().fullName("Online Payment").build());

                    log.info("Webhook payment recorded for bill: {}", bill.getBillId());
                });
            }
        } catch (Exception e) {
            log.error("Error processing Razorpay webhook: {}", e.getMessage(), e);
        }
    }
}
