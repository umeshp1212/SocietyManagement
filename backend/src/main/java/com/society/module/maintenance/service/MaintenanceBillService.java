package com.society.module.maintenance.service;

import com.society.common.PagedResponse;
import com.society.enums.OccupancyStatus;
import com.society.exception.BusinessException;
import com.society.exception.ResourceNotFoundException;
import com.society.module.maintenance.dto.*;
import com.society.module.maintenance.entity.BillLineItem;
import com.society.module.maintenance.entity.MaintenanceBill;
import com.society.module.maintenance.entity.MaintenanceBill.BillStatus;
import com.society.module.maintenance.entity.MaintenanceChargeConfig;
import com.society.module.maintenance.entity.MaintenanceLedger;
import com.society.module.maintenance.entity.MaintenanceChargeConfig.ApplicableTo;
import com.society.module.maintenance.entity.MaintenanceChargeConfig.CalculationType;
import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentMode;
import com.society.module.maintenance.entity.UnitAdvanceCredit;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentStatus;
import com.society.module.maintenance.entity.Penalty;
import com.society.module.maintenance.entity.Penalty.PenaltyStatus;
import com.society.module.maintenance.repository.BillLineItemRepository;
import com.society.module.maintenance.repository.MaintenanceBillRepository;
import com.society.module.maintenance.repository.MaintenanceChargeConfigRepository;
import com.society.module.maintenance.repository.MaintenancePaymentRepository;
import com.society.module.maintenance.repository.OpeningBalanceRepository;
import com.society.module.maintenance.repository.PenaltyRepository;
import com.society.common.OptimisticRetry;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceBillService {

    private final MaintenanceBillRepository billRepository;
    private final MaintenancePaymentRepository paymentRepository;
    private final MaintenanceChargeConfigRepository chargeConfigRepository;
    private final BillLineItemRepository lineItemRepository;
    private final UnitRepository unitRepository;
    private final WaterChargeConfigService waterChargeConfigService;
    private final PenaltyRepository penaltyRepository;
    private final OpeningBalanceRepository openingBalanceRepository;
    private final OptimisticRetry optimisticRetry;
    private final ReceiptNumberService receiptNumberService;
    private final MaintenanceLedgerService ledgerService;
    private final com.society.module.maintenance.repository.MaintenanceLedgerRepository ledgerRepository;
    private final com.society.module.maintenance.repository.UnitAdvanceCreditRepository advanceCreditRepository;

    /**
     * Flat late fee applied once to a bill when the unit is carrying arrears. Default 0
     * (disabled). Configure via app.maintenance.late-fee to enable for a society.
     */
    @org.springframework.beans.factory.annotation.Value("${app.maintenance.late-fee:0}")
    private BigDecimal lateFeeAmount;

    /**
     * Self-reference (through the Spring proxy) so retry can re-invoke the @Transactional
     * core method in a FRESH transaction. A plain this.method() call would bypass the proxy
     * and reuse the same (already-failed) transaction. @Lazy breaks the self-injection cycle.
     */
    @Autowired
    @Lazy
    private MaintenanceBillService self;

    /**
     * Interest rate: 1% per month on unpaid arrears
     */
    private static final BigDecimal MONTHLY_INTEREST_RATE = new BigDecimal("0.01");

    /**
     * Single money rounding policy for the whole module: 2 decimal places (paise),
     * HALF_UP. Every computed money value passes through {@link #money(BigDecimal)} so line
     * items, interest, and totals reconcile exactly and there is no scale-0-vs-scale-2 drift.
     */
    private static final int MONEY_SCALE = 2;

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    // ======================== BILL GENERATION ========================

    @Transactional
    public Map<String, Object> generateMonthlyBills(GenerateBillsRequest request) {
        List<Unit> units = unitRepository.findAll();
        List<MaintenanceChargeConfig> activeCharges = chargeConfigRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        int billsGenerated = 0;
        int billsRegenerated = 0;
        boolean isRegenerate = Boolean.TRUE.equals(request.getRegenerate());

        if (activeCharges.isEmpty()) {
            throw new BusinessException("No active charge configurations found. Please configure maintenance charges first.");
        }

        for (Unit unit : units) {
            // Skip inactive units
            if (!"ACTIVE".equals(unit.getStatus())) {
                continue;
            }

            // Check if bill already exists for this unit/month/year
            Optional<MaintenanceBill> existingBillOpt = billRepository.findByUnit_UnitIdAndBillMonthAndBillYear(
                    unit.getUnitId(), request.getMonth(), request.getYear());

            if (existingBillOpt.isPresent()) {
                if (isRegenerate) {
                    MaintenanceBill existingBill = existingBillOpt.get();
                    // Only regenerate UNPAID bills - don't touch paid/partially paid
                    if (existingBill.getStatus() == BillStatus.UNPAID) {
                        // Revert any penalties that were consumed (marked BILLED) when this bill
                        // was first generated back to PENDING, so generateBillForUnit re-attaches
                        // them to the regenerated bill instead of silently dropping them.
                        List<Penalty> billedPenalties = penaltyRepository
                                .findByUnit_UnitIdAndBillMonthAndBillYearAndStatus(
                                        unit.getUnitId(), request.getMonth(), request.getYear(),
                                        PenaltyStatus.BILLED);
                        for (Penalty p : billedPenalties) {
                            p.setStatus(PenaltyStatus.PENDING);
                        }
                        if (!billedPenalties.isEmpty()) {
                            penaltyRepository.saveAll(billedPenalties);
                            penaltyRepository.flush();
                        }

                        billRepository.delete(existingBill);
                        billRepository.flush();
                        // Generate fresh bill
                        MaintenanceBill newBill = generateBillForUnit(unit, activeCharges, request);
                        if (newBill != null) {
                            billRepository.save(newBill);
                            ledgerService.record(newBill, null,
                                    MaintenanceLedger.EntryType.BILL_GENERATED, newBill.getTotalAmount(),
                                    BigDecimal.ZERO, newBill.getBalanceAmount(),
                                    MaintenanceLedger.Source.SYSTEM, null, "Bill regenerated");
                            billsRegenerated++;
                        }
                    }
                }
                // If not regenerate mode, skip existing bills
                continue;
            }

            // Generate bill with line items
            MaintenanceBill bill = generateBillForUnit(unit, activeCharges, request);
            if (bill != null) {
                billRepository.save(bill);
                ledgerService.record(bill, null,
                        MaintenanceLedger.EntryType.BILL_GENERATED, bill.getTotalAmount(),
                        BigDecimal.ZERO, bill.getBalanceAmount(),
                        MaintenanceLedger.Source.SYSTEM, null, "Bill generated");
                billsGenerated++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("billsGenerated", billsGenerated);
        result.put("billsRegenerated", billsRegenerated);
        result.put("month", request.getMonth());
        result.put("year", request.getYear());
        String message = "Successfully generated " + billsGenerated + " new bills";
        if (billsRegenerated > 0) {
            message += " and regenerated " + billsRegenerated + " existing bills";
        }
        message += " for " + Month.of(request.getMonth()).name() + " " + request.getYear();
        result.put("message", message);
        return result;
    }

    /**
     * Generate a bill for a single unit with line items and arrears/interest calculation.
     */
    private MaintenanceBill generateBillForUnit(Unit unit, List<MaintenanceChargeConfig> activeCharges,
                                                 GenerateBillsRequest request) {
        BigDecimal areaSqft = unit.getAreaSqft() != null ? unit.getAreaSqft() : BigDecimal.ZERO;

        MaintenanceBill bill = new MaintenanceBill();
        bill.setUnit(unit);
        bill.setBillMonth(request.getMonth());
        bill.setBillYear(request.getYear());
        bill.setBillDate(LocalDate.of(request.getYear(), request.getMonth(), 1));
        bill.setUnitAreaSqft(areaSqft);

        int dueDayOfMonth = request.getDueDayOfMonth() != null ? request.getDueDayOfMonth() : 10;
        bill.setDueDate(LocalDate.of(request.getYear(), request.getMonth(), dueDayOfMonth));

        // Compute line items from charge configs
        List<BillLineItem> lineItems = new ArrayList<>();
        BigDecimal currentChargesTotal = BigDecimal.ZERO;

        for (MaintenanceChargeConfig charge : activeCharges) {
            if (!isChargeApplicable(charge, unit)) {
                continue;
            }

            BigDecimal lineAmount = computeChargeAmount(charge, areaSqft, unit);
            if (lineAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BillLineItem lineItem = BillLineItem.builder()
                    .bill(bill)
                    .chargeCode(charge.getChargeCode())
                    .chargeName(charge.getChargeName())
                    .calculationType(charge.getCalculationType().name())
                    .rate(charge.getCalculationType() == CalculationType.AREA_BASED
                            ? charge.getRatePerSqft() : charge.getFlatAmount())
                    .areaSqft(charge.getCalculationType() == CalculationType.AREA_BASED ? areaSqft : null)
                    .amount(lineAmount)
                    .displayOrder(charge.getDisplayOrder())
                    .build();

            lineItems.add(lineItem);
            currentChargesTotal = currentChargesTotal.add(lineAmount);
        }

        // Add penalty line items for this unit/month/year
        List<Penalty> pendingPenalties = penaltyRepository.findByUnit_UnitIdAndBillMonthAndBillYearAndStatus(
                unit.getUnitId(), request.getMonth(), request.getYear(), PenaltyStatus.PENDING);

        for (Penalty penalty : pendingPenalties) {
            BillLineItem penaltyLineItem = BillLineItem.builder()
                    .bill(bill)
                    .chargeCode("PENALTY_" + penalty.getCategory().name())
                    .chargeName("Penalty - " + penalty.getReason())
                    .calculationType("FLAT")
                    .rate(penalty.getAmount())
                    .amount(penalty.getAmount())
                    .displayOrder(99)
                    .build();

            lineItems.add(penaltyLineItem);
            currentChargesTotal = currentChargesTotal.add(penalty.getAmount());

            // Mark penalty as billed
            penalty.setStatus(PenaltyStatus.BILLED);
            penaltyRepository.save(penalty);
        }

        // If no charges apply, skip this unit
        if (lineItems.isEmpty()) {
            return null;
        }

        // Calculate arrears and interest from previous month's unpaid balance
        BigDecimal previousArrears = calculatePreviousArrears(unit.getUnitId(), request.getMonth(), request.getYear());
        BigDecimal interestOnArrears = money(previousArrears.multiply(MONTHLY_INTEREST_RATE));

        // Late fee: a flat, configurable charge applied ONCE when a unit is carrying arrears
        // (i.e. it defaulted on a previous bill). Distinct from interest-on-arrears, which is
        // the time-value cost of the outstanding amount. Defaults to 0 (disabled) so existing
        // societies see no change unless they explicitly configure app.maintenance.late-fee.
        BigDecimal lateFee = BigDecimal.ZERO;
        if (lateFeeAmount != null && lateFeeAmount.compareTo(BigDecimal.ZERO) > 0
                && previousArrears.compareTo(BigDecimal.ZERO) > 0) {
            lateFee = money(lateFeeAmount);
        }

        // Set bill amounts
        bill.setAmount(currentChargesTotal);
        bill.setPreviousArrears(previousArrears);
        bill.setInterestOnArrears(interestOnArrears);
        bill.setLateFee(lateFee);

        // Total = current charges + previous arrears + interest + late fee
        BigDecimal totalAmount = money(currentChargesTotal.add(previousArrears).add(interestOnArrears).add(lateFee));
        bill.setTotalAmount(totalAmount);
        bill.setBalanceAmount(totalAmount);
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setStatus(BillStatus.UNPAID);

        bill.setLineItems(lineItems);

        return bill;
    }

    /**
     * Check if a charge type applies to this unit based on conditions.
     */
    private boolean isChargeApplicable(MaintenanceChargeConfig charge, Unit unit) {
        ApplicableTo applicableTo = charge.getApplicableTo();
        int twoWheelers = unit.getTwoWheelerCount() != null ? unit.getTwoWheelerCount() : 0;
        int fourWheelers = unit.getFourWheelerCount() != null ? unit.getFourWheelerCount() : 0;

        switch (applicableTo) {
            case ALL:
                return true;
            case RENTED:
                return unit.getOccupancyStatus() == OccupancyStatus.RENTED;
            case OWNER_OCCUPIED:
                return unit.getOccupancyStatus() == OccupancyStatus.SELF_OCCUPIED;
            case PARKING:
                // Any parking (two-wheeler or four-wheeler)
                return twoWheelers > 0 || fourWheelers > 0;
            case TWO_WHEELER:
                return twoWheelers > 0;
            case FOUR_WHEELER:
                return fourWheelers > 0;
            default:
                return true;
        }
    }

    /**
     * Compute the charge amount based on calculation type.
     * Special handling for WATER_CHARGES: uses WaterChargeConfig for tank-based or municipal calculation.
     * Falls back to unit-specific waterCharges field if no config exists.
     */
    private BigDecimal computeChargeAmount(MaintenanceChargeConfig charge, BigDecimal areaSqft, Unit unit) {
        // Special handling: Water charges use WaterChargeConfig
        if ("WATER_CHARGES".equals(charge.getChargeCode())) {
            BigDecimal waterCharge = waterChargeConfigService.computeWaterChargeForUnit(unit);
            if (waterCharge.compareTo(BigDecimal.ZERO) > 0) {
                return money(waterCharge);
            }
            // Fall back to flat amount from config if no water charge config and no unit-specific amount
            return money(charge.getFlatAmount() != null ? charge.getFlatAmount() : BigDecimal.ZERO);
        }

        // Special handling: Parking charges multiply by vehicle count
        if (charge.getApplicableTo() == ApplicableTo.TWO_WHEELER) {
            int count = unit.getTwoWheelerCount() != null ? unit.getTwoWheelerCount() : 0;
            BigDecimal rate = charge.getFlatAmount() != null ? charge.getFlatAmount() : BigDecimal.ZERO;
            return money(rate.multiply(BigDecimal.valueOf(count)));
        }
        if (charge.getApplicableTo() == ApplicableTo.FOUR_WHEELER) {
            int count = unit.getFourWheelerCount() != null ? unit.getFourWheelerCount() : 0;
            BigDecimal rate = charge.getFlatAmount() != null ? charge.getFlatAmount() : BigDecimal.ZERO;
            return money(rate.multiply(BigDecimal.valueOf(count)));
        }

        if (charge.getCalculationType() == CalculationType.AREA_BASED) {
            if (charge.getRatePerSqft() == null || areaSqft == null || areaSqft.compareTo(BigDecimal.ZERO) <= 0) {
                return BigDecimal.ZERO;
            }
            // Consistent scale-2 rounding (was scale 0 / whole rupees, which broke reconciliation).
            return money(charge.getRatePerSqft().multiply(areaSqft));
        } else {
            // FLAT amount
            return money(charge.getFlatAmount() != null ? charge.getFlatAmount() : BigDecimal.ZERO);
        }
    }

    /**
     * Calculate the total unpaid arrears from all previous months for a unit.
     * This includes:
     * 1. Balance from all unpaid/partially-paid/overdue bills (EXCLUDING current month)
     * 2. Opening balance (legacy arrears from before app deployment)
     */
    private BigDecimal calculatePreviousArrears(Long unitId, int currentMonth, int currentYear) {
        List<MaintenanceBill> outstandingBills = billRepository.findOutstandingByUnit(unitId);

        BigDecimal totalArrears = BigDecimal.ZERO;
        for (MaintenanceBill existingBill : outstandingBills) {
            // Only consider bills BEFORE the current month
            if (isBillBeforeMonth(existingBill, currentMonth, currentYear)) {
                BigDecimal balance = existingBill.getBalanceAmount() != null
                        ? existingBill.getBalanceAmount() : BigDecimal.ZERO;
                totalArrears = totalArrears.add(balance);
            }
        }

        // Add opening balance (legacy arrears)
        BigDecimal openingBalance = openingBalanceRepository.getTotalOpeningBalanceByUnit(unitId);
        if (openingBalance != null && openingBalance.compareTo(BigDecimal.ZERO) > 0) {
            totalArrears = totalArrears.add(openingBalance);
        }

        return totalArrears;
    }

    private boolean isBillBeforeMonth(MaintenanceBill bill, int month, int year) {
        if (bill.getBillYear() < year) return true;
        if (bill.getBillYear() == year && bill.getBillMonth() < month) return true;
        return false;
    }

    // ======================== BILL QUERIES ========================

    public PagedResponse<BillDTO> getBillsByMonth(int month, int year, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("unit.unitId").ascending());
        Page<MaintenanceBill> billPage = billRepository.findByBillMonthAndBillYear(month, year, pageable);

        List<BillDTO> bills = billPage.getContent().stream()
                .map(this::mapToBillDTO)
                .collect(Collectors.toList());

        return new PagedResponse<>(bills, billPage.getNumber(), billPage.getSize(),
                billPage.getTotalElements(), billPage.getTotalPages(), billPage.isLast());
    }

    public List<BillDTO> getBillsByUnit(Long unitId) {
        List<MaintenanceBill> bills = billRepository.findByUnit_UnitIdOrderByBillYearDescBillMonthDesc(unitId);
        return bills.stream()
                .map(this::mapToBillDTO)
                .collect(Collectors.toList());
    }

    public BillDTO getBillById(Long billId) {
        MaintenanceBill bill = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found with id: " + billId));
        return mapToBillDTOWithLineItems(bill);
    }

    public List<BillDTO> getOutstandingByUnit(Long unitId) {
        List<MaintenanceBill> bills = billRepository.findOutstandingByUnit(unitId);
        return bills.stream()
                .map(this::mapToBillDTOWithLineItems)
                .collect(Collectors.toList());
    }

    public BigDecimal getTotalOutstanding(Long unitId) {
        BigDecimal billOutstanding = billRepository.getTotalOutstandingByUnit(unitId);
        BigDecimal openingBalance = openingBalanceRepository.getTotalOpeningBalanceByUnit(unitId);
        BigDecimal total = (billOutstanding != null ? billOutstanding : BigDecimal.ZERO)
                .add(openingBalance != null ? openingBalance : BigDecimal.ZERO);
        return total;
    }

    public List<BillDTO> getDefaulters(int month, int year) {
        List<MaintenanceBill> bills = billRepository.findDefaulters(month, year);
        return bills.stream()
                .map(this::mapToBillDTO)
                .collect(Collectors.toList());
    }

    // ======================== PAYMENT ========================

    /**
     * Public entry point: retries the transactional core on optimistic-lock conflicts so a
     * concurrent payment on the same bill cannot cause a lost update.
     */
    public PaymentDTO recordOfflinePayment(RecordOfflinePaymentRequest request) {
        return optimisticRetry.execute("record offline payment",
                () -> self.recordOfflinePaymentTransactional(request));
    }

    @Transactional
    public PaymentDTO recordOfflinePaymentTransactional(RecordOfflinePaymentRequest request) {
        MaintenanceBill bill = billRepository.findById(request.getBillId())
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found with id: " + request.getBillId()));

        BigDecimal totalReceived = request.getAmount();

        // An owner can transfer any amount to the society bank account (e.g. paying more than
        // this month's bill, rounding up, or clearing several months at once). We must record
        // what actually happened rather than reject it.
        //
        // The payment record captures the FULL received amount so it is visible in the
        // Transaction module and payment history (these read MaintenancePayment). The BILL,
        // however, only absorbs up to its outstanding balance so its balance never goes
        // negative (keeping arrears carry-forward, outstanding totals and defaulter reports
        // correct). Any surplus is credited to the unit's suspense/advance account so it is
        // tracked and can later be applied to a future bill — nothing is lost.
        BigDecimal balanceBefore = bill.getBalanceAmount() != null ? bill.getBalanceAmount() : BigDecimal.ZERO;
        BigDecimal appliedToBill = totalReceived.min(balanceBefore).max(BigDecimal.ZERO);
        BigDecimal surplus = totalReceived.subtract(appliedToBill).max(BigDecimal.ZERO);

        PaymentMode paymentMode = PaymentMode.valueOf(request.getPaymentMode());
        String payerType = request.getPayerType() != null ? request.getPayerType() : "OWNER";

        // ---- 1. Record the FULL received amount as a single payment (what the owner paid) ----
        MaintenancePayment payment = new MaintenancePayment();
        payment.setBill(bill);
        payment.setUnit(bill.getUnit());
        payment.setAmount(totalReceived);
        payment.setPaymentDate(request.getPaymentDate());
        payment.setPaymentMode(paymentMode);
        payment.setTransactionId(request.getTransactionId());
        payment.setPayerName(request.getPayerName());
        payment.setPayerType(payerType);
        payment.setReceiptNumber(generateReceiptNumber());
        payment.setStatus(PaymentStatus.VERIFIED);
        payment.setRemarks(buildAppliedRemarks(request.getRemarks(), totalReceived, appliedToBill, surplus));
        payment.setAppliedAmount(appliedToBill);
        payment.setVerifiedOn(LocalDateTime.now());
        payment.setVerifiedBy(currentUsername());
        // MaintenancePayment has a non-null @Version, so Spring Data's save() treats a brand-new
        // instance as "not new" and routes it through EntityManager.merge(). merge() persists a
        // managed COPY and returns it, leaving the original `payment` reference unmanaged with a
        // null id. We must keep the returned managed instance — otherwise payment.getPaymentId()
        // is null (breaking the DTO, the advance-credit link and reversal) and mutating the
        // original later triggers a second INSERT (the duplicate-receipt bug).
        payment = paymentRepository.save(payment);

        // ---- 2. Apply only up to the outstanding balance to the bill (never overpay it) ----
        BigDecimal paidAmount = (bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO)
                .add(appliedToBill);
        bill.setPaidAmount(paidAmount);
        BigDecimal newBalance = bill.getTotalAmount().subtract(paidAmount).max(BigDecimal.ZERO);
        bill.setBalanceAmount(newBalance);
        if (newBalance.compareTo(BigDecimal.ZERO) <= 0) {
            bill.setStatus(BillStatus.PAID);
        } else if (paidAmount.compareTo(BigDecimal.ZERO) > 0) {
            bill.setStatus(BillStatus.PARTIALLY_PAID);
        }
        billRepository.save(bill);

        ledgerService.record(bill, payment.getPaymentId(),
                MaintenanceLedger.EntryType.PAYMENT_APPLIED, appliedToBill,
                balanceBefore, bill.getBalanceAmount(),
                MaintenanceLedger.Source.OFFLINE, payment.getReceiptNumber(),
                request.getRemarks());

        // ---- 3. Record any surplus as the unit's advance credit (known owner, known source) ----
        if (surplus.compareTo(BigDecimal.ZERO) > 0) {
            UnitAdvanceCredit credit = UnitAdvanceCredit.builder()
                    .unit(bill.getUnit())
                    .amount(surplus)
                    .appliedAmount(BigDecimal.ZERO)
                    .balanceAmount(surplus)
                    .receivedDate(request.getPaymentDate())
                    .paymentMode(paymentMode.name())
                    .referenceNumber(request.getTransactionId())
                    .payerName(request.getPayerName())
                    .sourcePaymentId(payment.getPaymentId())
                    .sourceBillId(bill.getBillId())
                    .remarks("Overpayment on receipt " + payment.getReceiptNumber()
                            + " against bill #" + bill.getBillId()
                            + " (" + Month.of(bill.getBillMonth()).name() + " " + bill.getBillYear() + "). "
                            + "Received " + totalReceived + ", applied " + appliedToBill + ", credit " + surplus + ".")
                    .status(UnitAdvanceCredit.CreditStatus.AVAILABLE)
                    .build();
            credit = advanceCreditRepository.save(credit);

            // Link the credit to the payment so a later reversal can undo both together.
            // `payment` here is the MANAGED instance returned by save() above, so mutating it is
            // enough — the change is flushed automatically at commit. (Do NOT call save() again;
            // re-saving previously caused a duplicate INSERT of the same receipt number.)
            payment.setSurplusCreditId(credit.getAdvanceCreditId());

            log.info("Offline payment on bill {}: received {}, applied {}, surplus {} held as advance credit {} for unit {}",
                    bill.getBillId(), totalReceived, appliedToBill, surplus,
                    credit.getAdvanceCreditId(), bill.getUnit().getUnitNumber());
        }

        return mapToPaymentDTO(payment);
    }

    /** Compose the payment remarks, noting how a payment larger than the bill was split. */
    private String buildAppliedRemarks(String userRemarks, BigDecimal totalReceived,
                                       BigDecimal appliedToBill, BigDecimal surplus) {
        String base = userRemarks != null ? userRemarks : "";
        if (surplus.compareTo(BigDecimal.ZERO) > 0) {
            String note = "[Received " + totalReceived + "; applied " + appliedToBill
                    + " to this bill; surplus " + surplus + " credited to unit advance]";
            return base.isBlank() ? note : base + " " + note;
        }
        return base.isBlank() ? null : base;
    }

    // ======================== PAYMENT REVERSAL ========================

    /**
     * Reverse (void) a payment. Restores the payment amount to the bill's outstanding
     * balance, recomputes status, marks the payment REVERSED, and writes an audit entry.
     * Retried on optimistic-lock conflict so a concurrent update can't corrupt the balance.
     */
    public PaymentDTO reversePayment(Long paymentId, String reason) {
        return optimisticRetry.execute("reverse payment",
                () -> self.reversePaymentTransactional(paymentId, reason));
    }

    @Transactional
    public PaymentDTO reversePaymentTransactional(Long paymentId, String reason) {
        MaintenancePayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));

        if (payment.getStatus() == PaymentStatus.REVERSED) {
            throw new BusinessException("Payment has already been reversed");
        }
        if (payment.getStatus() == PaymentStatus.FAILED) {
            throw new BusinessException("A failed payment cannot be reversed");
        }

        MaintenanceBill bill = payment.getBill();
        if (bill == null) {
            throw new BusinessException("Payment is not linked to a bill and cannot be reversed here");
        }

        BigDecimal amount = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
        // Only the portion that was actually applied to the bill should be removed from it.
        // For an overpayment, amount = full received but appliedAmount = what reduced this bill;
        // the surplus lives in a linked suspense entry and is reversed separately below.
        BigDecimal appliedToBill = payment.getAppliedAmount() != null ? payment.getAppliedAmount() : amount;
        BigDecimal balanceBefore = bill.getBalanceAmount() != null ? bill.getBalanceAmount() : BigDecimal.ZERO;

        // Restore the applied amount to the bill: reduce paidAmount, recompute balance (clamped to total).
        BigDecimal paidSoFar = bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal newPaid = paidSoFar.subtract(appliedToBill).max(BigDecimal.ZERO);
        bill.setPaidAmount(newPaid);
        BigDecimal newBalance = bill.getTotalAmount().subtract(newPaid).max(BigDecimal.ZERO);
        bill.setBalanceAmount(newBalance);

        if (newPaid.compareTo(BigDecimal.ZERO) <= 0) {
            bill.setStatus(BillStatus.UNPAID);
        } else if (newBalance.compareTo(BigDecimal.ZERO) <= 0) {
            bill.setStatus(BillStatus.PAID);
        } else {
            bill.setStatus(BillStatus.PARTIALLY_PAID);
        }
        billRepository.save(bill);

        // If this payment carried an advance-credit surplus, void that credit too so the whole
        // payment is undone together (money can't linger as credit after a reversal). If part of
        // the credit has already been applied to a later bill, we refuse rather than silently
        // leave the books inconsistent.
        if (payment.getSurplusCreditId() != null) {
            UnitAdvanceCredit credit = advanceCreditRepository.findById(payment.getSurplusCreditId()).orElse(null);
            if (credit != null && credit.getStatus() == UnitAdvanceCredit.CreditStatus.AVAILABLE) {
                BigDecimal applied = credit.getAppliedAmount() != null ? credit.getAppliedAmount() : BigDecimal.ZERO;
                if (applied.compareTo(BigDecimal.ZERO) > 0) {
                    throw new BusinessException(
                            "Part of this payment's advance credit has already been applied to another bill and cannot be reversed automatically.");
                }
                credit.setStatus(UnitAdvanceCredit.CreditStatus.REVERSED);
                credit.setBalanceAmount(BigDecimal.ZERO);
                credit.setRemarks((credit.getRemarks() != null ? credit.getRemarks() + " " : "")
                        + "[Voided: source payment " + payment.getReceiptNumber() + " reversed - " + reason + "]");
                advanceCreditRepository.save(credit);
            }
        }

        // Mark the payment reversed (kept for audit; never deleted).
        payment.setStatus(PaymentStatus.REVERSED);
        payment.setReversedOn(LocalDateTime.now());
        payment.setReversedBy(currentUsername());
        payment.setReversalReason(reason);
        paymentRepository.save(payment);

        // Audit: negative amount reflects the money removed from THIS bill (the applied portion).
        ledgerService.record(bill, payment.getPaymentId(),
                MaintenanceLedger.EntryType.PAYMENT_REVERSED, appliedToBill.negate(),
                balanceBefore, newBalance,
                MaintenanceLedger.Source.ADMIN, payment.getReceiptNumber(), reason);

        log.info("Payment {} reversed (received {}, applied-to-bill {}), bill {} balance {} -> {}",
                paymentId, amount, appliedToBill, bill.getBillId(), balanceBefore, newBalance);

        return mapToPaymentDTO(payment);
    }

    // ======================== PAYMENT REASSIGNMENT ========================

    /**
     * Reassign a wrongly-attributed payment to the correct unit's bill.
     *
     * <p>Implemented as an atomic reverse-and-recreate: the original payment is reversed
     * (restoring the source bill's balance and marked REVERSED for audit), and an equivalent
     * VERIFIED payment is created against the target bill carrying over the original's mode,
     * transaction reference, payer and amount. Both mutations and their ledger entries are
     * committed together; retried on optimistic-lock conflict.
     *
     * @return the newly created payment on the target bill
     */
    public PaymentDTO reassignPayment(Long paymentId, Long targetBillId, String reason) {
        return optimisticRetry.execute("reassign payment",
                () -> self.reassignPaymentTransactional(paymentId, targetBillId, reason));
    }

    @Transactional
    public PaymentDTO reassignPaymentTransactional(Long paymentId, Long targetBillId, String reason) {
        MaintenancePayment original = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));

        if (original.getStatus() == PaymentStatus.REVERSED) {
            throw new BusinessException("Payment has already been reversed and cannot be reassigned");
        }
        if (original.getStatus() == PaymentStatus.FAILED) {
            throw new BusinessException("A failed payment cannot be reassigned");
        }

        MaintenanceBill sourceBill = original.getBill();
        if (sourceBill == null) {
            throw new BusinessException("Payment is not linked to a bill and cannot be reassigned here");
        }

        MaintenanceBill targetBill = billRepository.findById(targetBillId)
                .orElseThrow(() -> new ResourceNotFoundException("Target bill not found with id: " + targetBillId));

        if (targetBill.getBillId().equals(sourceBill.getBillId())) {
            throw new BusinessException("The payment is already assigned to this bill");
        }
        if (targetBill.getStatus() == BillStatus.PAID) {
            throw new BusinessException("Target bill is already fully paid");
        }

        // A payment that carried an overpayment surplus (advance credit) can't be cleanly
        // moved in one step, because part of it lives in a separate credit record. Ask the
        // manager to reverse it (which also voids the credit) and record a fresh payment instead.
        if (original.getSurplusCreditId() != null) {
            throw new BusinessException(
                    "This payment includes an advance-credit surplus. Please reverse it and record a new payment on the correct bill.");
        }

        BigDecimal amount = original.getAmount() != null ? original.getAmount() : BigDecimal.ZERO;

        BigDecimal targetBalance = targetBill.getBalanceAmount() != null
                ? targetBill.getBalanceAmount() : BigDecimal.ZERO;
        if (amount.compareTo(targetBalance) > 0) {
            throw new BusinessException(
                    "Payment amount exceeds the outstanding balance of the target bill (" + targetBalance + ")");
        }

        // ---- 1. Reverse the original payment off the source bill ----
        // (No surplus here — guarded above — so amount == what was applied to the source bill.)
        BigDecimal sourceBalanceBefore = sourceBill.getBalanceAmount() != null
                ? sourceBill.getBalanceAmount() : BigDecimal.ZERO;
        BigDecimal sourcePaidSoFar = sourceBill.getPaidAmount() != null
                ? sourceBill.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal sourceNewPaid = sourcePaidSoFar.subtract(amount).max(BigDecimal.ZERO);
        sourceBill.setPaidAmount(sourceNewPaid);
        BigDecimal sourceNewBalance = sourceBill.getTotalAmount().subtract(sourceNewPaid).max(BigDecimal.ZERO);
        sourceBill.setBalanceAmount(sourceNewBalance);
        if (sourceNewPaid.compareTo(BigDecimal.ZERO) <= 0) {
            sourceBill.setStatus(BillStatus.UNPAID);
        } else if (sourceNewBalance.compareTo(BigDecimal.ZERO) <= 0) {
            sourceBill.setStatus(BillStatus.PAID);
        } else {
            sourceBill.setStatus(BillStatus.PARTIALLY_PAID);
        }
        billRepository.save(sourceBill);

        String reassignNote = "Reassigned to bill #" + targetBill.getBillId()
                + " (unit " + targetBill.getUnit().getUnitNumber() + "): " + reason;
        original.setStatus(PaymentStatus.REVERSED);
        original.setReversedOn(LocalDateTime.now());
        original.setReversedBy(currentUsername());
        original.setReversalReason(reassignNote);
        paymentRepository.save(original);

        ledgerService.record(sourceBill, original.getPaymentId(),
                MaintenanceLedger.EntryType.PAYMENT_REVERSED, amount.negate(),
                sourceBalanceBefore, sourceNewBalance,
                MaintenanceLedger.Source.ADMIN, original.getReceiptNumber(), reassignNote);

        // ---- 2. Create the equivalent payment on the target bill ----
        MaintenancePayment moved = new MaintenancePayment();
        moved.setBill(targetBill);
        moved.setUnit(targetBill.getUnit());
        moved.setAmount(amount);
        moved.setPaymentDate(original.getPaymentDate() != null ? original.getPaymentDate() : LocalDate.now());
        moved.setPaymentMode(original.getPaymentMode());
        moved.setTransactionId(original.getTransactionId());
        moved.setPayerName(original.getPayerName());
        moved.setPayerType(original.getPayerType() != null ? original.getPayerType() : "OWNER");
        moved.setReceiptNumber(generateReceiptNumber());
        moved.setStatus(PaymentStatus.VERIFIED);
        moved.setRemarks("Reassigned from bill #" + sourceBill.getBillId()
                + " (receipt " + original.getReceiptNumber() + "): " + reason);
        moved.setVerifiedOn(LocalDateTime.now());
        moved.setVerifiedBy(currentUsername());
        paymentRepository.save(moved);

        BigDecimal targetBalanceBefore = targetBalance;
        BigDecimal targetPaidSoFar = targetBill.getPaidAmount() != null
                ? targetBill.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal targetNewPaid = targetPaidSoFar.add(amount);
        targetBill.setPaidAmount(targetNewPaid);
        BigDecimal targetNewBalance = targetBill.getTotalAmount().subtract(targetNewPaid).max(BigDecimal.ZERO);
        targetBill.setBalanceAmount(targetNewBalance);
        if (targetNewBalance.compareTo(BigDecimal.ZERO) <= 0) {
            targetBill.setStatus(BillStatus.PAID);
        } else {
            targetBill.setStatus(BillStatus.PARTIALLY_PAID);
        }
        billRepository.save(targetBill);

        ledgerService.record(targetBill, moved.getPaymentId(),
                MaintenanceLedger.EntryType.PAYMENT_APPLIED, amount,
                targetBalanceBefore, targetNewBalance,
                MaintenanceLedger.Source.ADMIN, moved.getReceiptNumber(), reassignNote);

        log.info("Payment {} reassigned from bill {} to bill {} (amount {}); new payment {}",
                paymentId, sourceBill.getBillId(), targetBill.getBillId(), amount, moved.getPaymentId());

        return mapToPaymentDTO(moved);
    }

    private String currentUsername() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null
                && !"anonymousUser".equalsIgnoreCase(auth.getName())) {
            return auth.getName();
        }
        return "ADMIN";
    }

    // ======================== LEDGER QUERIES ========================

    public List<LedgerEntryDTO> getLedgerByBill(Long billId) {
        return ledgerRepository.findByBillIdOrderByPerformedOnAscLedgerIdAsc(billId)
                .stream().map(this::mapToLedgerDTO).collect(Collectors.toList());
    }

    public List<LedgerEntryDTO> getLedgerByUnit(Long unitId) {
        return ledgerRepository.findByUnitIdOrderByPerformedOnDescLedgerIdDesc(unitId)
                .stream().map(this::mapToLedgerDTO).collect(Collectors.toList());
    }

    private LedgerEntryDTO mapToLedgerDTO(MaintenanceLedger e) {
        return LedgerEntryDTO.builder()
                .ledgerId(e.getLedgerId())
                .billId(e.getBillId())
                .unitId(e.getUnitId())
                .paymentId(e.getPaymentId())
                .entryType(e.getEntryType() != null ? e.getEntryType().name() : null)
                .amount(e.getAmount())
                .balanceBefore(e.getBalanceBefore())
                .balanceAfter(e.getBalanceAfter())
                .source(e.getSource() != null ? e.getSource().name() : null)
                .reference(e.getReference())
                .performedBy(e.getPerformedBy())
                .performedOn(e.getPerformedOn())
                .reason(e.getReason())
                .build();
    }

    public PagedResponse<PaymentDTO> getPaymentsByUnit(Long unitId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "paymentDate"));
        Page<MaintenancePayment> paymentPage = paymentRepository.findByUnit_UnitId(unitId, pageable);

        List<PaymentDTO> payments = paymentPage.getContent().stream()
                .map(this::mapToPaymentDTO)
                .collect(Collectors.toList());

        return new PagedResponse<>(payments, paymentPage.getNumber(), paymentPage.getSize(),
                paymentPage.getTotalElements(), paymentPage.getTotalPages(), paymentPage.isLast());
    }

    public List<PaymentDTO> getPaymentsByBill(Long billId) {
        List<MaintenancePayment> payments = paymentRepository.findByBill_BillId(billId);
        return payments.stream()
                .map(this::mapToPaymentDTO)
                .collect(Collectors.toList());
    }

    // ======================== ADVANCE CREDIT ========================

    /** All advance-credit entries for a unit (newest first). */
    public List<AdvanceCreditDTO> getAdvanceCreditByUnit(Long unitId) {
        return advanceCreditRepository
                .findByUnit_UnitIdOrderByReceivedDateDescAdvanceCreditIdDesc(unitId)
                .stream().map(this::mapToAdvanceCreditDTO).collect(Collectors.toList());
    }

    /** Total still-available advance credit for a unit. */
    public BigDecimal getAvailableAdvanceCredit(Long unitId) {
        BigDecimal available = advanceCreditRepository.getAvailableCreditByUnit(unitId);
        return available != null ? available : BigDecimal.ZERO;
    }

    private AdvanceCreditDTO mapToAdvanceCreditDTO(UnitAdvanceCredit c) {
        return AdvanceCreditDTO.builder()
                .advanceCreditId(c.getAdvanceCreditId())
                .unitId(c.getUnit() != null ? c.getUnit().getUnitId() : null)
                .unitNumber(c.getUnit() != null ? c.getUnit().getUnitNumber() : null)
                .amount(c.getAmount())
                .appliedAmount(c.getAppliedAmount())
                .balanceAmount(c.getBalanceAmount())
                .receivedDate(c.getReceivedDate())
                .paymentMode(c.getPaymentMode())
                .referenceNumber(c.getReferenceNumber())
                .payerName(c.getPayerName())
                .sourcePaymentId(c.getSourcePaymentId())
                .sourceBillId(c.getSourceBillId())
                .remarks(c.getRemarks())
                .status(c.getStatus() != null ? c.getStatus().name() : null)
                .createdOn(c.getCreatedOn())
                .createdBy(c.getCreatedBy())
                .build();
    }

    public Map<String, Object> getCollectionSummary(int month, int year) {
        List<MaintenanceBill> bills = billRepository.findByBillMonthAndBillYear(month, year);

        BigDecimal totalBilled = bills.stream()
                .map(MaintenanceBill::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCollected = bills.stream()
                .map(b -> b.getPaidAmount() != null ? b.getPaidAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalOutstanding = bills.stream()
                .map(b -> b.getBalanceAmount() != null ? b.getBalanceAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long paidCount = bills.stream().filter(b -> b.getStatus() == BillStatus.PAID).count();
        long unpaidCount = bills.stream().filter(b -> b.getStatus() == BillStatus.UNPAID).count();
        long partialCount = bills.stream().filter(b -> b.getStatus() == BillStatus.PARTIALLY_PAID).count();
        long overdueCount = bills.stream().filter(b -> b.getStatus() == BillStatus.OVERDUE).count();

        Map<String, Object> summary = new HashMap<>();
        summary.put("month", month);
        summary.put("year", year);
        summary.put("totalBilled", totalBilled);
        summary.put("totalCollected", totalCollected);
        summary.put("totalOutstanding", totalOutstanding);
        summary.put("paidCount", paidCount);
        summary.put("unpaidCount", unpaidCount);
        summary.put("partialCount", partialCount);
        summary.put("overdueCount", overdueCount);
        return summary;
    }

    // ======================== HELPERS ========================

    private String generateReceiptNumber() {
        return receiptNumberService.next();
    }

    private BillDTO mapToBillDTO(MaintenanceBill bill) {
        BillDTO dto = new BillDTO();
        dto.setBillId(bill.getBillId());
        dto.setUnitId(bill.getUnit().getUnitId());
        dto.setUnitNumber(bill.getUnit().getUnitNumber());
        dto.setOwnerName(bill.getUnit().getOwnerNames());
        dto.setBillMonth(bill.getBillMonth());
        dto.setBillYear(bill.getBillYear());
        dto.setBillPeriod(Month.of(bill.getBillMonth()).name() + " " + bill.getBillYear());
        dto.setBillDate(bill.getBillDate());
        dto.setDueDate(bill.getDueDate());
        dto.setAmount(bill.getAmount());
        dto.setPreviousArrears(bill.getPreviousArrears());
        dto.setInterestOnArrears(bill.getInterestOnArrears());
        dto.setLateFee(bill.getLateFee());
        dto.setTotalAmount(bill.getTotalAmount());
        dto.setPaidAmount(bill.getPaidAmount());
        dto.setBalanceAmount(bill.getBalanceAmount());
        dto.setStatus(bill.getStatus().name());
        dto.setPaymentLink(bill.getPaymentLink());
        dto.setCashfreeOrderId(bill.getCashfreeOrderId());
        dto.setUnitAreaSqft(bill.getUnitAreaSqft());
        return dto;
    }

    private BillDTO mapToBillDTOWithLineItems(MaintenanceBill bill) {
        BillDTO dto = mapToBillDTO(bill);

        // Load line items
        List<BillLineItem> lineItems = bill.getLineItems();
        if (lineItems != null && !lineItems.isEmpty()) {
            List<BillLineItemDTO> lineItemDTOs = lineItems.stream()
                    .map(li -> BillLineItemDTO.builder()
                            .lineItemId(li.getLineItemId())
                            .chargeCode(li.getChargeCode())
                            .chargeName(li.getChargeName())
                            .calculationType(li.getCalculationType())
                            .rate(li.getRate())
                            .areaSqft(li.getAreaSqft())
                            .amount(li.getAmount())
                            .displayOrder(li.getDisplayOrder())
                            .build())
                    .collect(Collectors.toList());
            dto.setLineItems(lineItemDTOs);
        }

        return dto;
    }

    private PaymentDTO mapToPaymentDTO(MaintenancePayment payment) {
        return PaymentDTO.builder()
                .paymentId(payment.getPaymentId())
                .billId(payment.getBill().getBillId())
                .unitId(payment.getUnit().getUnitId())
                .unitNumber(payment.getUnit().getUnitNumber())
                .amount(payment.getAmount())
                .paymentDate(payment.getPaymentDate())
                .paymentMode(payment.getPaymentMode().name())
                .transactionId(payment.getTransactionId())
                .cashfreePaymentId(payment.getCashfreePaymentId())
                .cashfreeOrderId(payment.getCashfreeOrderId())
                .payerName(payment.getPayerName())
                .payerType(payment.getPayerType())
                .receiptNumber(payment.getReceiptNumber())
                .status(payment.getStatus().name())
                .remarks(payment.getRemarks())
                .verifiedOn(payment.getVerifiedOn())
                .verifiedBy(payment.getVerifiedBy())
                .reversedOn(payment.getReversedOn())
                .reversedBy(payment.getReversedBy())
                .reversalReason(payment.getReversalReason())
                .build();
    }
}
