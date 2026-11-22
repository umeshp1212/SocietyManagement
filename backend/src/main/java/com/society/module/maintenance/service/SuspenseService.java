package com.society.module.maintenance.service;

import com.society.common.PagedResponse;
import com.society.enums.PaymentMode;
import com.society.exception.BusinessException;
import com.society.exception.ResourceNotFoundException;
import com.society.module.maintenance.dto.PaymentDTO;
import com.society.module.maintenance.dto.RecordOfflinePaymentRequest;
import com.society.module.maintenance.dto.SuspenseAuditDTO;
import com.society.module.maintenance.dto.SuspenseEntryDTO;
import com.society.module.maintenance.entity.MaintenanceBill;
import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.maintenance.entity.UnitAdvanceCredit;
import com.society.module.maintenance.repository.MaintenanceBillRepository;
import com.society.module.maintenance.repository.UnitAdvanceCreditRepository;
import com.society.module.settings.service.SocietySettingsService;
import com.society.module.maintenance.entity.SuspenseAuditTrail;
import com.society.module.maintenance.entity.SuspenseAuditTrail.SuspenseAction;
import com.society.module.maintenance.entity.SuspenseEntry;
import com.society.module.maintenance.entity.SuspenseEntry.SuspenseStatus;
import com.society.module.maintenance.repository.SuspenseAuditTrailRepository;
import com.society.module.maintenance.repository.SuspenseEntryRepository;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SuspenseService {

    private final SuspenseEntryRepository suspenseEntryRepository;
    private final SuspenseAuditTrailRepository auditTrailRepository;
    private final UnitRepository unitRepository;
    private final OpeningBalanceService openingBalanceService;
    private final MaintenanceBillRepository billRepository;
    private final UnitAdvanceCreditRepository advanceCreditRepository;
    private final SocietySettingsService societySettingsService;

    /** Lazy to avoid a startup dependency cycle with the bill/payment services. */
    @Autowired
    @Lazy
    private MaintenanceBillService billService;

    // ==================== CREATE ====================

    @Transactional
    public SuspenseEntryDTO createSuspenseEntry(BigDecimal amount, LocalDate receivedDate,
                                                 String paymentMode, String referenceNumber,
                                                 String description, String createdBy) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Amount must be positive");
        }

        SuspenseEntry entry = SuspenseEntry.builder()
                .amount(amount)
                .receivedDate(receivedDate != null ? receivedDate : LocalDate.now())
                .paymentMode(paymentMode != null ? PaymentMode.valueOf(paymentMode) : null)
                .referenceNumber(referenceNumber)
                .description(description)
                .status(SuspenseStatus.UNASSIGNED)
                .build();

        entry = suspenseEntryRepository.save(entry);

        // Audit trail
        createAudit(entry, SuspenseAction.CREATED, null, null, createdBy,
                "Suspense entry created: " + amount + " via " + (paymentMode != null ? paymentMode : "Unknown"));

        log.info("Suspense entry created: ID={}, Amount={}, Ref={}", entry.getSuspenseId(), amount, referenceNumber);
        return mapToDTO(entry);
    }

    // ==================== ASSIGN ====================

    @Transactional
    public SuspenseEntryDTO assignToUnit(Long suspenseId, Long unitId, String remarks,
                                          boolean applyToOpeningBalance, String assignedBy) {
        return assignToUnit(suspenseId, unitId, remarks, applyToOpeningBalance, null, assignedBy);
    }

    /**
     * @param interestOutstandingOnReceipt optional: old interest the unit owed when the money was
     *        received (from the accountant's bill). A payment clears interest first, so only
     *        (amount - this) stops earning interest and is refunded.
     */
    @Transactional
    public SuspenseEntryDTO assignToUnit(Long suspenseId, Long unitId, String remarks,
                                          boolean applyToOpeningBalance,
                                          BigDecimal interestOutstandingOnReceipt, String assignedBy) {
        SuspenseEntry entry = suspenseEntryRepository.findById(suspenseId)
                .orElseThrow(() -> new ResourceNotFoundException("SuspenseEntry", "suspenseId", suspenseId));

        if (entry.getStatus() == SuspenseStatus.ASSIGNED) {
            throw new BusinessException("This entry is already assigned to Unit " +
                    entry.getAssignedToUnit().getUnitNumber() + ". Reverse it first before reassigning.");
        }

        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "unitId", unitId));

        entry.setStatus(SuspenseStatus.ASSIGNED);
        entry.setAssignedToUnit(unit);
        entry.setAssignedBy(assignedBy);
        entry.setAssignedOn(LocalDateTime.now());
        entry.setAssignmentRemarks(remarks);
        entry.setApplyToOpeningBalance(applyToOpeningBalance);

        entry = suspenseEntryRepository.save(entry);

        String outcome = "";
        if (billRepository.existsByUnit_UnitId(unitId)) {
            // The unit already has bills (its opening balance is folded into them), so the money
            // must reduce what the owner owes ON THE BILLS: record it as a real payment, dated
            // when the money was actually received. Also refund the interest that kept accruing
            // because the payment was sitting in suspense.
            entry.setApplyToOpeningBalance(false);
            outcome = applyToBills(entry, unit, interestOutstandingOnReceipt);
            entry = suspenseEntryRepository.save(entry);
        } else if (applyToOpeningBalance) {
            // No bills yet: reduce the unit's legacy arrears (opening balance) as before.
            openingBalanceService.recordPaymentAgainstOpeningBalance(unitId, entry.getAmount());
            log.info("Suspense {} applied to opening balance of unit {}", suspenseId, unit.getUnitNumber());
        }

        // Audit trail
        String auditText = (remarks != null ? remarks : "Assigned to " + unit.getUnitNumber()) + outcome;
        createAudit(entry, SuspenseAction.ASSIGNED, unitId, unit.getUnitNumber(), assignedBy, auditText);

        log.info("Suspense entry {} assigned to unit {}", suspenseId, unit.getUnitNumber());
        return mapToDTO(entry);
    }

    /**
     * Apply an assigned suspense entry to the unit's bills as real payment(s), oldest bill
     * first (any leftover beyond the dues becomes advance credit via the normal payment flow),
     * then create the interest-refund credit. Returns a short text for the audit trail.
     */
    private String applyToBills(SuspenseEntry entry, Unit unit, BigDecimal interestOutstandingOnReceipt) {
        Long unitId = unit.getUnitId();
        List<MaintenanceBill> allBills = billRepository.findByUnit_UnitIdOrderByBillYearDescBillMonthDesc(unitId);
        List<MaintenanceBill> targets = new ArrayList<>(billRepository.findOutstandingByUnit(unitId));
        if (targets.isEmpty()) {
            // Everything is already paid: record against the latest bill; the whole amount
            // then becomes advance credit for the unit's next bill.
            targets.add(allBills.get(0));
        }

        // Interest refund is based on the latest bill's carried principal, computed before payments.
        BigDecimal refund = computeInterestRefund(entry, allBills, interestOutstandingOnReceipt);

        String mode = toBillPaymentMode(entry);
        BigDecimal remaining = entry.getAmount();
        List<Long> paymentIds = new ArrayList<>();
        for (int i = 0; i < targets.size() && remaining.compareTo(BigDecimal.ZERO) > 0; i++) {
            MaintenanceBill bill = targets.get(i);
            boolean last = i == targets.size() - 1;
            BigDecimal balance = bill.getBalanceAmount() != null ? bill.getBalanceAmount() : BigDecimal.ZERO;
            BigDecimal pay = last ? remaining : remaining.min(balance);
            if (pay.compareTo(BigDecimal.ZERO) <= 0) continue;

            RecordOfflinePaymentRequest req = RecordOfflinePaymentRequest.builder()
                    .billId(bill.getBillId())
                    .amount(pay)
                    .paymentDate(entry.getReceivedDate())
                    .paymentMode(mode)
                    .transactionId(entry.getReferenceNumber())
                    .payerName(unit.getOwnerNames())
                    .payerType("OWNER")
                    .remarks("Assigned from suspense entry #" + entry.getSuspenseId()
                            + " (money received " + entry.getReceivedDate() + ")")
                    .build();
            PaymentDTO paid = billService.recordOfflinePayment(req);
            paymentIds.add(paid.getPaymentId());
            remaining = remaining.subtract(pay);
        }
        entry.setAppliedPaymentIds(paymentIds.stream().map(String::valueOf).collect(Collectors.joining(",")));

        String text = " | Recorded " + paymentIds.size() + " payment(s) of " + entry.getAmount() + " on bills";

        // Take the interest off the LATEST bill right away (so the member sees the correct
        // payable now). Only what the bill can absorb is removed (never below its unpaid balance
        // or the interest it carries); any remainder becomes a credit for the next bill.
        if (refund.compareTo(BigDecimal.ZERO) > 0) {
            MaintenanceBill latest = billRepository.findById(allBills.get(0).getBillId()).orElse(null);
            if (latest != null) {
                BigDecimal balance = latest.getBalanceAmount() != null ? latest.getBalanceAmount() : BigDecimal.ZERO;
                BigDecimal interestOnBill = latest.getInterestArrears() != null ? latest.getInterestArrears() : BigDecimal.ZERO;
                BigDecimal onBill = refund.min(balance).min(interestOnBill).max(BigDecimal.ZERO);
                if (onBill.compareTo(BigDecimal.ZERO) > 0) {
                    reduceBillInterest(latest, onBill);
                    entry.setInterestRefundBillId(latest.getBillId());
                    entry.setInterestRefundBillAmount(onBill);
                    refund = refund.subtract(onBill);
                    text += " | Interest " + onBill + " removed from " + latest.getBillMonth() + "/" + latest.getBillYear() + " bill";
                }
            }
        }

        if (refund.compareTo(BigDecimal.ZERO) > 0) {
            UnitAdvanceCredit credit = UnitAdvanceCredit.builder()
                    .unit(unit)
                    .amount(refund)
                    .appliedAmount(BigDecimal.ZERO)
                    .balanceAmount(refund)
                    .receivedDate(LocalDate.now())
                    .paymentMode("INTEREST_REFUND")
                    .payerName(unit.getOwnerNames())
                    .remarks("Interest refund: " + entry.getAmount() + " was received on " + entry.getReceivedDate()
                            + " but sat in suspense (entry #" + entry.getSuspenseId() + ")")
                    .status(UnitAdvanceCredit.CreditStatus.AVAILABLE)
                    .build();
            credit = advanceCreditRepository.save(credit);
            entry.setInterestRefundCreditId(credit.getAdvanceCreditId());
            entry.setInterestRefundAmount(refund);
            text += " | Interest refund credit " + refund + " (applies to the next bill)";
        }
        return text;
    }

    /** Remove {@code amount} of interest from a bill: interest fields, total and balance move together. */
    private void reduceBillInterest(MaintenanceBill bill, BigDecimal amount) {
        BigDecimal interest = nz(bill.getInterestArrears());
        bill.setInterestArrears(interest.subtract(amount.min(interest)));
        BigDecimal legacy = nz(bill.getInterestOnArrears());
        bill.setInterestOnArrears(legacy.subtract(amount.min(legacy)));
        BigDecimal newInterest = nz(bill.getNewInterest());
        bill.setNewInterest(newInterest.subtract(amount.min(newInterest)));
        bill.setTotalAmount(nz(bill.getTotalAmount()).subtract(amount).max(BigDecimal.ZERO));
        BigDecimal newBalance = nz(bill.getBalanceAmount()).subtract(amount).max(BigDecimal.ZERO);
        bill.setBalanceAmount(newBalance);
        if (newBalance.compareTo(BigDecimal.ZERO) <= 0) {
            bill.setStatus(MaintenanceBill.BillStatus.PAID);
        }
        billRepository.save(bill);
    }

    /** Put back interest removed by {@link #reduceBillInterest} (used when an assignment is reversed). */
    private void restoreBillInterest(MaintenanceBill bill, BigDecimal amount) {
        bill.setInterestArrears(nz(bill.getInterestArrears()).add(amount));
        bill.setInterestOnArrears(nz(bill.getInterestOnArrears()).add(amount));
        bill.setNewInterest(nz(bill.getNewInterest()).add(amount));
        bill.setTotalAmount(nz(bill.getTotalAmount()).add(amount));
        bill.setBalanceAmount(nz(bill.getTotalAmount()).subtract(nz(bill.getPaidAmount())).max(BigDecimal.ZERO));
        if (bill.getStatus() == MaintenanceBill.BillStatus.PAID
                && bill.getBalanceAmount().compareTo(BigDecimal.ZERO) > 0) {
            bill.setStatus(nz(bill.getPaidAmount()).compareTo(BigDecimal.ZERO) > 0
                    ? MaintenanceBill.BillStatus.PARTIALLY_PAID : MaintenanceBill.BillStatus.UNPAID);
        }
        billRepository.save(bill);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /**
     * Interest charged because the money sat in suspense: for every month after the month the
     * money was received, up to the latest generated bill, the monthly interest on the amount
     * (capped at the principal that interest was actually charged on).
     */
    private BigDecimal computeInterestRefund(SuspenseEntry entry, List<MaintenanceBill> billsNewestFirst,
                                             BigDecimal interestOutstandingOnReceipt) {
        BigDecimal rate = societySettingsService.getSettings().getInterestRatePercent();
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0 || billsNewestFirst.isEmpty()) {
            return BigDecimal.ZERO;
        }
        MaintenanceBill latest = billsNewestFirst.get(0);
        long months = ChronoUnit.MONTHS.between(
                YearMonth.from(entry.getReceivedDate()),
                YearMonth.of(latest.getBillYear(), latest.getBillMonth()));
        if (months <= 0) return BigDecimal.ZERO;

        BigDecimal principal = latest.getPrincipalArrears() != null ? latest.getPrincipalArrears() : BigDecimal.ZERO;
        // Payments clear old interest first; only the rest reduces the principal that earns interest.
        BigDecimal clearedInterest = interestOutstandingOnReceipt != null
                ? interestOutstandingOnReceipt.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal base = entry.getAmount().subtract(clearedInterest).min(principal);
        if (base.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        BigDecimal monthly = base.multiply(rate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .setScale(0, RoundingMode.HALF_UP);
        return monthly.multiply(BigDecimal.valueOf(months)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Map the suspense payment mode to a valid bill payment mode (ONLINE/unknown -> bank transfer). */
    private String toBillPaymentMode(SuspenseEntry entry) {
        if (entry.getPaymentMode() != null) {
            try {
                return MaintenancePayment.PaymentMode.valueOf(entry.getPaymentMode().name()).name();
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        return MaintenancePayment.PaymentMode.BANK_TRANSFER.name();
    }

    /** Undo what applyToBills did: reverse the payments and void the unused interest-refund credit. */
    private void undoBillApplication(SuspenseEntry entry, String reason) {
        String ids = entry.getAppliedPaymentIds();
        if (ids == null || ids.isBlank()) {
            return;
        }
        UnitAdvanceCredit refundCredit = entry.getInterestRefundCreditId() != null
                ? advanceCreditRepository.findById(entry.getInterestRefundCreditId()).orElse(null) : null;
        if (refundCredit != null && refundCredit.getAppliedAmount() != null
                && refundCredit.getAppliedAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("The interest refund from this assignment has already been applied "
                    + "to a bill, so it can't be reversed automatically. Adjust that bill first.");
        }
        // Put the interest back on the bill first, so reversing the payments below recomputes
        // its balance from the restored total.
        if (entry.getInterestRefundBillId() != null && entry.getInterestRefundBillAmount() != null) {
            billRepository.findById(entry.getInterestRefundBillId())
                    .ifPresent(b -> restoreBillInterest(b, entry.getInterestRefundBillAmount()));
        }
        for (String id : ids.split(",")) {
            if (!id.isBlank()) {
                billService.reversePayment(Long.valueOf(id.trim()), "Suspense assignment reversed: " + reason);
            }
        }
        if (refundCredit != null) {
            refundCredit.setStatus(UnitAdvanceCredit.CreditStatus.REVERSED);
            refundCredit.setBalanceAmount(BigDecimal.ZERO);
            refundCredit.setRemarks((refundCredit.getRemarks() != null ? refundCredit.getRemarks() + " " : "")
                    + "[Voided: suspense assignment reversed]");
            advanceCreditRepository.save(refundCredit);
        }
        entry.setAppliedPaymentIds(null);
        entry.setInterestRefundCreditId(null);
        entry.setInterestRefundAmount(null);
        entry.setInterestRefundBillId(null);
        entry.setInterestRefundBillAmount(null);
    }

    // ==================== REVERSE ====================

    @Transactional
    public SuspenseEntryDTO reverseAssignment(Long suspenseId, String reason, String reversedBy) {
        SuspenseEntry entry = suspenseEntryRepository.findById(suspenseId)
                .orElseThrow(() -> new ResourceNotFoundException("SuspenseEntry", "suspenseId", suspenseId));

        if (entry.getStatus() != SuspenseStatus.ASSIGNED) {
            throw new BusinessException("Only ASSIGNED entries can be reversed");
        }

        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Reason is mandatory for reversal");
        }

        Long previousUnitId = entry.getAssignedToUnit().getUnitId();
        String previousUnitNumber = entry.getAssignedToUnit().getUnitNumber();

        // If it was applied to opening balance, reverse that too
        if (Boolean.TRUE.equals(entry.getApplyToOpeningBalance())) {
            openingBalanceService.reversePaymentFromOpeningBalance(previousUnitId, entry.getAmount());
            log.info("Reversed opening balance payment for unit {} amount {}", previousUnitNumber, entry.getAmount());
        }

        // If it was applied to bills as real payments, undo those and the interest refund.
        undoBillApplication(entry, reason);

        entry.setStatus(SuspenseStatus.REVERSED);
        entry.setAssignedToUnit(null);
        entry.setAssignedBy(null);
        entry.setAssignedOn(null);
        entry.setAssignmentRemarks(null);
        entry.setApplyToOpeningBalance(false);

        entry = suspenseEntryRepository.save(entry);

        // Audit trail
        createAudit(entry, SuspenseAction.REVERSED, previousUnitId, previousUnitNumber, reversedBy, reason);

        log.info("Suspense entry {} reversed from unit {}. Reason: {}", suspenseId, previousUnitNumber, reason);
        return mapToDTO(entry);
    }

    // ==================== REASSIGN (shortcut: reverse + assign) ====================

    @Transactional
    public SuspenseEntryDTO reassign(Long suspenseId, Long newUnitId, String reason,
                                      String remarks, boolean applyToOpeningBalance, String performedBy) {
        // First reverse
        SuspenseEntry entry = suspenseEntryRepository.findById(suspenseId)
                .orElseThrow(() -> new ResourceNotFoundException("SuspenseEntry", "suspenseId", suspenseId));

        if (entry.getStatus() == SuspenseStatus.ASSIGNED) {
            reverseAssignment(suspenseId, reason, performedBy);
        }

        // Reset status to UNASSIGNED so assign works
        entry = suspenseEntryRepository.findById(suspenseId).orElseThrow();
        entry.setStatus(SuspenseStatus.UNASSIGNED);
        suspenseEntryRepository.save(entry);

        // Then assign to new unit
        return assignToUnit(suspenseId, newUnitId, remarks, applyToOpeningBalance, performedBy);
    }

    // ==================== QUERIES ====================

    public PagedResponse<SuspenseEntryDTO> getAllEntries(int page, int size, String status, String search) {
        Pageable pageable = PageRequest.of(page, size);
        Page<SuspenseEntry> entryPage;

        if (search != null && !search.isBlank()) {
            entryPage = suspenseEntryRepository.search(search, pageable);
        } else if (status != null && !status.isBlank()) {
            entryPage = suspenseEntryRepository.findByStatusOrderByReceivedDateDesc(
                    SuspenseStatus.valueOf(status), pageable);
        } else {
            entryPage = suspenseEntryRepository.findAllByOrderByReceivedDateDesc(pageable);
        }

        List<SuspenseEntryDTO> content = entryPage.getContent().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        return PagedResponse.<SuspenseEntryDTO>builder()
                .content(content)
                .page(entryPage.getNumber())
                .size(entryPage.getSize())
                .totalElements(entryPage.getTotalElements())
                .totalPages(entryPage.getTotalPages())
                .last(entryPage.isLast())
                .build();
    }

    public SuspenseEntryDTO getById(Long suspenseId) {
        SuspenseEntry entry = suspenseEntryRepository.findById(suspenseId)
                .orElseThrow(() -> new ResourceNotFoundException("SuspenseEntry", "suspenseId", suspenseId));

        SuspenseEntryDTO dto = mapToDTO(entry);
        // Include audit trail for detail view
        List<SuspenseAuditDTO> audits = auditTrailRepository
                .findBySuspenseEntry_SuspenseIdOrderByPerformedOnDesc(suspenseId)
                .stream().map(this::mapToAuditDTO).collect(Collectors.toList());
        dto.setAuditTrail(audits);
        return dto;
    }

    public List<SuspenseEntryDTO> getUnassignedEntries() {
        return suspenseEntryRepository.findByStatusOrderByReceivedDateDesc(SuspenseStatus.UNASSIGNED)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public List<SuspenseEntryDTO> getEntriesAssignedToUnit(Long unitId) {
        return suspenseEntryRepository.findByAssignedToUnit_UnitIdOrderByAssignedOnDesc(unitId)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public Map<String, Object> getSummary() {
        long unassignedCount = suspenseEntryRepository.countByStatus(SuspenseStatus.UNASSIGNED);
        long assignedCount = suspenseEntryRepository.countByStatus(SuspenseStatus.ASSIGNED);
        long reversedCount = suspenseEntryRepository.countByStatus(SuspenseStatus.REVERSED);
        BigDecimal unassignedAmount = suspenseEntryRepository.getTotalAmountByStatus(SuspenseStatus.UNASSIGNED);
        BigDecimal assignedAmount = suspenseEntryRepository.getTotalAmountByStatus(SuspenseStatus.ASSIGNED);
        long totalEntries = suspenseEntryRepository.count();

        return Map.of(
                "totalEntries", totalEntries,
                "unassignedCount", unassignedCount,
                "unassignedAmount", unassignedAmount != null ? unassignedAmount : BigDecimal.ZERO,
                "assignedCount", assignedCount,
                "assignedAmount", assignedAmount != null ? assignedAmount : BigDecimal.ZERO,
                "reversedCount", reversedCount
        );
    }

    // ==================== HELPERS ====================

    private void createAudit(SuspenseEntry entry, SuspenseAction action, Long unitId,
                              String unitNumber, String performedBy, String reason) {
        SuspenseAuditTrail audit = SuspenseAuditTrail.builder()
                .suspenseEntry(entry)
                .action(action)
                .unitId(unitId)
                .unitNumber(unitNumber)
                .performedBy(performedBy)
                .performedOn(LocalDateTime.now())
                .reason(reason)
                .build();
        auditTrailRepository.save(audit);
    }

    private SuspenseEntryDTO mapToDTO(SuspenseEntry entry) {
        return SuspenseEntryDTO.builder()
                .suspenseId(entry.getSuspenseId())
                .amount(entry.getAmount())
                .receivedDate(entry.getReceivedDate())
                .paymentMode(entry.getPaymentMode() != null ? entry.getPaymentMode().name() : null)
                .referenceNumber(entry.getReferenceNumber())
                .description(entry.getDescription())
                .status(entry.getStatus().name())
                .assignedToUnitId(entry.getAssignedToUnit() != null ? entry.getAssignedToUnit().getUnitId() : null)
                .assignedToUnitNumber(entry.getAssignedToUnit() != null ? entry.getAssignedToUnit().getUnitNumber() : null)
                .assignedToOwnerName(entry.getAssignedToUnit() != null ? entry.getAssignedToUnit().getOwnerNames() : null)
                .assignedBy(entry.getAssignedBy())
                .assignedOn(entry.getAssignedOn())
                .assignmentRemarks(entry.getAssignmentRemarks())
                .applyToOpeningBalance(entry.getApplyToOpeningBalance())
                .createdOn(entry.getCreatedOn())
                .createdBy(entry.getCreatedBy())
                .auditTrail(Collections.emptyList())
                .build();
    }

    private SuspenseAuditDTO mapToAuditDTO(SuspenseAuditTrail audit) {
        return SuspenseAuditDTO.builder()
                .auditId(audit.getAuditId())
                .action(audit.getAction().name())
                .unitId(audit.getUnitId())
                .unitNumber(audit.getUnitNumber())
                .performedBy(audit.getPerformedBy())
                .performedOn(audit.getPerformedOn())
                .reason(audit.getReason())
                .build();
    }
}
