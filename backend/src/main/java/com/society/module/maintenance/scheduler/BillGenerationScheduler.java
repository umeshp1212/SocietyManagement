package com.society.module.maintenance.scheduler;

import com.society.module.maintenance.dto.GenerateBillsRequest;
import com.society.module.maintenance.service.MaintenanceBillService;
import com.society.module.settings.entity.SocietySettings;
import com.society.module.settings.service.SocietySettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

/**
 * Automatically generates the current month's maintenance bills once a month, on the
 * society-configured {@code billGenerationDay}.
 *
 * <p>The job itself runs daily (cron below), but it is gated two ways so it is both
 * admin-controllable and idempotent:</p>
 * <ol>
 *   <li><b>Admin Start/Stop</b> — {@link SocietySettings#getAutoBillGenerationEnabled()}
 *       is the on/off switch flipped by the admin button. When off, the job no-ops.</li>
 *   <li><b>Day match</b> — it only acts when today's day-of-month equals the configured
 *       generation day (clamped to the month length, so day 31 fires on the last day of a
 *       short month). On every other day it no-ops.</li>
 * </ol>
 *
 * <p>Generation is naturally idempotent: {@code generateMonthlyBills} skips any unit that
 * already has a bill for the month, so even if the server restarts and the job fires again
 * on the same day, no duplicate bills are produced. {@code regenerate} is left false so paid
 * and unpaid bills are never disturbed by the automatic run.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BillGenerationScheduler {

    private final MaintenanceBillService billService;
    private final SocietySettingsService settingsService;

    /**
     * Runs every day at 02:00 server time. The actual month-day gate and the enabled flag
     * are checked inside, so changing {@code billGenerationDay} from the admin portal takes
     * effect without a redeploy. Override the schedule with {@code app.maintenance.bill-gen-cron}.
     */
    @Scheduled(cron = "${app.maintenance.bill-gen-cron:0 0 2 * * *}")
    public void generateMonthlyBillsIfDue() {
        try {
            SocietySettings settings = settingsService.getSettings();

            if (!Boolean.TRUE.equals(settings.getAutoBillGenerationEnabled())) {
                return; // admin has it stopped
            }

            LocalDate today = LocalDate.now();
            if (!isConfiguredDay(today, settings.getBillGenerationDay())) {
                return; // not the generation day
            }

            GenerateBillsRequest request = GenerateBillsRequest.builder()
                    .month(today.getMonthValue())
                    .year(today.getYear())
                    .regenerate(false) // never disturb existing bills automatically
                    .build();

            Map<String, Object> result = billService.generateMonthlyBills(request);
            log.info("Auto bill generation ran for {}/{}: {}",
                    today.getMonthValue(), today.getYear(), result.get("message"));

        } catch (RuntimeException e) {
            // A scheduled generation failure must never crash the scheduler thread.
            log.error("Auto bill generation failed: {}", e.toString());
        }
    }

    /**
     * True when {@code today} is the configured generation day. The configured day is
     * resolved to a real calendar day of the current month:
     * <ul>
     *   <li>0 (or null) means "last day of the month" (28/29/30/31 as appropriate);</li>
     *   <li>any day past the month's length is clamped to the last day, so 31 fires on
     *       Feb 28/29.</li>
     * </ul>
     */
    private boolean isConfiguredDay(LocalDate today, Integer configuredDay) {
        int lastDay = today.lengthOfMonth();
        int target;
        if (configuredDay == null || configuredDay <= 0) {
            target = lastDay; // last day of month
        } else {
            target = Math.min(configuredDay, lastDay);
        }
        return today.getDayOfMonth() == target;
    }
}
