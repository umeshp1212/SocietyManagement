package com.society.module.member.service;

import com.society.exception.BusinessException;
import com.society.module.maintenance.entity.MaintenanceBill;
import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.maintenance.repository.MaintenanceBillRepository;
import com.society.module.maintenance.service.CashfreeService;
import com.society.module.member.dto.CreatePaymentOrderRequest;
import com.society.module.member.dto.PaymentOrderResponse;
import com.society.module.member.dto.VerifyPaymentRequest;
import com.society.module.settings.entity.SocietySettings;
import com.society.module.settings.service.SocietySettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentGatewayRouter {

    private final RazorpayService razorpayService;
    private final CashfreeService cashfreeService;
    private final SocietySettingsService settingsService;
    private final MaintenanceBillRepository billRepository;

    /**
     * Get the active payment gateway from society settings.
     */
    public String getActiveGateway() {
        SocietySettings settings = settingsService.getSettings();
        String gateway = settings.getPaymentGateway();
        return (gateway != null && !gateway.isBlank()) ? gateway.toUpperCase() : "RAZORPAY";
    }

    /**
     * Get discount settings for the member dashboard.
     */
    public SocietySettings getSettings() {
        return settingsService.getSettings();
    }

    /**
     * Create a payment order using the active gateway.
     * Applies online payment discount if eligible.
     */
    public PaymentOrderResponse createOrder(Long ownerId, CreatePaymentOrderRequest request) {
        String gateway = getActiveGateway();
        SocietySettings settings = settingsService.getSettings();

        // Calculate discount
        BigDecimal originalAmount = request.getAmount();
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal discountPercent = BigDecimal.ZERO;
        boolean discountApplied = false;

        if (Boolean.TRUE.equals(settings.getDiscountEnabled())
                && settings.getDiscountPercent() != null
                && settings.getDiscountPercent().compareTo(BigDecimal.ZERO) > 0) {

            // Check if payment is within discount due days
            boolean eligible = isEligibleForDiscount(request.getUnitId(), settings.getDiscountDueDays());

            if (eligible) {
                discountPercent = settings.getDiscountPercent();
                discountAmount = originalAmount
                        .multiply(discountPercent)
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                discountApplied = true;

                // Update request amount with discounted value
                request.setAmount(originalAmount.subtract(discountAmount));

                log.info("Discount applied: {}% = ₹{} off. Original: ₹{}, Discounted: ₹{}",
                        discountPercent, discountAmount, originalAmount, request.getAmount());
            }
        }

        log.info("Creating payment order via {} for owner: {}, amount: ₹{}",
                gateway, ownerId, request.getAmount());

        PaymentOrderResponse response = switch (gateway) {
            case "CASHFREE" -> cashfreeService.createMemberOrder(ownerId, request);
            case "RAZORPAY" -> razorpayService.createOrder(ownerId, request);
            default -> throw new BusinessException("Unknown payment gateway: " + gateway);
        };

        // Add discount info to response
        response.setOriginalAmount(originalAmount);
        response.setDiscountAmount(discountAmount);
        response.setDiscountPercent(discountPercent);
        response.setDiscountApplied(discountApplied);

        return response;
    }

    /**
     * Verify payment and record it using the appropriate gateway.
     * If a discount was applied, adjust bill totals so the discount doesn't become arrears.
     */
    public MaintenancePayment verifyAndRecordPayment(Long ownerId, VerifyPaymentRequest request) {
        String gateway = request.getGateway();
        if (gateway == null || gateway.isBlank()) {
            gateway = getActiveGateway();
        }
        log.info("Verifying payment via {} for owner: {}", gateway, ownerId);

        MaintenancePayment payment = switch (gateway.toUpperCase()) {
            case "CASHFREE" -> cashfreeService.verifyAndRecordMemberPayment(ownerId, request);
            case "RAZORPAY" -> razorpayService.verifyAndRecordPayment(ownerId, request);
            default -> throw new BusinessException("Unknown payment gateway: " + gateway);
        };

        // If discount was applied, adjust bill totals so the discount gap doesn't carry as arrears
        if (request.getDiscountAmount() != null && request.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            adjustBillsForDiscount(request.getUnitId(), request.getDiscountAmount());
        }

        return payment;
    }

    /**
     * After a discounted payment, reduce bill totalAmount and balanceAmount
     * so the discount doesn't appear as unpaid balance / arrears.
     * Applied to bills that still have a small remaining balance (the discount gap).
     */
    private void adjustBillsForDiscount(Long unitId, BigDecimal discountAmount) {
        List<MaintenanceBill> bills = billRepository.findOutstandingByUnit(unitId);
        BigDecimal remaining = discountAmount;

        for (MaintenanceBill bill : bills) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal balance = bill.getBalanceAmount() != null ? bill.getBalanceAmount() : BigDecimal.ZERO;
            if (balance.compareTo(BigDecimal.ZERO) <= 0) continue;

            // Only adjust if balance is small (likely the discount gap)
            BigDecimal adjust = remaining.min(balance);
            bill.setTotalAmount(bill.getTotalAmount().subtract(adjust));
            bill.setBalanceAmount(balance.subtract(adjust));

            if (bill.getBalanceAmount().compareTo(BigDecimal.ZERO) <= 0) {
                bill.setBalanceAmount(BigDecimal.ZERO);
                bill.setStatus(MaintenanceBill.BillStatus.PAID);
            }

            billRepository.save(bill);
            remaining = remaining.subtract(adjust);

            log.info("Discount adjustment: bill {} reduced by ₹{}, new balance: ₹{}",
                    bill.getBillId(), adjust, bill.getBalanceAmount());
        }
    }

    /**
     * Check if the unit has any current month bill within the discount due days.
     */
    private boolean isEligibleForDiscount(Long unitId, Integer discountDueDays) {
        if (discountDueDays == null || discountDueDays <= 0) return false;

        LocalDate today = LocalDate.now();
        List<MaintenanceBill> bills = billRepository.findOutstandingByUnit(unitId);

        for (MaintenanceBill bill : bills) {
            // Check if today is within discountDueDays from the bill date
            LocalDate billDate = bill.getBillDate();
            if (billDate != null && !today.isAfter(billDate.plusDays(discountDueDays))) {
                return true;
            }
        }
        return false;
    }
}
