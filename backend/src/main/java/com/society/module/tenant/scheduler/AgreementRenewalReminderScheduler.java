package com.society.module.tenant.scheduler;

import com.society.module.tenant.service.AgreementRenewalReminderService;
import com.society.module.tenant.service.AgreementRenewalReminderService.ReminderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends automated rent-agreement renewal reminder emails to owners whose tenant agreement is
 * expiring within the configured window (default 15 days).
 *
 * <p>Mirrors {@code PaymentReminderScheduler}: runs daily at a sensible hour, externalises the
 * cron via {@code app.tenant.renewal-reminder-cron}, and guards against RuntimeExceptions so a
 * failed run never kills the scheduler thread. Idempotency (one reminder per cycle) is handled
 * in the service via the tenant's {@code renewalReminderSentOn} flag.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgreementRenewalReminderScheduler {

    private final AgreementRenewalReminderService reminderService;

    /** Toggle the whole job without a redeploy. Default enabled. */
    @Value("${app.tenant.renewal-reminder-enabled:true}")
    private boolean enabled;

    /** Days before expiry to start reminding. Business rule: 15 days. */
    @Value("${app.tenant.renewal-reminder-window-days:15}")
    private int windowDays;

    /**
     * Runs every day at 09:00 server time. Override with
     * {@code app.tenant.renewal-reminder-cron}.
     */
    @Scheduled(cron = "${app.tenant.renewal-reminder-cron:0 0 9 * * *}")
    public void sendRemindersIfDue() {
        if (!enabled) {
            return;
        }
        try {
            ReminderResult result = reminderService.sendRenewalReminders(windowDays);
            log.info("Auto agreement renewal reminders ran (window={} days): {} candidate(s), {} sent, {} skipped (no email).",
                    windowDays, result.candidates(), result.sent(), result.skippedNoEmail());
        } catch (RuntimeException e) {
            // A scheduled reminder failure must never crash the scheduler thread.
            log.error("Auto agreement renewal reminder run failed: {}", e.toString());
        }
    }
}
