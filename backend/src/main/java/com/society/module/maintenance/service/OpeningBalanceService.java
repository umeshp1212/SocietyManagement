package com.society.module.maintenance.service;

import com.society.exception.BusinessException;
import com.society.exception.ResourceNotFoundException;
import com.society.module.maintenance.dto.OpeningBalanceDTO;
import com.society.module.maintenance.entity.OpeningBalance;
import com.society.module.maintenance.repository.OpeningBalanceRepository;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OpeningBalanceService {

    private final OpeningBalanceRepository openingBalanceRepository;
    private final UnitRepository unitRepository;

    public List<OpeningBalanceDTO> getAllOpeningBalances() {
        return openingBalanceRepository.findAllByOrderByUnit_UnitNumberAsc().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<OpeningBalanceDTO> getOutstandingOpeningBalances() {
        return openingBalanceRepository.findWithOutstandingBalance().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public OpeningBalanceDTO getByUnitId(Long unitId) {
        OpeningBalance ob = openingBalanceRepository.findByUnit_UnitId(unitId)
                .orElseThrow(() -> new ResourceNotFoundException("OpeningBalance", "unitId", unitId));
        return mapToDTO(ob);
    }

    public BigDecimal getOpeningBalanceForUnit(Long unitId) {
        return openingBalanceRepository.getTotalOpeningBalanceByUnit(unitId);
    }

    /**
     * Upsert a unit's opening balance as a split of Principal Arrears + Interest Arrears.
     * The total {@code amount} is derived (principal + interest) so every existing
     * outstanding/arrears calculation that reads {@code amount}/{@code balanceAmount}
     * keeps working unchanged.
     */
    @Transactional
    public OpeningBalanceDTO createOrUpdateOpeningBalance(Long unitId, BigDecimal principalArrears,
                                                          BigDecimal interestArrears, LocalDate asOfDate,
                                                          String remarks, String enteredBy) {
        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "unitId", unitId));

        BigDecimal principal = principalArrears != null ? principalArrears : BigDecimal.ZERO;
        BigDecimal interest = interestArrears != null ? interestArrears : BigDecimal.ZERO;

        if (principal.compareTo(BigDecimal.ZERO) < 0 || interest.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Principal and interest arrears must be zero or positive");
        }

        BigDecimal amount = principal.add(interest);
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Opening balance amount must be zero or positive");
        }

        OpeningBalance ob = openingBalanceRepository.findByUnit_UnitId(unitId)
                .orElse(OpeningBalance.builder()
                        .unit(unit)
                        .paidAmount(BigDecimal.ZERO)
                        .build());

        ob.setPrincipalArrears(principal);
        ob.setInterestArrears(interest);
        ob.setAmount(amount);
        ob.setAsOfDate(asOfDate != null ? asOfDate : LocalDate.now());
        ob.setRemarks(remarks);
        ob.setEnteredBy(enteredBy);
        ob.setBalanceAmount(amount.subtract(ob.getPaidAmount() != null ? ob.getPaidAmount() : BigDecimal.ZERO));

        ob = openingBalanceRepository.save(ob);
        return mapToDTO(ob);
    }

    /**
     * Outstanding principal portion of a unit's opening balance (principal minus the
     * principal share of any payments already applied). Payments are applied
     * interest-first, so principal is only reduced once interest is fully cleared.
     */
    public BigDecimal getOutstandingPrincipalForUnit(Long unitId) {
        OpeningBalance ob = openingBalanceRepository.findByUnit_UnitId(unitId).orElse(null);
        if (ob == null) return BigDecimal.ZERO;
        BigDecimal paid = ob.getPaidAmount() != null ? ob.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal interest = ob.getInterestArrears() != null ? ob.getInterestArrears() : BigDecimal.ZERO;
        BigDecimal principal = ob.getPrincipalArrears() != null ? ob.getPrincipalArrears() : BigDecimal.ZERO;
        BigDecimal paidTowardsPrincipal = paid.subtract(interest).max(BigDecimal.ZERO);
        return principal.subtract(paidTowardsPrincipal).max(BigDecimal.ZERO);
    }

    /**
     * Outstanding interest portion of a unit's opening balance (interest minus the
     * interest share of any payments already applied, interest-first).
     */
    public BigDecimal getOutstandingInterestForUnit(Long unitId) {
        OpeningBalance ob = openingBalanceRepository.findByUnit_UnitId(unitId).orElse(null);
        if (ob == null) return BigDecimal.ZERO;
        BigDecimal paid = ob.getPaidAmount() != null ? ob.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal interest = ob.getInterestArrears() != null ? ob.getInterestArrears() : BigDecimal.ZERO;
        BigDecimal paidTowardsInterest = paid.min(interest).max(BigDecimal.ZERO);
        return interest.subtract(paidTowardsInterest).max(BigDecimal.ZERO);
    }

    /**
     * Record a payment against the opening balance (when suspense is assigned or direct payment).
     */
    @Transactional
    public void recordPaymentAgainstOpeningBalance(Long unitId, BigDecimal paymentAmount) {
        OpeningBalance ob = openingBalanceRepository.findByUnit_UnitId(unitId).orElse(null);
        if (ob == null || ob.getBalanceAmount().compareTo(BigDecimal.ZERO) <= 0) return;

        BigDecimal newPaid = ob.getPaidAmount().add(paymentAmount);
        ob.setPaidAmount(newPaid);
        ob.setBalanceAmount(ob.getAmount().subtract(newPaid));
        openingBalanceRepository.save(ob);
    }

    /**
     * Reverse a payment against opening balance (when suspense assignment is reversed).
     */
    @Transactional
    public void reversePaymentFromOpeningBalance(Long unitId, BigDecimal amount) {
        OpeningBalance ob = openingBalanceRepository.findByUnit_UnitId(unitId).orElse(null);
        if (ob == null) return;

        BigDecimal newPaid = ob.getPaidAmount().subtract(amount);
        if (newPaid.compareTo(BigDecimal.ZERO) < 0) newPaid = BigDecimal.ZERO;
        ob.setPaidAmount(newPaid);
        ob.setBalanceAmount(ob.getAmount().subtract(newPaid));
        openingBalanceRepository.save(ob);
    }

    public Map<String, Object> getSummary() {
        BigDecimal totalOutstanding = openingBalanceRepository.getTotalOpeningBalanceOutstanding();
        long totalEntries = openingBalanceRepository.count();
        long outstandingEntries = openingBalanceRepository.findWithOutstandingBalance().size();

        return Map.of(
                "totalEntries", totalEntries,
                "outstandingEntries", outstandingEntries,
                "totalOutstandingAmount", totalOutstanding != null ? totalOutstanding : BigDecimal.ZERO
        );
    }

    private OpeningBalanceDTO mapToDTO(OpeningBalance ob) {
        return OpeningBalanceDTO.builder()
                .openingBalanceId(ob.getOpeningBalanceId())
                .unitId(ob.getUnit().getUnitId())
                .unitNumber(ob.getUnit().getUnitNumber())
                .ownerName(ob.getUnit().getOwnerNames())
                .principalArrears(ob.getPrincipalArrears())
                .interestArrears(ob.getInterestArrears())
                .amount(ob.getAmount())
                .asOfDate(ob.getAsOfDate())
                .remarks(ob.getRemarks())
                .enteredBy(ob.getEnteredBy())
                .paidAmount(ob.getPaidAmount())
                .balanceAmount(ob.getBalanceAmount())
                .createdOn(ob.getCreatedOn())
                .build();
    }
}
