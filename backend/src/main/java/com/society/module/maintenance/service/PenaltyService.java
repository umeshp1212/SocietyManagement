package com.society.module.maintenance.service;

import com.society.exception.BusinessException;
import com.society.exception.ResourceNotFoundException;
import com.society.module.maintenance.dto.PenaltyDTO;
import com.society.module.maintenance.entity.Penalty;
import com.society.module.maintenance.entity.Penalty.PenaltyCategory;
import com.society.module.maintenance.entity.Penalty.PenaltyStatus;
import com.society.module.maintenance.repository.PenaltyRepository;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.repository.UnitRepository;
import com.society.module.maintenance.entity.BillLineItem;
import com.society.module.maintenance.entity.MaintenanceBill;
import com.society.module.maintenance.entity.MaintenanceBill.BillStatus;
import com.society.module.maintenance.entity.MaintenanceLedger;
import com.society.module.maintenance.entity.UnitAdvanceCredit;
import com.society.module.maintenance.repository.MaintenanceBillRepository;
import com.society.module.maintenance.repository.UnitAdvanceCreditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PenaltyService {

    private final PenaltyRepository penaltyRepository;
    private final UnitRepository unitRepository;
    private final MaintenanceBillRepository billRepository;
    private final UnitAdvanceCreditRepository advanceCreditRepository;
    private final MaintenanceLedgerService ledgerService;

    @Transactional
    public PenaltyDTO addPenalty(PenaltyDTO request) {
        Unit unit = unitRepository.findById(request.getUnitId())
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "unitId", request.getUnitId()));

        Penalty penalty = Penalty.builder()
                .unit(unit)
                .amount(request.getAmount())
                .reason(request.getReason())
                .category(PenaltyCategory.valueOf(request.getCategory()))
                .billMonth(request.getBillMonth())
                .billYear(request.getBillYear())
                .status(PenaltyStatus.PENDING)
                .imposedBy(request.getImposedBy() != null ? request.getImposedBy() : "SYSTEM")
                .build();

        penalty = penaltyRepository.save(penalty);
        return mapToDTO(penalty);
    }

    public List<PenaltyDTO> getPenaltiesByUnit(Long unitId) {
        return penaltyRepository.findByUnit_UnitIdOrderByCreatedOnDesc(unitId)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    /** Full penalty history (all statuses), newest first. */
    public List<PenaltyDTO> getAllPenalties() {
        return penaltyRepository.findAllByOrderByCreatedOnDesc()
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public List<PenaltyDTO> getPendingPenalties() {
        return penaltyRepository.findByStatusOrderByCreatedOnDesc(PenaltyStatus.PENDING)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public List<PenaltyDTO> getPenaltiesByMonthYear(int month, int year) {
        return penaltyRepository.findByBillMonthAndBillYearAndStatus(month, year, PenaltyStatus.PENDING)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Transactional
    public PenaltyDTO cancelPenalty(Long penaltyId) {
        Penalty penalty = penaltyRepository.findById(penaltyId)
                .orElseThrow(() -> new ResourceNotFoundException("Penalty", "penaltyId", penaltyId));

        if (penalty.getStatus() == PenaltyStatus.CANCELLED) {
            // Already cancelled, but its line item may still be on the bill (e.g. it was
            // cancelled before this fix, or regeneration was skipped). Clean that up.
            if (!removePenaltyFromBill(penalty)) {
                throw new BusinessException("Penalty is already cancelled and is not on any bill");
            }
            return mapToDTO(penalty);
        }

        boolean wasBilled = penalty.getStatus() == PenaltyStatus.BILLED;
        penalty.setStatus(PenaltyStatus.CANCELLED);
        penalty = penaltyRepository.save(penalty);

        if (wasBilled) {
            removePenaltyFromBill(penalty);
        }
        return mapToDTO(penalty);
    }

    /**
     * A BILLED penalty is already a line item on its month's bill. Cancelling it must take it
     * off that bill directly (works for UNPAID, PARTIALLY_PAID and PAID bills, without
     * regenerating or reversing payments): remove the line item, reduce current charges /
     * total / balance, re-derive status, and turn any resulting overpayment into the unit's
     * advance credit so it applies to the next bill.
     */
    private boolean removePenaltyFromBill(Penalty penalty) {
        Long unitId = penalty.getUnit().getUnitId();
        MaintenanceBill bill = billRepository.findByUnit_UnitIdAndBillMonthAndBillYear(
                unitId, penalty.getBillMonth(), penalty.getBillYear()).orElse(null);
        if (bill == null) {
            log.warn("Cancelled penalty {} but no bill found for unit {} {}/{}",
                    penalty.getPenaltyId(), unitId, penalty.getBillMonth(), penalty.getBillYear());
            return false;
        }

        String code = "PENALTY_" + penalty.getCategory().name();
        List<BillLineItem> matching = bill.getLineItems().stream()
                .filter(li -> code.equals(li.getChargeCode())
                        && li.getAmount() != null
                        && li.getAmount().compareTo(penalty.getAmount()) == 0)
                .collect(Collectors.toList());
        // Other still-BILLED penalties of the same category/amount legitimately own a matching
        // line item each; only remove one if there is a surplus line item for this penalty.
        long stillBilled = penaltyRepository.findByUnit_UnitIdAndBillMonthAndBillYearAndStatus(
                        unitId, penalty.getBillMonth(), penalty.getBillYear(), PenaltyStatus.BILLED)
                .stream()
                .filter(p -> !p.getPenaltyId().equals(penalty.getPenaltyId())
                        && p.getCategory() == penalty.getCategory()
                        && p.getAmount().compareTo(penalty.getAmount()) == 0)
                .count();
        if (matching.size() <= stillBilled) {
            log.warn("Cancelled penalty {}: bill {} has no matching {} line item to remove",
                    penalty.getPenaltyId(), bill.getBillId(), code);
            return false;
        }
        BillLineItem item = matching.get(0);

        BigDecimal amt = item.getAmount();
        bill.getLineItems().remove(item); // orphanRemoval deletes the row

        BigDecimal balanceBefore = nz(bill.getBalanceAmount());
        BigDecimal paid = nz(bill.getPaidAmount());
        BigDecimal oldTotal = nz(bill.getTotalAmount());
        BigDecimal newTotal = oldTotal.subtract(amt).max(BigDecimal.ZERO);

        bill.setAmount(nz(bill.getAmount()).subtract(amt).max(BigDecimal.ZERO));
        bill.setTotalAmount(newTotal);

        BigDecimal surplus = paid.subtract(newTotal);
        if (surplus.compareTo(BigDecimal.ZERO) > 0) {
            // Owner already paid the penalty: cap paid at the new total, credit the excess.
            bill.setPaidAmount(newTotal);
            UnitAdvanceCredit credit = UnitAdvanceCredit.builder()
                    .unit(bill.getUnit())
                    .amount(surplus)
                    .appliedAmount(BigDecimal.ZERO)
                    .balanceAmount(surplus)
                    .receivedDate(LocalDate.now())
                    .paymentMode("PENALTY_REFUND")
                    .payerName(bill.getUnit().getOwnerNames())
                    .remarks("Refund of cancelled penalty #" + penalty.getPenaltyId()
                            + " already paid on bill " + bill.getBillMonth() + "/" + bill.getBillYear())
                    .status(UnitAdvanceCredit.CreditStatus.AVAILABLE)
                    .build();
            advanceCreditRepository.save(credit);
            paid = newTotal;
        }

        BigDecimal newBalance = newTotal.subtract(paid).max(BigDecimal.ZERO);
        bill.setBalanceAmount(newBalance);
        if (newBalance.compareTo(BigDecimal.ZERO) <= 0) {
            bill.setStatus(BillStatus.PAID);
        } else if (paid.compareTo(BigDecimal.ZERO) > 0) {
            bill.setStatus(BillStatus.PARTIALLY_PAID);
        } else {
            bill.setStatus(BillStatus.UNPAID);
        }
        billRepository.save(bill);

        ledgerService.record(bill, null, MaintenanceLedger.EntryType.BILL_GENERATED, amt.negate(),
                balanceBefore, newBalance, MaintenanceLedger.Source.ADMIN, null,
                "Penalty cancelled: -" + amt + " (" + penalty.getReason() + ")");
        log.info("Removed cancelled penalty {} ({}) from bill {}; new total {}, balance {}, status {}",
                penalty.getPenaltyId(), amt, bill.getBillId(), newTotal, newBalance, bill.getStatus());
        return true;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private PenaltyDTO mapToDTO(Penalty penalty) {
        return PenaltyDTO.builder()
                .penaltyId(penalty.getPenaltyId())
                .unitId(penalty.getUnit().getUnitId())
                .unitNumber(penalty.getUnit().getUnitNumber())
                .amount(penalty.getAmount())
                .reason(penalty.getReason())
                .category(penalty.getCategory().name())
                .billMonth(penalty.getBillMonth())
                .billYear(penalty.getBillYear())
                .status(penalty.getStatus().name())
                .imposedBy(penalty.getImposedBy())
                .createdOn(penalty.getCreatedOn())
                .build();
    }
}
