package com.society.module.maintenance.scheduler;

import com.society.module.maintenance.service.PaymentReminderService;
import com.society.module.maintenance.service.PaymentReminderService.ReminderResult;
import com.society.module.settings.entity.SocietySettings;
import com.society.module.settings.service.SocietySettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Sends automated email payment reminders to members with outstanding maintenance dues,
 * once a month on the society-configured {@code reminderDayOfMonth}.
 *
 * <p>Gated the same way as {@link BillGenerationScheduler}: the admin Start/Stop switch
 * ({@link SocietySettings#getAutoReminderEnabled()}) plus a day-of-month match. The job runs
 * daily but only sends on the configured day, so toggling it or changing the day from the
 * admin portal takes effect without a redeploy.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReminderScheduler {

    private final PaymentReminderService reminderService;
    private final SocietySettingsService settingsService;

    /**
     * Runs every day at 09:00 server time (a reasonable hour for members to receive a
     * reminder). The enabled flag and day gate are checked inside. Override with
     * {@code app.maintenance.reminder-cron}.
     */
    @Scheduled(cron = "${app.maintenance.reminder-cron:0 0 9 * * *}")
    public void sendRemindersIfDue() {
        try {
            SocietySettings settings = settingsService.getSettings();

            if (!Boolean.TRUE.equals(settings.getAutoReminderEnabled())) {
                return; // admin has it stopped
            }

            LocalDate today = LocalDate.now();
            if (!isConfiguredDay(today, settings.getReminderDayOfMonth())) {
                return; // not the reminder day
            }

            ReminderResult result = reminderService.sendReminders();
            log.info("Auto payment reminders ran: {} candidate(s), {} sent, {} skipped (no email), mailConfigured={}",
                    result.candidates(), result.sent(), result.skippedNoEmail(), result.mailConfigured());

        } catch (RuntimeException e) {
            // A scheduled reminder failure must never crash the scheduler thread.
            log.error("Auto payment reminder run failed: {}", e.toString());
        }
    }

    /**
     * True when {@code today} is the configured reminder day. 0 (or null) means "last day of
     * the month"; a day past the month's length is clamped to the last day (so 31 fires on
     * Feb 28/29).
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
