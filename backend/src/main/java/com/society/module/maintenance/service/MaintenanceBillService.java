package com.society.module.maintenance.service;

import com.society.common.PagedResponse;
import com.society.enums.OccupancyStatus;
import com.society.enums.UnitType;
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
    private final com.society.module.maintenance.repository.UnitTagAssignmentRepository unitTagAssignmentRepository;
    private final BillLineItemRepository lineItemRepository;
    private final UnitRepository unitRepository;
    private final WaterChargeConfigService waterChargeConfigService;
    private final PenaltyRepository penaltyRepository;
    private final OpeningBalanceRepository openingBalanceRepository;
    private final OpeningBalanceService openingBalanceService;
    private final OptimisticRetry optimisticRetry;
    private final ReceiptNumberService receiptNumberService;
    private final MaintenanceLedgerService ledgerService;
    private final com.society.module.maintenance.repository.MaintenanceLedgerRepository ledgerRepository;
    private final com.society.module.maintenance.repository.UnitAdvanceCreditRepository advanceCreditRepository;
    private final com.society.module.settings.service.SocietySettingsService societySettingsService;

    /**
     * Resolve the effective monthly interest rate (%) charged on outstanding principal —
     * the accountant's "Add: Interest". Admin-configurable (no redeploy); 0 = disabled.
     */
    private BigDecimal effectiveInterestRatePercent() {
        BigDecimal fromSettings = societySettingsService.getSettings().getInterestRatePercent();
        return fromSettings != null ? fromSettings : BigDecimal.ZERO;
    }

    /**
     * Resolve the due date for a bill. {@code dueDayOfMonth} may be null (fall back to the
     * society's configured {@code billDueDay}), 0 (meaning "last day of the month"), or any
     * day number; values past the month's length are clamped to the final calendar day. So
     * 0, 31-on-a-30-day-month, and 31-in-February all safely resolve to month-end.
     */
    private LocalDate resolveDueDate(int year, int month, Integer dueDayOfMonth) {
        Integer day = dueDayOfMonth;
        if (day == null) {
            day = societySettingsService.getSettings().getBillDueDay();
        }
        LocalDate firstOfMonth = LocalDate.of(year, month, 1);
        int lastDay = firstOfMonth.lengthOfMonth();
        // 0 (or null settings) => last day of month; otherwise clamp into [1, lastDay].
        if (day == null || day <= 0) {
            return firstOfMonth.withDayOfMonth(lastDay);
        }
        int clamped = Math.min(day, lastDay);
        return firstOfMonth.withDayOfMonth(clamped);
    }

    /**
     * Self-reference (through the Spring proxy) so retry can re-invoke the @Transactional
     * core method in a FRESH transaction. A plain this.method() call would bypass the proxy
     * and reuse the same (already-failed) transaction. @Lazy breaks the self-injection cycle.
     */
    @Autowired
    @Lazy
    private MaintenanceBillService self;

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
        // Pre-load unit->tags once (mirroring how activeCharges is loaded once) so tag-scoped
        // charges can be filtered per unit without an N+1 query during the unit loop.
        Map<Long, Set<Long>> unitTagMap = loadUnitTagMap();
        int billsGenerated = 0;
        int billsRegenerated = 0;
        int billsSkipped = 0;
        List<String> skippedUnits = new ArrayList<>();
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
                    // Only regenerate UNPAID bills - don't touch paid/partially paid.
                    // A bill can be UNPAID yet still have payment rows attached (e.g. a payment
                    // that was recorded then REVERSED, or a failed/pending online attempt). Those
                    // rows reference the bill via a NOT-NULL foreign key (maintenance_payments.bill_id)
                    // with no cascade, so deleting the bill would throw a FK violation (the
                    // production 500). Skip regeneration for such bills rather than destroying
                    // payment history; report them so the admin can reverse/clean up first.
                    long paymentRows = paymentRepository.countByBill_BillId(existingBill.getBillId());
                    if (existingBill.getStatus() == BillStatus.UNPAID && paymentRows > 0) {
                        log.warn("Skipping regeneration of UNPAID bill {} for unit {} ({}/{}): {} payment row(s) reference it; delete would violate the FK.",
                                existingBill.getBillId(), unit.getUnitNumber(),
                                request.getMonth(), request.getYear(), paymentRows);
                        billsSkipped++;
                        skippedUnits.add(unit.getUnitNumber());
                        continue;
                    }
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

                        // If this bill had auto-applied advance credit, give it back to the
                        // unit's credits before deleting, so the regenerated bill re-applies it
                        // instead of the credit being lost (keeps regenerate idempotent).
                        refundAdvanceCredit(existingBill);

                        billRepository.delete(existingBill);
                        billRepository.flush();
                        // Generate fresh bill
                        MaintenanceBill newBill = generateBillForUnit(unit, activeCharges, request, unitTagMap);
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
            MaintenanceBill bill = generateBillForUnit(unit, activeCharges, request, unitTagMap);
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
        result.put("billsSkipped", billsSkipped);
        result.put("skippedUnits", skippedUnits);
        result.put("month", request.getMonth());
        result.put("year", request.getYear());
        String message = "Successfully generated " + billsGenerated + " new bills";
        if (billsRegenerated > 0) {
            message += " and regenerated " + billsRegenerated + " existing bills";
        }
        message += " for " + Month.of(request.getMonth()).name() + " " + request.getYear();
        if (billsSkipped > 0) {
            message += ". Skipped " + billsSkipped + " bill(s) that have payment records (reverse those payments first to regenerate): "
                    + String.join(", ", skippedUnits);
        }
        result.put("message", message);
        return result;
    }

    /**
     * Generate a bill for a single unit with line items and arrears/interest calculation.
     */
    private MaintenanceBill generateBillForUnit(Unit unit, List<MaintenanceChargeConfig> activeCharges,
                                                 GenerateBillsRequest request, Map<Long, Set<Long>> unitTagMap) {
        BigDecimal areaSqft = unit.getAreaSqft() != null ? unit.getAreaSqft() : BigDecimal.ZERO;

        MaintenanceBill bill = new MaintenanceBill();
        bill.setUnit(unit);
        bill.setBillMonth(request.getMonth());
        bill.setBillYear(request.getYear());
        bill.setBillDate(LocalDate.of(request.getYear(), request.getMonth(), 1));
        bill.setUnitAreaSqft(areaSqft);

        bill.setDueDate(resolveDueDate(request.getYear(), request.getMonth(), request.getDueDayOfMonth()));

        // Compute line items from charge configs
        List<BillLineItem> lineItems = new ArrayList<>();
        BigDecimal currentChargesTotal = BigDecimal.ZERO;

        for (MaintenanceChargeConfig charge : activeCharges) {
            if (!isChargeApplicable(charge, unit, unitTagMap)) {
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

        // Carried-forward arrears, split into principal and interest for transparency.
        ArrearsSplit carried = calculateArrearsSplit(unit.getUnitId(), request.getMonth(), request.getYear());
        BigDecimal principalArrears = carried.principal();

        // "Add: Interest" — a configurable PERCENTAGE charged each month on the OUTSTANDING
        // PRINCIPAL that was still unpaid before this month's bill (i.e. not cleared by the
        // previous due date). This is the accountant's "Add: Interest" line. The rate comes
        // from the society setting (interestRatePercent). New interest is charged on the
        // outstanding principal only (not on already-accrued interest, so interest never
        // compounds on interest). Total Interest Arrears = carried interest + this month's new
        // interest. 0% = disabled.
        BigDecimal interestRatePercent = effectiveInterestRatePercent();
        BigDecimal newInterest = BigDecimal.ZERO;
        if (interestRatePercent != null && interestRatePercent.compareTo(BigDecimal.ZERO) > 0
                && principalArrears.compareTo(BigDecimal.ZERO) > 0) {
            // Round the monthly interest to the nearest whole rupee (HALF_UP), the same
            // convention as the area-based Sinking/Repair fund charges, to match the
            // accountant's manually generated bills. Still a valid scale-2 money value.
            newInterest = money(principalArrears.multiply(interestRatePercent)
                    .divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP)
                    .setScale(0, RoundingMode.HALF_UP));
        }
        BigDecimal interestArrears = money(carried.interest().add(newInterest));

        // Legacy fields kept in sync so older readers/reports still work.
        BigDecimal previousArrears = principalArrears;
        BigDecimal interestOnArrears = interestArrears;

        // Late fee retired: the monthly percentage on outstanding principal is now charged as
        // interest (above), matching the accountant's "Add: Interest" line. Keeping a separate
        // late fee would double-charge the same base, so it stays zero.
        BigDecimal lateFee = BigDecimal.ZERO;

        // "Less: Adjustment" — optional per-unit manual credit/waiver supplied for this run.
        BigDecimal adjustment = BigDecimal.ZERO;
        if (request.getAdjustments() != null) {
            BigDecimal adj = request.getAdjustments().get(unit.getUnitId());
            if (adj != null && adj.compareTo(BigDecimal.ZERO) > 0) {
                adjustment = money(adj);
            }
        }

        // "Less: Receipt" — amount received against the PREVIOUS month's bill, shown on this
        // bill. Previous Balance is presented GROSS (before this receipt) and then the receipt
        // is subtracted, matching the accountant's layout. It does not change the Gross Total:
        // our carried arrears are already net of this receipt.
        BigDecimal lastReceiptAmount = previousBillReceipt(unit.getUnitId(),
                request.getMonth(), request.getYear());

        // Gross total (before advance credit) = current charges + arrears + interest + late fee - adjustment.
        BigDecimal grossTotal = money(currentChargesTotal
                .add(principalArrears)
                .add(interestArrears)
                .add(lateFee)
                .subtract(adjustment));
        if (grossTotal.compareTo(BigDecimal.ZERO) < 0) {
            grossTotal = BigDecimal.ZERO; // an over-large adjustment never produces a negative bill
        }

        // "Less: Advance Credit Applied" — auto-apply the unit's available advance/prepaid
        // credit (unit_advance_credits) to this bill, up to the gross total. The remaining
        // credit stays AVAILABLE and carries forward to the next bill. E.g. a unit holding a
        // 3217 credit with a 2619 bill pays 0 now and carries 598 into next month.
        BigDecimal creditApplied = applyAdvanceCredit(unit.getUnitId(), grossTotal, bill);

        BigDecimal totalAmount = money(grossTotal.subtract(creditApplied).max(BigDecimal.ZERO));

        // Set bill amounts
        bill.setAmount(currentChargesTotal);
        bill.setPrincipalArrears(principalArrears);
        bill.setInterestArrears(interestArrears);
        bill.setNewInterest(newInterest);
        bill.setLastReceiptAmount(lastReceiptAmount);
        bill.setAdjustment(adjustment);
        bill.setCreditApplied(creditApplied);
        bill.setPreviousArrears(previousArrears);
        bill.setInterestOnArrears(interestOnArrears);
        bill.setLateFee(lateFee);

        bill.setTotalAmount(totalAmount);
        bill.setBalanceAmount(totalAmount);
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setStatus(BillStatus.UNPAID);

        bill.setLineItems(lineItems);

        return bill;
    }

    /**
     * Check if a charge type applies to this unit based on conditions.
     *
     * <p>Two layers are combined with AND semantics:</p>
     * <ol>
     *   <li><b>Tag restriction</b> — if the charge has a {@code requiredTagId}, the unit
     *       must carry that tag (an admin-defined group such as solar-panel owners),
     *       otherwise the charge does not apply regardless of {@code applicableTo}.</li>
     *   <li><b>ApplicableTo</b> — the attribute-derived category (ALL/FLAT_ONLY/RENTED/
     *       parking/etc.).</li>
     * </ol>
     */
    private boolean isChargeApplicable(MaintenanceChargeConfig charge, Unit unit,
                                       Map<Long, Set<Long>> unitTagMap) {
        // Tag gate: a tag-scoped charge only applies to units carrying that tag.
        Long requiredTagId = charge.getRequiredTagId();
        if (requiredTagId != null) {
            Set<Long> tags = unitTagMap.getOrDefault(unit.getUnitId(), Collections.emptySet());
            if (!tags.contains(requiredTagId)) {
                return false;
            }
        }

        ApplicableTo applicableTo = charge.getApplicableTo();
        int twoWheelers = unit.getTwoWheelerCount() != null ? unit.getTwoWheelerCount() : 0;
        int fourWheelers = unit.getFourWheelerCount() != null ? unit.getFourWheelerCount() : 0;

        switch (applicableTo) {
            case ALL:
                return true;
            case FLAT_ONLY:
                return unit.getUnitType() == UnitType.FLAT;
            case SHOP_ONLY:
                return unit.getUnitType() == UnitType.SHOP;
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
     * Load the full unit-&gt;tagIds map in one query, for use across the bill-generation
     * unit loop. Keyed by unitId; a unit with no tags simply has no entry (callers treat
     * that as an empty set). Avoids an N+1 query per unit/charge.
     */
    private Map<Long, Set<Long>> loadUnitTagMap() {
        Map<Long, Set<Long>> map = new HashMap<>();
        unitTagAssignmentRepository.findAll().forEach(a ->
                map.computeIfAbsent(a.getUnitId(), k -> new HashSet<>()).add(a.getUnitTagId()));
        return map;
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
            // Area-based charges (e.g. Sinking Fund, Repair Fund) are rounded to the nearest
            // whole rupee to match the accountant's manually generated bills. The result is still
            // a valid scale-2 money value (e.g. 38.00), so bill totals reconcile exactly as the
            // sum of displayed line items.
            BigDecimal areaBased = charge.getRatePerSqft().multiply(areaSqft)
                    .setScale(0, RoundingMode.HALF_UP);
            return money(areaBased);
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
        return calculateArrearsSplit(unitId, currentMonth, currentYear).total();
    }

    /**
     * Principal + interest breakdown of a unit's carried-forward arrears, used to show
     * "Principal Arrears" and "Interest Arrears" transparently on the bill.
     *
     * <p>Principal = unpaid balance of prior bills' principal + opening-balance principal.
     * Interest    = unpaid balance of prior bills' interest  + opening-balance interest.
     * Allocation is INTEREST-FIRST: any amount paid against a prior bill clears its interest
     * portion before its principal, so a partially-paid prior bill carries forward its
     * remaining interest first and principal last. Opening balances are already interest-first
     * (see OpeningBalanceService), so the whole module now uses one consistent rule.</p>
     */
    private ArrearsSplit calculateArrearsSplit(Long unitId, int currentMonth, int currentYear) {
        List<MaintenanceBill> outstandingBills = billRepository.findOutstandingByUnit(unitId);

        BigDecimal principal = BigDecimal.ZERO;
        BigDecimal interest = BigDecimal.ZERO;

        for (MaintenanceBill b : outstandingBills) {
            if (!isBillBeforeMonth(b, currentMonth, currentYear)) {
                continue;
            }
            BigDecimal balance = b.getBalanceAmount() != null ? b.getBalanceAmount() : BigDecimal.ZERO;
            if (balance.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal billInterest = b.getInterestArrears() != null && b.getInterestArrears().signum() > 0
                    ? b.getInterestArrears()
                    : (b.getInterestOnArrears() != null ? b.getInterestOnArrears() : BigDecimal.ZERO);
            BigDecimal billTotal = b.getTotalAmount() != null ? b.getTotalAmount() : BigDecimal.ZERO;

            if (billTotal.compareTo(BigDecimal.ZERO) <= 0) {
                // Degenerate bill; treat the whole remaining balance as principal.
                principal = principal.add(balance);
                continue;
            }

            // INTEREST-FIRST allocation (new accountant's method): any amount paid against the
            // prior bill clears its INTEREST portion before touching principal. So the interest
            // still outstanding is (billInterest - amountPaid), floored at 0, and whatever of the
            // remaining balance is left over is carried principal. This makes interest shrink
            // first as payments arrive, matching the "deduct from interest first" rule.
            BigDecimal paidSoFar = billTotal.subtract(balance).max(BigDecimal.ZERO);
            BigDecimal interestShare = billInterest.subtract(paidSoFar).max(BigDecimal.ZERO).min(balance);
            BigDecimal principalShare = balance.subtract(interestShare).max(BigDecimal.ZERO);
            interest = interest.add(money(interestShare));
            principal = principal.add(money(principalShare));
        }

        // Add opening balance (legacy pre-go-live arrears), split into principal + interest.
        principal = principal.add(money(openingBalanceService.getOutstandingPrincipalForUnit(unitId)));
        interest = interest.add(money(openingBalanceService.getOutstandingInterestForUnit(unitId)));

        return new ArrearsSplit(money(principal), money(interest));
    }

    /** Immutable principal/interest arrears breakdown. */
    private record ArrearsSplit(BigDecimal principal, BigDecimal interest) {
        BigDecimal total() {
            return principal.add(interest);
        }
    }

    private boolean isBillBeforeMonth(MaintenanceBill bill, int month, int year) {
        if (bill.getBillYear() < year) return true;
        if (bill.getBillYear() == year && bill.getBillMonth() < month) return true;
        return false;
    }

    /**
     * Auto-apply a unit's available advance credit (prepaid/overpaid money) to a freshly
     * generated bill, up to {@code grossTotal}. Consumes AVAILABLE credits oldest-first (FIFO),
     * decrementing each credit's balance and marking it APPLIED when exhausted. Returns the
     * total credit applied (0 if the unit has none). The remaining credit stays AVAILABLE and
     * is picked up by the next month's bill.
     */
    private BigDecimal applyAdvanceCredit(Long unitId, BigDecimal grossTotal, MaintenanceBill bill) {
        if (grossTotal == null || grossTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        List<UnitAdvanceCredit> credits = advanceCreditRepository
                .findByUnit_UnitIdAndStatusOrderByReceivedDateAscAdvanceCreditIdAsc(
                        unitId, UnitAdvanceCredit.CreditStatus.AVAILABLE);
        if (credits.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal remainingToCover = grossTotal;
        BigDecimal totalApplied = BigDecimal.ZERO;

        for (UnitAdvanceCredit credit : credits) {
            if (remainingToCover.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal available = credit.getBalanceAmount() != null ? credit.getBalanceAmount() : BigDecimal.ZERO;
            if (available.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal use = available.min(remainingToCover);
            BigDecimal newApplied = (credit.getAppliedAmount() != null ? credit.getAppliedAmount() : BigDecimal.ZERO).add(use);
            BigDecimal newBalance = available.subtract(use);
            credit.setAppliedAmount(money(newApplied));
            credit.setBalanceAmount(money(newBalance));
            if (newBalance.compareTo(BigDecimal.ZERO) <= 0) {
                credit.setStatus(UnitAdvanceCredit.CreditStatus.APPLIED);
            }
            credit.setRemarks((credit.getRemarks() != null ? credit.getRemarks() + " " : "")
                    + "[Applied " + money(use) + " to bill " + Month.of(bill.getBillMonth()).name()
                    + " " + bill.getBillYear() + "]");
            advanceCreditRepository.save(credit);

            totalApplied = totalApplied.add(use);
            remainingToCover = remainingToCover.subtract(use);
        }

        if (totalApplied.compareTo(BigDecimal.ZERO) > 0) {
            log.info("Applied advance credit {} to {}/{} bill for unit {} (gross {}).",
                    money(totalApplied), bill.getBillMonth(), bill.getBillYear(), unitId, grossTotal);
        }
        return money(totalApplied);
    }

    /**
     * Reverse the advance credit a bill consumed (used before deleting a bill during
     * regeneration), restoring the applied amount back to the unit's credits so it can be
     * re-applied to the regenerated bill. Refunds onto the unit's credits (most-recently
     * applied first) and re-opens any that were marked APPLIED.
     */
    private void refundAdvanceCredit(MaintenanceBill bill) {
        BigDecimal toRefund = bill.getCreditApplied() != null ? bill.getCreditApplied() : BigDecimal.ZERO;
        if (toRefund.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Long unitId = bill.getUnit().getUnitId();
        // Reverse-FIFO: refund the credits that were consumed last, first.
        List<UnitAdvanceCredit> credits = advanceCreditRepository
                .findByUnit_UnitIdOrderByReceivedDateDescAdvanceCreditIdDesc(unitId);
        for (UnitAdvanceCredit credit : credits) {
            if (toRefund.compareTo(BigDecimal.ZERO) <= 0) break;
            if (credit.getStatus() == UnitAdvanceCredit.CreditStatus.REVERSED) continue;
            BigDecimal applied = credit.getAppliedAmount() != null ? credit.getAppliedAmount() : BigDecimal.ZERO;
            if (applied.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal giveBack = applied.min(toRefund);
            credit.setAppliedAmount(money(applied.subtract(giveBack)));
            credit.setBalanceAmount(money((credit.getBalanceAmount() != null ? credit.getBalanceAmount() : BigDecimal.ZERO).add(giveBack)));
            credit.setStatus(UnitAdvanceCredit.CreditStatus.AVAILABLE);
            advanceCreditRepository.save(credit);
            toRefund = toRefund.subtract(giveBack);
        }
        log.info("Refunded advance credit {} from regenerated bill {} (unit {}).",
                money(bill.getCreditApplied()), bill.getBillId(), unitId);
    }

    /**
     * Amount received against the PREVIOUS month's bill for this unit — the "Less: Receipt"
     * line. Returns the previous bill's paidAmount (0 if there was no prior bill or no payment).
     */
    private BigDecimal previousBillReceipt(Long unitId, int currentMonth, int currentYear) {
        int prevMonth = currentMonth - 1;
        int prevYear = currentYear;
        if (prevMonth < 1) {
            prevMonth = 12;
            prevYear--;
        }
        return billRepository.findByUnit_UnitIdAndBillMonthAndBillYear(unitId, prevMonth, prevYear)
                .map(b -> b.getPaidAmount() != null ? b.getPaidAmount() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
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
        BigDecimal total = (billOutstanding != null ? billOutstanding : BigDecimal.ZERO);

        // The opening balance (pre-go-live arrears) is FOLDED INTO each generated bill's
        // arrears by calculateArrearsSplit, so it is already reflected in the bills'
        // balance_amount above. Only add it separately when the unit has NO bills yet
        // (otherwise it would be double-counted — and any adjustment that cleared the arrears
        // on the bill would be undone in this total). See D-1404: bill payable 2595, but the
        // old code reported 2595 + 37995 = 40590.
        if (!billRepository.existsByUnit_UnitId(unitId)) {
            BigDecimal openingBalance = openingBalanceRepository.getTotalOpeningBalanceByUnit(unitId);
            total = total.add(openingBalance != null ? openingBalance : BigDecimal.ZERO);
        }
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

    /**
     * Seed an OPENING advance credit for a unit — prepaid money an owner paid BEFORE go-live
     * (the mirror of an opening balance, but a credit). It is stored as an AVAILABLE
     * {@link UnitAdvanceCredit} with no source payment, so the next generated bill auto-applies
     * it (and carries the remainder forward). Use for pre-system advances only; credits from
     * in-system overpayments are created automatically by the payment flow.
     */
    @Transactional
    public AdvanceCreditDTO createOpeningAdvanceCredit(Long unitId, BigDecimal amount,
                                                       LocalDate receivedDate, String remarks,
                                                       String enteredBy) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Opening credit amount must be greater than zero");
        }
        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "unitId", unitId));

        BigDecimal amt = money(amount);
        UnitAdvanceCredit credit = UnitAdvanceCredit.builder()
                .unit(unit)
                .amount(amt)
                .appliedAmount(BigDecimal.ZERO)
                .balanceAmount(amt)
                .receivedDate(receivedDate != null ? receivedDate : LocalDate.now())
                .paymentMode("OPENING")
                .payerName(unit.getOwnerNames())
                .remarks("Opening advance credit (prepaid before go-live)"
                        + (remarks != null && !remarks.isBlank() ? " - " + remarks : "")
                        + " [entered by " + enteredBy + "]")
                .status(UnitAdvanceCredit.CreditStatus.AVAILABLE)
                .build();
        credit = advanceCreditRepository.save(credit);
        log.info("Opening advance credit {} seeded for unit {} by {}.", amt, unit.getUnitNumber(), enteredBy);
        return mapToAdvanceCreditDTO(credit);
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
        dto.setPrincipalArrears(bill.getPrincipalArrears());
        dto.setInterestArrears(bill.getInterestArrears());
        dto.setNewInterest(bill.getNewInterest());
        dto.setLastReceiptAmount(bill.getLastReceiptAmount());
        dto.setCreditApplied(bill.getCreditApplied());
        dto.setAdjustment(bill.getAdjustment());
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
