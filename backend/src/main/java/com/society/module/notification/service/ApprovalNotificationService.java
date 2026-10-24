package com.society.module.notification.service;

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

import java.util.ArrayList;
import java.util.List;

/**
 * Centralises the member-approval email notifications:
 *
 * <ol>
 *   <li><b>Request submitted</b> (member registration, profile update, tenant registration):
 *       an HTML email is sent to the <em>society</em> inbox so the admin knows a request is
 *       waiting for approval. The email carries a "Review Request" button that deep-links to
 *       the admin portal login.</li>
 *   <li><b>Request approved</b>: a confirmation HTML email is sent to the <em>owner</em>
 *       (the requester) with a "Go to Member Portal" button that deep-links to the member
 *       portal login.</li>
 * </ol>
 *
 * <p>The HTML letterhead (header/footer band + prominent action button) mirrors
 * {@code PaymentReminderService}'s "Pay Now" template so all system emails look consistent.
 * A plain-text alternative is always included for non-HTML mail clients.</p>
 *
 * <p>Mail is best-effort: the {@link JavaMailSender} is injected with {@code required = false}
 * so a missing mail configuration degrades gracefully, and any failure is logged but never
 * propagated, so an email outage never rolls back the underlying request/approval
 * transaction.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalNotificationService {

    // Nullable: when mail is not configured the bean is absent and we degrade gracefully.
    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@society.com}")
    private String fromEmail;

    /** Frontend host (also the CORS origin, so scheme+host only, no path). */
    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    /** Angular build baseHref, e.g. "/app" in prod; empty in dev. */
    @Value("${app.frontend-base-path:}")
    private String frontendBasePath;

    private final SocietySettingsService societySettingsService;

    /** A labelled row shown in the email's detail card. */
    private record Detail(String label, String value) {
    }

    // ==================== REQUEST SUBMITTED -> SOCIETY ====================

    /**
     * Notify the society inbox that a new member registration request needs approval.
     */
    public void notifySocietyOfRegistrationRequest(String memberEmail, String memberMobile, String unitNumber) {
        List<Detail> details = new ArrayList<>();
        details.add(new Detail("Unit", unitNumber));
        details.add(new Detail("Email", memberEmail));
        details.add(new Detail("Mobile", memberMobile));
        sendToSociety(
                "New Member Registration Request - Unit " + safe(unitNumber),
                "New Member Registration Request",
                "A new member registration request has been submitted and is awaiting your approval.",
                details);
    }

    /**
     * Notify the society inbox that a profile update request needs approval.
     */
    public void notifySocietyOfProfileUpdateRequest(String ownerName, String unitNumber,
                                                     String fieldType, String reason) {
        List<Detail> details = new ArrayList<>();
        details.add(new Detail("Member", ownerName));
        details.add(new Detail("Unit", unitNumber));
        details.add(new Detail("Fields to update", fieldType));
        details.add(new Detail("Reason", reason));
        sendToSociety(
                "New Profile Update Request - " + safe(ownerName),
                "New Profile Update Request",
                "A profile update request has been submitted and is awaiting your approval.",
                details);
    }

    /**
     * Notify the society inbox that a tenant registration needs approval.
     */
    public void notifySocietyOfTenantRequest(String tenantName, String unitNumber, String ownerName) {
        List<Detail> details = new ArrayList<>();
        details.add(new Detail("Tenant", tenantName));
        details.add(new Detail("Unit", unitNumber));
        details.add(new Detail("Owner", ownerName));
        sendToSociety(
                "New Tenant Registration Request - Unit " + safe(unitNumber),
                "New Tenant Registration Request",
                "A new tenant registration has been submitted and is awaiting your approval.",
                details);
    }

    // ==================== REQUEST APPROVED -> OWNER ====================

    /**
     * Notify the owner that their member registration request was approved.
     */
    public void notifyOwnerRegistrationApproved(String ownerEmail, String ownerName, String unitNumber) {
        List<Detail> details = new ArrayList<>();
        details.add(new Detail("Unit", unitNumber));
        sendToOwner(ownerEmail,
                "Member Registration Approved - Unit " + safe(unitNumber),
                "Member Registration Approved",
                "Dear " + safe(ownerName) + ", your member registration request for Unit "
                        + safe(unitNumber) + " has been approved by the society. "
                        + "You can now log in to the member portal using your registered mobile number.",
                details);
    }

    /**
     * Notify the owner that their profile update request was approved.
     */
    public void notifyOwnerProfileUpdateApproved(String ownerEmail, String ownerName, String unitNumber) {
        List<Detail> details = new ArrayList<>();
        if (StringUtils.hasText(unitNumber)) {
            details.add(new Detail("Unit", unitNumber));
        }
        sendToOwner(ownerEmail,
                "Profile Update Approved",
                "Profile Update Approved",
                "Dear " + safe(ownerName) + ", your profile update request has been approved by the "
                        + "society and your details have been updated.",
                details);
    }

    /**
     * Notify the owner that their tenant registration was approved.
     */
    public void notifyOwnerTenantApproved(String ownerEmail, String ownerName,
                                          String tenantName, String unitNumber) {
        List<Detail> details = new ArrayList<>();
        details.add(new Detail("Tenant", tenantName));
        details.add(new Detail("Unit", unitNumber));
        sendToOwner(ownerEmail,
                "Tenant Registration Approved - Unit " + safe(unitNumber),
                "Tenant Registration Approved",
                "Dear " + safe(ownerName) + ", the tenant registration for " + safe(tenantName)
                        + " (Unit " + safe(unitNumber) + ") has been approved by the society.",
                details);
    }

    // ==================== DISPATCH ====================

    private void sendToSociety(String subject, String heading, String intro, List<Detail> details) {
        String societyEmail = societyEmail();
        if (!StringUtils.hasText(societyEmail)) {
            log.warn("Society email not configured; skipping notification: {}", subject);
            return;
        }
        String link = adminPortalLink();
        send(societyEmail, subject, heading, intro, details,
                "Review Request", link);
    }

    private void sendToOwner(String ownerEmail, String subject, String heading,
                             String intro, List<Detail> details) {
        if (!StringUtils.hasText(ownerEmail)) {
            log.warn("No owner email available; skipping approval notification: {}", subject);
            return;
        }
        String link = memberPortalLink();
        send(ownerEmail, subject, heading, intro, details,
                "Go to Member Portal", link);
    }

    private void send(String to, String subject, String heading, String intro,
                      List<Detail> details, String buttonLabel, String link) {
        if (mailSender == null) {
            log.warn("Mail sender not configured; email not sent: {}", subject);
            return;
        }
        SocietySettings settings = settings();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true); // multipart = true
            helper.setFrom(fromEmail);
            helper.setTo(to.trim());
            helper.setSubject(subject);

            String html = buildHtml(settings, heading, intro, details, buttonLabel, link);
            String plain = buildPlainText(settings, intro, details, buttonLabel, link);
            helper.setText(plain, html);

            mailSender.send(message);
            log.info("Notification email sent to {}: {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send notification email to {} ({}): {}", to, subject, e.getMessage());
        }
    }

    // ==================== TEMPLATES ====================

    private String buildPlainText(SocietySettings settings, String intro,
                                  List<Detail> details, String buttonLabel, String link) {
        StringBuilder sb = new StringBuilder();
        sb.append(intro).append("\n\n");
        for (Detail d : details) {
            sb.append(d.label()).append(": ").append(safe(d.value())).append("\n");
        }
        sb.append("\n").append(buttonLabel).append(": ").append(link).append("\n\n");
        sb.append("Regards,\n").append(societyName(settings));
        return sb.toString();
    }

    /**
     * HTML letterhead email with header/footer band and a prominent action button, matching
     * the "Pay Now" maintenance-reminder template. Society name is HTML-escaped (plain data).
     */
    private String buildHtml(SocietySettings settings, String heading, String intro,
                             List<Detail> details, String buttonLabel, String link) {
        String society = StringUtils.hasText(societyName(settings)) ? esc(societyName(settings)) : "Your Society";

        StringBuilder html = new StringBuilder();
        html.append("<div style=\"margin:0;padding:24px 0;background-color:#f4f6f8;\">");
        html.append("<div style=\"max-width:640px;margin:0 auto;background-color:#ffffff;")
            .append("border:1px solid #e0e0e0;border-radius:8px;overflow:hidden;")
            .append("font-family:Arial,Helvetica,sans-serif;color:#333333;\">");

        // Header band
        html.append("<div style=\"background-color:#1a237e;color:#ffffff;padding:20px 24px;text-align:center;\">");
        html.append("<div style=\"font-size:22px;font-weight:bold;letter-spacing:0.5px;\">")
            .append(society).append("</div>");
        html.append("<div style=\"font-size:13px;margin-top:6px;color:#e8eaf6;\">")
            .append(esc(heading)).append("</div>");
        html.append("</div>");

        // Body
        html.append("<div style=\"padding:24px;font-size:14px;line-height:1.6;\">");
        html.append("<p style=\"margin:0 0 16px 0;\">").append(esc(intro)).append("</p>");

        // Detail card
        if (details != null && !details.isEmpty()) {
            html.append("<div style=\"background:#f5f7fa;border:1px solid #e0e0e0;border-radius:6px;")
                .append("padding:16px;margin:0 0 20px 0;\">");
            html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;font-size:14px;\">");
            for (Detail d : details) {
                html.append("<tr>");
                html.append("<td style=\"padding:4px 12px 4px 0;color:#777;white-space:nowrap;vertical-align:top;\">")
                    .append(esc(d.label())).append("</td>");
                html.append("<td style=\"padding:4px 0;color:#333;font-weight:bold;\">")
                    .append(esc(safe(d.value()))).append("</td>");
                html.append("</tr>");
            }
            html.append("</table>");
            html.append("</div>");
        }

        // Action button (bulletproof-ish table button for email clients)
        html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 auto 20px auto;\"><tr>");
        html.append("<td style=\"border-radius:6px;background-color:#00796b;\">");
        html.append("<a href=\"").append(esc(link)).append("\" target=\"_blank\" ")
            .append("style=\"display:inline-block;padding:14px 32px;font-size:16px;font-weight:bold;")
            .append("color:#ffffff;text-decoration:none;border-radius:6px;\">").append(esc(buttonLabel)).append("</a>");
        html.append("</td></tr></table>");

        html.append("<p style=\"margin:0 0 4px 0;font-size:12px;color:#777;\">If the button doesn't work, ")
            .append("copy and paste this link into your browser:</p>");
        html.append("<p style=\"margin:0 0 8px 0;font-size:12px;\"><a href=\"").append(esc(link))
            .append("\" target=\"_blank\" style=\"color:#00796b;word-break:break-all;\">").append(esc(link))
            .append("</a></p>");
        html.append("</div>");

        // Footer band
        html.append("<div style=\"background-color:#1a237e;color:#c5cae9;padding:16px 24px;font-size:12px;text-align:center;\">");
        html.append("This is an automated message from ").append(society).append(".");
        html.append("</div>");

        html.append("</div></div>");
        return html.toString();
    }

    // ==================== LINKS / SETTINGS ====================

    /** Member portal login deep link, matching PaymentReminderService. */
    private String memberPortalLink() {
        return frontendBase() + "/member/login";
    }

    /** Admin portal login deep link (admin app served at the frontend root). */
    private String adminPortalLink() {
        return frontendBase() + "/login";
    }

    private String frontendBase() {
        String host = frontendUrl != null ? frontendUrl.replaceAll("/+$", "") : "";
        String base = frontendBasePath != null ? frontendBasePath.trim() : "";
        if (!base.isEmpty()) {
            if (!base.startsWith("/")) base = "/" + base;
            base = base.replaceAll("/+$", "");
        }
        return host + base;
    }

    private SocietySettings settings() {
        try {
            return societySettingsService.getSettings();
        } catch (Exception e) {
            log.error("Could not load society settings: {}", e.getMessage());
            return null;
        }
    }

    private String societyEmail() {
        SocietySettings s = settings();
        return s != null ? s.getEmail() : null;
    }

    private String societyName(SocietySettings settings) {
        if (settings != null && StringUtils.hasText(settings.getSocietyName())) {
            return settings.getSocietyName().trim();
        }
        return "Society Management";
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }
}
