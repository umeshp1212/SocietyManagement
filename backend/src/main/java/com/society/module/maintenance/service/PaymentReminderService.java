package com.society.module.maintenance.service;

import com.society.module.maintenance.entity.MaintenanceBill;
import com.society.module.maintenance.repository.MaintenanceBillRepository;
import com.society.module.owner.entity.Owner;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.entity.UnitOwner;
import com.society.module.owner.repository.UnitOwnerRepository;
import com.society.module.owner.repository.UnitRepository;
import com.society.module.settings.entity.SocietySettings;
import com.society.module.settings.service.SocietySettingsService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends maintenance-payment reminder emails to members (unit primary owners) who still have
 * outstanding dues. Each email is an HTML letterhead (society header/footer from
 * {@link SocietySettings}) whose body shows the outstanding amount and a prominent
 * "Pay Now" button linking to the member portal, so the member can click through and pay
 * online instead of paying externally.
 *
 * <p>Mail handling mirrors {@code OwnerEmailServiceImpl}: the {@link JavaMailSender} is
 * injected with {@code required = false} so a missing mail config degrades gracefully, and a
 * per-recipient failure never halts delivery to the remaining recipients.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentReminderService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // Nullable: when mail is not configured the bean is absent and we degrade gracefully.
    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@society.com}")
    private String fromEmail;

    /** Member portal host (also the CORS origin, so scheme+host only, no path). */
    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    /** Angular build baseHref, e.g. "/app" in prod; empty in dev. */
    @Value("${app.frontend-base-path:}")
    private String frontendBasePath;

    private final MaintenanceBillRepository billRepository;
    private final UnitRepository unitRepository;
    private final UnitOwnerRepository unitOwnerRepository;
    private final SocietySettingsService societySettingsService;

    /** Immutable per-unit reminder target: owner email + name + total outstanding. */
    public record ReminderTarget(Long unitId, String unitNumber, String ownerName,
                                 String email, BigDecimal outstanding, LocalDate earliestDueDate) {
    }

    /**
     * Result of a reminder run, suitable for returning to the admin "send now" endpoint and
     * for logging the scheduled run.
     */
    public record ReminderResult(int candidates, int sent, int skippedNoEmail, boolean mailConfigured,
                                 List<String> skippedUnits) {
    }

    /**
     * Build the list of units with outstanding dues and a resolvable primary-owner email.
     * Units whose primary owner has no email are returned as "skipped" by the caller.
     */
    public List<ReminderTarget> collectTargets() {
        List<Unit> units = unitRepository.findAll();
        // De-dupe by unit, keep insertion order for stable reporting.
        Map<Long, ReminderTarget> targets = new LinkedHashMap<>();

        for (Unit unit : units) {
            if (!"ACTIVE".equals(unit.getStatus())) {
                continue;
            }
            BigDecimal outstanding = billRepository.getTotalOutstandingByUnit(unit.getUnitId());
            if (outstanding == null || outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            Owner owner = resolvePrimaryOwner(unit);
            String email = owner != null ? owner.getEmail() : null;
            String ownerName = owner != null ? owner.getFullName() : "Resident";

            LocalDate earliestDue = earliestOutstandingDueDate(unit.getUnitId());

            targets.put(unit.getUnitId(), new ReminderTarget(
                    unit.getUnitId(), unit.getUnitNumber(), ownerName,
                    email, outstanding, earliestDue));
        }
        return new ArrayList<>(targets.values());
    }

    /**
     * Send a reminder to every unit with outstanding dues. Best-effort per recipient.
     */
    public ReminderResult sendReminders() {
        List<ReminderTarget> targets = collectTargets();
        SocietySettings settings = societySettingsService.getSettings();

        if (mailSender == null) {
            log.warn("Mail sender not configured. {} payment reminder(s) not sent.", targets.size());
            List<String> skipped = new ArrayList<>();
            for (ReminderTarget t : targets) skipped.add(t.unitNumber());
            return new ReminderResult(targets.size(), 0, 0, false, skipped);
        }

        int sent = 0;
        int skippedNoEmail = 0;
        List<String> skippedUnits = new ArrayList<>();

        for (ReminderTarget t : targets) {
            if (!StringUtils.hasText(t.email())) {
                skippedNoEmail++;
                skippedUnits.add(t.unitNumber() + " (no email)");
                continue;
            }
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true);
                helper.setFrom(fromEmail);
                helper.setTo(t.email().trim());
                helper.setSubject(buildSubject(settings, t));
                String html = buildHtml(settings, t);
                String plain = buildPlainText(settings, t);
                helper.setText(plain, html);
                mailSender.send(message);
                sent++;
            } catch (Exception e) {
                log.error("Failed to send payment reminder for unit {} to {}: {}",
                        t.unitNumber(), t.email(), e.getMessage());
                skippedUnits.add(t.unitNumber() + " (send failure)");
            }
        }

        log.info("Payment reminder run complete: {} candidate(s), {} sent, {} skipped (no email).",
                targets.size(), sent, skippedNoEmail);
        return new ReminderResult(targets.size(), sent, skippedNoEmail, true, skippedUnits);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private Owner resolvePrimaryOwner(Unit unit) {
        return unitOwnerRepository.findPrimaryOwnerByUnitId(unit.getUnitId())
                .map(UnitOwner::getOwner)
                // Fall back to any linked owner if no explicit primary is flagged.
                .orElseGet(() -> unitOwnerRepository.findByUnit_UnitId(unit.getUnitId()).stream()
                        .map(UnitOwner::getOwner)
                        .findFirst()
                        .orElse(null));
    }

    private LocalDate earliestOutstandingDueDate(Long unitId) {
        return billRepository.findOutstandingByUnit(unitId).stream()
                .map(MaintenanceBill::getDueDate)
                .filter(d -> d != null)
                .min(LocalDate::compareTo)
                .orElse(null);
    }

    /**
     * Member portal deep link. The portal login route is "/login" and the dashboard (which
     * lists bills and has the Pay button) is "/dashboard". We point members at the dashboard;
     * the member auth guard bounces them to login first if their session has expired.
     */
    private String memberPortalLink() {
        String host = frontendUrl != null ? frontendUrl.replaceAll("/+$", "") : "";
        String base = frontendBasePath != null ? frontendBasePath.trim() : "";
        if (!base.isEmpty()) {
            if (!base.startsWith("/")) base = "/" + base;
            base = base.replaceAll("/+$", "");
        }
        return host + base + "/dashboard";
    }

    private String buildSubject(SocietySettings settings, ReminderTarget t) {
        String society = settings != null && StringUtils.hasText(settings.getSocietyName())
                ? settings.getSocietyName().trim() : "Society";
        return society + " - Maintenance Payment Reminder (Unit " + t.unitNumber() + ")";
    }

    private String buildPlainText(SocietySettings settings, ReminderTarget t) {
        String society = settings != null && StringUtils.hasText(settings.getSocietyName())
                ? settings.getSocietyName().trim() : "Your Society";
        StringBuilder sb = new StringBuilder();
        sb.append("Dear ").append(t.ownerName()).append(",\n\n");
        sb.append("This is a friendly reminder that maintenance dues are pending for Unit ")
                .append(t.unitNumber()).append(".\n\n");
        sb.append("Total outstanding: INR ").append(t.outstanding().toPlainString()).append("\n");
        if (t.earliestDueDate() != null) {
            sb.append("Due since: ").append(t.earliestDueDate().format(DATE_FMT)).append("\n");
        }
        sb.append("\nYou can pay online quickly and securely from the member portal:\n");
        sb.append(memberPortalLink()).append("\n\n");
        sb.append("Paying online updates your account instantly and gives you a receipt right away.\n\n");
        sb.append("Regards,\n").append(society);
        return sb.toString();
    }

    /**
     * HTML letterhead email with a prominent "Pay Now" button that deep-links to the member
     * portal. Society header/footer values are HTML-escaped (plain data). Falls back to safe
     * defaults when the society header/footer settings are incomplete, so a reminder is never
     * blocked by missing office-bearer names (unlike the owner-email letterhead which rejects).
     */
    private String buildHtml(SocietySettings settings, ReminderTarget t) {
        String link = memberPortalLink();
        String society = settings != null && StringUtils.hasText(settings.getSocietyName())
                ? esc(settings.getSocietyName().trim()) : "Your Society";

        StringBuilder html = new StringBuilder();
        html.append("<div style=\"margin:0;padding:24px 0;background-color:#f4f6f8;\">");
        html.append("<div style=\"max-width:640px;margin:0 auto;background-color:#ffffff;")
            .append("border:1px solid #e0e0e0;border-radius:8px;overflow:hidden;")
            .append("font-family:Arial,Helvetica,sans-serif;color:#333333;\">");

        // Header band
        html.append("<div style=\"background-color:#1a237e;color:#ffffff;padding:20px 24px;text-align:center;\">");
        html.append("<div style=\"font-size:22px;font-weight:bold;letter-spacing:0.5px;\">")
            .append(society).append("</div>");
        html.append("<div style=\"font-size:13px;margin-top:6px;color:#e8eaf6;\">Maintenance Payment Reminder</div>");
        html.append("</div>");

        // Body
        html.append("<div style=\"padding:24px;font-size:14px;line-height:1.6;\">");
        html.append("<p style=\"margin:0 0 12px 0;\">Dear ").append(esc(t.ownerName())).append(",</p>");
        html.append("<p style=\"margin:0 0 16px 0;\">This is a friendly reminder that maintenance dues are pending for ")
            .append("<strong>Unit ").append(esc(t.unitNumber())).append("</strong>.</p>");

        // Outstanding summary card
        html.append("<div style=\"background:#fff8e1;border:1px solid #ffe082;border-radius:6px;")
            .append("padding:16px;margin:0 0 20px 0;\">");
        html.append("<div style=\"font-size:13px;color:#8d6e63;\">Total Outstanding</div>");
        html.append("<div style=\"font-size:26px;font-weight:bold;color:#e65100;margin-top:4px;\">&#8377; ")
            .append(esc(t.outstanding().toPlainString())).append("</div>");
        if (t.earliestDueDate() != null) {
            html.append("<div style=\"font-size:12px;color:#8d6e63;margin-top:6px;\">Due since ")
                .append(esc(t.earliestDueDate().format(DATE_FMT))).append("</div>");
        }
        html.append("</div>");

        html.append("<p style=\"margin:0 0 20px 0;\">Skip the queue and the bank transfer hassle &mdash; ")
            .append("pay online in a few taps from your member portal. Your account is updated instantly ")
            .append("and your receipt is available right away.</p>");

        // Pay Now button (bulletproof-ish table button for email clients)
        html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 auto 20px auto;\"><tr>");
        html.append("<td style=\"border-radius:6px;background-color:#00796b;\">");
        html.append("<a href=\"").append(esc(link)).append("\" target=\"_blank\" ")
            .append("style=\"display:inline-block;padding:14px 32px;font-size:16px;font-weight:bold;")
            .append("color:#ffffff;text-decoration:none;border-radius:6px;\">Pay Now on Member Portal</a>");
        html.append("</td></tr></table>");

        html.append("<p style=\"margin:0 0 4px 0;font-size:12px;color:#777;\">If the button doesn't work, ")
            .append("copy and paste this link into your browser:</p>");
        html.append("<p style=\"margin:0 0 8px 0;font-size:12px;\"><a href=\"").append(esc(link))
            .append("\" target=\"_blank\" style=\"color:#00796b;word-break:break-all;\">").append(esc(link))
            .append("</a></p>");
        html.append("</div>");

        // Footer band
        html.append("<div style=\"background-color:#1a237e;color:#c5cae9;padding:16px 24px;font-size:12px;text-align:center;\">");
        html.append("This is an automated reminder from ").append(society).append(".");
        html.append("</div>");

        html.append("</div></div>");
        return html.toString();
    }

    private String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }
}
