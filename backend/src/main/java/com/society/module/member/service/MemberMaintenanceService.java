package com.society.module.member.service;

import com.society.common.PagedResponse;
import com.society.exception.BusinessException;
import com.society.module.maintenance.dto.BillDTO;
import com.society.module.maintenance.dto.PaymentDTO;
import com.society.module.maintenance.service.MaintenanceBillService;
import com.society.module.member.dto.MemberDashboardResponse;
import com.society.module.owner.entity.Owner;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.entity.UnitOwner;
import com.society.module.owner.repository.OwnerRepository;
import com.society.module.owner.repository.UnitOwnerRepository;
import com.society.module.settings.entity.SocietySettings;
import com.society.module.settings.service.SocietySettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberMaintenanceService {

    private final MaintenanceBillService billService;
    private final OwnerRepository ownerRepository;
    private final UnitOwnerRepository unitOwnerRepository;
    private final SocietySettingsService settingsService;

    /**
     * Get the dashboard summary for a member's unit.
     * Shows outstanding amount, outstanding bills, and recent payments.
     */
    @Transactional(readOnly = true)
    public MemberDashboardResponse getDashboard(Long ownerId, Long unitId) {
        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new BusinessException("Owner not found"));

        // Verify this owner actually owns the requested unit
        validateOwnerUnit(ownerId, unitId);

        UnitOwner unitOwner = unitOwnerRepository.findByOwner_OwnerId(ownerId).stream()
                .filter(uo -> uo.getUnit().getUnitId().equals(unitId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Unit not found for this member"));

        Unit unit = unitOwner.getUnit();

        // Get outstanding info
        BigDecimal totalOutstanding = billService.getTotalOutstanding(unitId);
        if (totalOutstanding == null) totalOutstanding = BigDecimal.ZERO;

        List<BillDTO> outstandingBills = billService.getOutstandingByUnit(unitId);

        // Get recent payments (first page, 10 items)
        PagedResponse<PaymentDTO> paymentsPage = billService.getPaymentsByUnit(unitId, 0, 10);
        List<PaymentDTO> recentPayments = paymentsPage.getContent();

        // Calculate total paid
        BigDecimal totalPaid = recentPayments.stream()
                .filter(p -> "SUCCESS".equals(p.getStatus()) || "VERIFIED".equals(p.getStatus()))
                .map(PaymentDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // For total paid across all time, sum from all bills
        List<BillDTO> allBills = billService.getBillsByUnit(unitId);
        BigDecimal totalPaidAllTime = allBills.stream()
                .map(b -> b.getPaidAmount() != null ? b.getPaidAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        MemberDashboardResponse.MemberDashboardResponseBuilder builder = MemberDashboardResponse.builder()
                .ownerId(ownerId)
                .ownerName(owner.getFullName())
                .unitId(unitId)
                .unitNumber(unit.getUnitNumber())
                .wing(unit.getWing())
                .floor(unit.getFloor())
                .totalOutstanding(totalOutstanding)
                .outstandingBillCount(outstandingBills.size())
                .totalPaid(totalPaidAllTime)
                .outstandingBills(outstandingBills)
                .recentPayments(recentPayments);

        // Add discount info
        SocietySettings settings = settingsService.getSettings();
        boolean discountEnabled = Boolean.TRUE.equals(settings.getDiscountEnabled());
        builder.discountEnabled(discountEnabled);
        if (discountEnabled) {
            builder.discountPercent(settings.getDiscountPercent())
                    .discountDueDays(settings.getDiscountDueDays())
                    .discountMessage(settings.getDiscountMessage());

            // Check eligibility — any bill within discount due days?
            boolean eligible = outstandingBills.stream().anyMatch(b -> {
                if (b.getBillDate() == null || settings.getDiscountDueDays() == null) return false;
                return !java.time.LocalDate.now().isAfter(b.getBillDate().plusDays(settings.getDiscountDueDays()));
            });
            builder.discountEligible(eligible);
        }

        return builder.build();
    }

    /**
     * Get all bills for a member's unit.
     */
    @Transactional(readOnly = true)
    public List<BillDTO> getBillsByUnit(Long ownerId, Long unitId) {
        validateOwnerUnit(ownerId, unitId);
        return billService.getBillsByUnit(unitId);
    }

    /**
     * Get outstanding bills for a member's unit.
     */
    @Transactional(readOnly = true)
    public List<BillDTO> getOutstandingBills(Long ownerId, Long unitId) {
        validateOwnerUnit(ownerId, unitId);
        return billService.getOutstandingByUnit(unitId);
    }

    /**
     * Get payment history for a member's unit.
     */
    @Transactional(readOnly = true)
    public PagedResponse<PaymentDTO> getPaymentHistory(Long ownerId, Long unitId, int page, int size) {
        validateOwnerUnit(ownerId, unitId);
        return billService.getPaymentsByUnit(unitId, page, size);
    }

    /**
     * Get payments for a specific bill.
     */
    @Transactional(readOnly = true)
    public List<PaymentDTO> getPaymentsByBill(Long ownerId, Long unitId, Long billId) {
        validateOwnerUnit(ownerId, unitId);
        // Verify the bill belongs to this unit
        BillDTO bill = billService.getBillById(billId);
        if (!bill.getUnitId().equals(unitId)) {
            throw new BusinessException("This bill does not belong to your unit");
        }
        return billService.getPaymentsByBill(billId);
    }

    /**
     * Validate that the owner actually owns the specified unit.
     * Public access for controller-level validation.
     */
    public void validateUnitAccess(Long ownerId, Long unitId) {
        validateOwnerUnit(ownerId, unitId);
    }

    /**
     * Validate that the owner actually owns the specified unit.
     */
    private void validateOwnerUnit(Long ownerId, Long unitId) {
        boolean owns = unitOwnerRepository.existsByUnit_UnitIdAndOwner_OwnerId(unitId, ownerId);
        if (!owns) {
            throw new BusinessException("You don't have access to this unit");
        }
    }
}
