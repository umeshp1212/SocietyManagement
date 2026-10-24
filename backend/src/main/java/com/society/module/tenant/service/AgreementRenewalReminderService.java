package com.society.module.tenant.service;

import com.society.module.owner.entity.Owner;
import com.society.module.owner.entity.UnitOwner;
import com.society.module.owner.repository.UnitOwnerRepository;
import com.society.module.tenant.entity.Tenant;
import com.society.module.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans active tenants whose rent agreement is expiring within a configurable window
 * (default 15 days) and emails the unit's primary owner a renewal reminder.
 *
 * <p>Each reminder is recorded on the tenant via {@code renewalReminderSentOn} so the owner
 * is reminded once per cycle, not every day the agreement sits inside the window. When the
 * owner renews (updates {@code rentEndDate}) that flag is cleared, re-arming the reminder for
 * the next expiry.</p>
 *
 * <p>Mail is best-effort: a per-tenant failure never halts the remaining recipients, and a
 * missing mail configuration degrades gracefully (handled in {@link TenantNotificationService}).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgreementRenewalReminderService {

    private final TenantRepository tenantRepository;
    private final UnitOwnerRepository unitOwnerRepository;
    private final TenantNotificationService notificationService;

    /** Summary of a reminder run, useful for logging and an admin "send now" endpoint. */
    public record ReminderResult(int candidates, int sent, int skippedNoEmail,
                                 List<String> skippedUnits) {
    }

    /**
     * Send renewal reminders for every active tenant whose agreement expires within
     * {@code windowDays} days and that has not already been reminded for the current end date.
     */
    @Transactional
    public ReminderResult sendRenewalReminders(int windowDays) {
        LocalDate today = LocalDate.now();
        LocalDate windowEnd = today.plusDays(windowDays);

        List<Tenant> candidates =
                tenantRepository.findTenantsNeedingRenewalReminder(today, windowEnd);

        int sent = 0;
        int skippedNoEmail = 0;
        List<String> skippedUnits = new ArrayList<>();

        for (Tenant tenant : candidates) {
            Owner owner = resolvePrimaryOwner(tenant);
            String ownerEmail = owner != null ? owner.getEmail() : null;
            String ownerName = owner != null ? owner.getFullName() : null;
            String unitNumber = tenant.getUnit() != null ? tenant.getUnit().getUnitNumber() : "?";

            if (ownerEmail == null || ownerEmail.isBlank()) {
                skippedNoEmail++;
                skippedUnits.add(unitNumber + " (no owner email)");
                continue;
            }

            boolean emailed = notificationService.sendAgreementRenewalReminder(tenant, ownerEmail, ownerName);
            if (emailed) {
                // Mark as reminded for the current end date so we don't re-send daily.
                tenant.setRenewalReminderSentOn(today);
                tenantRepository.save(tenant);
                sent++;
            } else {
                skippedUnits.add(unitNumber + " (send failure)");
            }
        }

        log.info("Agreement renewal reminder run complete: {} candidate(s), {} sent, {} skipped (no email).",
                candidates.size(), sent, skippedNoEmail);
        return new ReminderResult(candidates.size(), sent, skippedNoEmail, skippedUnits);
    }

    /** Resolve the unit's primary owner, falling back to any linked owner. */
    private Owner resolvePrimaryOwner(Tenant tenant) {
        if (tenant.getUnit() == null) {
            return null;
        }
        Long unitId = tenant.getUnit().getUnitId();
        return unitOwnerRepository.findPrimaryOwnerByUnitId(unitId)
                .map(UnitOwner::getOwner)
                .orElseGet(() -> unitOwnerRepository.findByUnit_UnitId(unitId).stream()
                        .map(UnitOwner::getOwner)
                        .findFirst()
                        .orElse(null));
    }
}
