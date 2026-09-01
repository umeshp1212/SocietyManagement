package com.society.module.tenant.service;

import com.society.enums.NocStatus;
import com.society.enums.OccupancyStatus;
import com.society.enums.TenantStatus;
import com.society.exception.BusinessException;
import com.society.exception.ResourceNotFoundException;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.repository.UnitRepository;
import com.society.module.tenant.dto.TenantDTO;
import com.society.module.tenant.entity.Tenant;
import com.society.module.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the SUPER_ADMIN approval workflow for member-submitted tenant registrations.
 *
 * On approval:
 *   1. Tenant status -> ACTIVE, NOC status -> APPROVED (records approver + timestamp).
 *   2. Unit occupancy -> RENTED, which causes the non-occupancy charge
 *      (MaintenanceChargeConfig with applicableTo = RENTED) to be included on the
 *      unit's maintenance bills from the next bill generation.
 *   3. A No Objection Certificate PDF is generated and emailed to the owner
 *      (CC the tenant when a tenant email is present). Email is best-effort.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantApprovalService {

    private final TenantRepository tenantRepository;
    private final UnitRepository unitRepository;
    private final TenantService tenantService;
    private final TenantNotificationService tenantNotificationService;

    /**
     * List all tenant registrations awaiting admin approval.
     */
    public List<TenantDTO> getPendingApprovals() {
        return tenantRepository.findByStatusOrderByCreatedOnDesc(TenantStatus.PENDING_APPROVAL)
                .stream()
                .map(tenantService::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Approve a pending tenant registration.
     *
     * @param tenantId   the pending tenant
     * @param approverName the authenticated admin's name/username
     */
    @Transactional
    public TenantDTO approve(Long tenantId, String approverName) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "tenantId", tenantId));

        if (tenant.getStatus() != TenantStatus.PENDING_APPROVAL) {
            throw new BusinessException("Only pending tenant registrations can be approved. Current status: "
                    + tenant.getStatus());
        }

        // Guard: ensure the unit hasn't been occupied by another active tenant in the meantime
        String unitNumber = tenant.getUnit().getUnitNumber();
        boolean unitHasActiveTenant = tenantRepository
                .findActiveByUnitId(tenant.getUnit().getUnitId(), TenantStatus.ACTIVE)
                .isPresent();
        if (unitHasActiveTenant) {
            throw new BusinessException("Unit " + unitNumber +
                    " already has an active tenant. Cannot approve this registration.");
        }

        // 1. Activate tenant + approve NOC
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setNocStatus(NocStatus.APPROVED);
        tenant.setNocApprovedBy(approverName != null ? approverName : "ADMIN");
        tenant.setNocApprovedOn(LocalDateTime.now());
        tenant = tenantRepository.save(tenant);

        // 2. Mark unit as RENTED -> non-occupancy charge applies from next bill generation
        Unit unit = tenant.getUnit();
        unit.setOccupancyStatus(OccupancyStatus.RENTED);
        unitRepository.save(unit);

        // 3. Generate + email the No Objection Certificate (best-effort)
        tenantNotificationService.sendNocCertificate(tenant);

        log.info("Tenant {} (unit {}) approved by {} -> unit set RENTED, NOC issued.",
                tenant.getTenantId(), unit.getUnitNumber(), approverName);

        return tenantService.toDTO(tenant);
    }

    /**
     * Reject a pending tenant registration.
     */
    @Transactional
    public TenantDTO reject(Long tenantId, String approverName, String reason) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "tenantId", tenantId));

        if (tenant.getStatus() != TenantStatus.PENDING_APPROVAL) {
            throw new BusinessException("Only pending tenant registrations can be rejected. Current status: "
                    + tenant.getStatus());
        }

        tenant.setStatus(TenantStatus.REJECTED);
        tenant.setNocStatus(NocStatus.REJECTED);
        tenant.setNocApprovedBy(approverName != null ? approverName : "ADMIN");
        tenant.setNocApprovedOn(LocalDateTime.now());
        tenant.setMoveOutReason(reason); // reuse field to store the rejection reason
        tenant = tenantRepository.save(tenant);

        log.info("Tenant registration {} (unit {}) rejected by {}: {}",
                tenant.getTenantId(), tenant.getUnit().getUnitNumber(), approverName, reason);

        return tenantService.toDTO(tenant);
    }
}
