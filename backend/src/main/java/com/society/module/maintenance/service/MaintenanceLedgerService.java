package com.society.module.maintenance.service;

import com.society.module.maintenance.entity.MaintenanceLedger;
import com.society.module.maintenance.entity.MaintenanceLedger.EntryType;
import com.society.module.maintenance.entity.MaintenanceLedger.Source;
import com.society.module.maintenance.entity.MaintenanceBill;
import com.society.module.maintenance.repository.MaintenanceLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Writes append-only entries to the {@code maintenance_ledger} audit table. Every money
 * mutation on a bill must go through here so there is a complete, attributable history for
 * dispute resolution. Methods participate in the caller's transaction, so a ledger row is
 * committed atomically with the mutation it records (and rolled back together on failure).
 */
@Service
@RequiredArgsConstructor
public class MaintenanceLedgerService {

    private final MaintenanceLedgerRepository ledgerRepository;

    /**
     * Record a money mutation against a bill.
     *
     * @param bill          the affected bill (used for ids and balance snapshot)
     * @param paymentId     related payment id, or null (e.g. bill generation)
     * @param entryType     what happened
     * @param amount        signed amount (positive = increases owed/paid, negative = reversal)
     * @param balanceBefore bill balance before the mutation
     * @param balanceAfter  bill balance after the mutation
     * @param source        channel the mutation came through
     * @param reference     optional gateway/receipt reference
     * @param reason        optional human-readable reason (required for reversals)
     */
    public void record(MaintenanceBill bill, Long paymentId, EntryType entryType,
                        BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
                        Source source, String reference, String reason) {
        MaintenanceLedger entry = MaintenanceLedger.builder()
                .billId(bill != null ? bill.getBillId() : null)
                .unitId(bill != null && bill.getUnit() != null ? bill.getUnit().getUnitId() : null)
                .paymentId(paymentId)
                .entryType(entryType)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .source(source)
                .reference(reference)
                .performedBy(currentUser())
                .performedOn(LocalDateTime.now())
                .reason(reason)
                .build();
        ledgerRepository.save(entry);
    }

    /**
     * Resolve the acting username from the security context. Falls back to "SYSTEM" for
     * unauthenticated contexts (e.g. gateway webhooks), so ledger rows are always attributed.
     */
    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null
                && !"anonymousUser".equalsIgnoreCase(auth.getName())) {
            return auth.getName();
        }
        return "SYSTEM";
    }
}
