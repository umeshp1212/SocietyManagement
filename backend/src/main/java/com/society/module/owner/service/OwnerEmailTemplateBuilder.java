package com.society.module.owner.service;

import com.society.exception.BusinessException;
import com.society.module.settings.entity.SocietySettings;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

/**
 * Assembles the fixed three-part owner email template: Society Header, Body, Footer.
 *
 * <p>Header is populated from {@link SocietySettings} identity fields (society name,
 * address, registration number, phone, email); the Body carries the user-entered
 * subject and message; the Footer carries the signatory names (chairman, secretary,
 * treasurer). The content is always produced in the order header -> body -> footer.
 *
 * <p>Assembly is rejected (via {@link BusinessException}) before any content is
 * produced when the user subject/message is missing/blank, or when a required
 * header/footer settings field is missing/blank. The exception message identifies
 * the specific offending field.
 *
 * <p>{@link #buildHtmlBody(SocietySettings, String, String)} assembles the same
 * three-part structure as an HTML document: the header and footer settings values
 * are HTML-escaped (plain data, never markup) while the already-sanitized body is
 * inserted verbatim so its allowed formatting is preserved (Req 2.2, 2.3).
 *
 * Requirements: 2.2, 2.3, 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7
 */
@Component
public class OwnerEmailTemplateBuilder {

    private static final String LINE = System.lineSeparator();
    private static final String SECTION_BREAK = LINE + LINE;

    /**
     * Assembles the full email body in the fixed order header -> body -> footer.
     *
     * @param settings the society settings supplying header and footer content
     * @param subject  the user-entered subject
     * @param message  the user-entered message content
     * @return the assembled email content
     * @throws BusinessException if the subject or message is empty/whitespace (Req 4.6),
     *                           or a required settings field is missing/blank (Req 4.7)
     */
    public String buildBody(SocietySettings settings, String subject, String message) {
        // Validate everything before assembling anything so no content is produced on failure.
        if (settings == null) {
            throw new BusinessException("Society settings are missing");
        }
        validateUserInput(subject, message);
        validateHeaderSettings(settings);
        validateFooterSettings(settings);

        StringBuilder content = new StringBuilder();
        content.append(buildHeader(settings));
        content.append(SECTION_BREAK);
        content.append(buildBodySection(subject, message));
        content.append(SECTION_BREAK);
        content.append(buildFooter(settings));
        return content.toString();
    }

    /**
     * Assembles the full email as an HTML document in the fixed order
     * header -&gt; body -&gt; footer (Req 2.2).
     *
     * <p>Header and footer values come from {@link SocietySettings} and are
     * HTML-escaped before insertion because they are plain data and must never be
     * interpreted as markup. The subject is likewise escaped. The
     * {@code sanitizedBody} is inserted verbatim: it is <b>already sanitized</b>
     * upstream by the {@code OwnerEmailSanitizer} and therefore is not re-escaped,
     * so every element of allowed formatting it carries is preserved (Req 2.3).
     *
     * @param settings      the society settings supplying header and footer content
     * @param subject       the user-entered subject
     * @param sanitizedBody the already-sanitized HTML body
     * @return the assembled HTML email document
     * @throws BusinessException if the subject is empty/whitespace (Req 4.6),
     *                           or a required settings field is missing/blank (Req 4.7)
     */
    public String buildHtmlBody(SocietySettings settings, String subject, String sanitizedBody) {
        // Validate everything before assembling anything so no content is produced on failure.
        if (settings == null) {
            throw new BusinessException("Society settings are missing");
        }
        validateHeaderSettings(settings);
        validateFooterSettings(settings);
        if (!StringUtils.hasText(subject)) {
            throw new BusinessException("Subject is required");
        }

        StringBuilder html = new StringBuilder();
        // Outer wrapper: constrains width and centres the letterhead in the client viewport.
        html.append("<div style=\"margin:0;padding:24px 0;background-color:#f4f6f8;\">");
        html.append("<div style=\"max-width:640px;margin:0 auto;background-color:#ffffff;")
            .append("border:1px solid #e0e0e0;border-radius:8px;overflow:hidden;")
            .append("font-family:Arial,Helvetica,sans-serif;color:#333333;\">");
        html.append(buildHtmlHeader(settings));
        html.append("<div style=\"padding:24px;\">");
        html.append("<p style=\"margin:0 0 16px 0;font-size:16px;color:#1a237e;\">")
            .append("<strong>Subject: ").append(esc(subject.trim())).append("</strong></p>");
        html.append("<div style=\"font-size:14px;line-height:1.6;color:#333333;\">");
        html.append(sanitizedBody == null ? "" : sanitizedBody); // sanitized HTML, inserted verbatim
        html.append("</div>");
        html.append("</div>");
        html.append(buildHtmlFooter(settings));
        html.append("</div>");
        html.append("</div>");
        return html.toString();
    }

    /**
     * Returns the email subject line, which is the user-entered subject (Req 4.5).
     *
     * @param subject the user-entered subject
     * @return the validated, trimmed subject
     * @throws BusinessException if the subject is empty/whitespace (Req 4.6)
     */
    public String buildSubject(String subject) {
        if (!StringUtils.hasText(subject)) {
            throw new BusinessException("Subject is required");
        }
        return subject.trim();
    }

    // ----- Validation -----

    private void validateUserInput(String subject, String message) {
        if (!StringUtils.hasText(subject)) {
            throw new BusinessException("Subject is required");
        }
        if (!StringUtils.hasText(message)) {
            throw new BusinessException("Message content is required");
        }
    }

    private void validateHeaderSettings(SocietySettings settings) {
        if (!StringUtils.hasText(settings.getSocietyName())) {
            throw new BusinessException("Society name is missing");
        }
        if (!hasAnyAddressLine(settings)) {
            throw new BusinessException("Society address is missing");
        }
        if (!StringUtils.hasText(settings.getRegistrationNumber())) {
            throw new BusinessException("Society registration number is missing");
        }
        if (!StringUtils.hasText(settings.getPhone())) {
            throw new BusinessException("Society phone is missing");
        }
        if (!StringUtils.hasText(settings.getEmail())) {
            throw new BusinessException("Society email is missing");
        }
    }

    private void validateFooterSettings(SocietySettings settings) {
        if (!StringUtils.hasText(settings.getChairmanName())) {
            throw new BusinessException("Chairman name is missing");
        }
        if (!StringUtils.hasText(settings.getSecretaryName())) {
            throw new BusinessException("Secretary name is missing");
        }
        if (!StringUtils.hasText(settings.getTreasurerName())) {
            throw new BusinessException("Treasurer name is missing");
        }
    }

    private boolean hasAnyAddressLine(SocietySettings settings) {
        return StringUtils.hasText(settings.getAddressLine1())
                || StringUtils.hasText(settings.getAddressLine2())
                || StringUtils.hasText(settings.getCity())
                || StringUtils.hasText(settings.getState())
                || StringUtils.hasText(settings.getPincode());
    }

    // ----- Assembly -----

    private String buildHeader(SocietySettings settings) {
        StringBuilder header = new StringBuilder();
        header.append(settings.getSocietyName().trim());

        String address = buildAddress(settings);
        if (StringUtils.hasText(address)) {
            header.append(LINE).append(address);
        }
        header.append(LINE).append("Reg. No: ").append(settings.getRegistrationNumber().trim());
        header.append(LINE).append("Phone: ").append(settings.getPhone().trim());
        header.append(LINE).append("Email: ").append(settings.getEmail().trim());
        return header.toString();
    }

    private String buildAddress(SocietySettings settings) {
        StringBuilder addressLine = new StringBuilder();
        appendPart(addressLine, settings.getAddressLine1());
        appendPart(addressLine, settings.getAddressLine2());
        appendPart(addressLine, settings.getCity());
        appendPart(addressLine, settings.getState());
        appendPart(addressLine, settings.getPincode());
        return addressLine.toString();
    }

    private void appendPart(StringBuilder builder, String value) {
        if (StringUtils.hasText(value)) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value.trim());
        }
    }

    private String buildBodySection(String subject, String message) {
        StringBuilder body = new StringBuilder();
        body.append("Subject: ").append(subject.trim());
        body.append(SECTION_BREAK);
        body.append(message.trim());
        return body.toString();
    }

    private String buildFooter(SocietySettings settings) {
        StringBuilder footer = new StringBuilder();
        footer.append("Regards,");
        footer.append(LINE).append("Chairman: ").append(settings.getChairmanName().trim());
        footer.append(LINE).append("Secretary: ").append(settings.getSecretaryName().trim());
        footer.append(LINE).append("Treasurer: ").append(settings.getTreasurerName().trim());
        return footer.toString();
    }

    // ----- HTML Assembly -----

    /**
     * Renders the society header as an HTML block. Every settings value is
     * HTML-escaped so it can never inject markup.
     */
    private String buildHtmlHeader(SocietySettings settings) {
        StringBuilder header = new StringBuilder();
        // Letterhead header band with a solid background colour (Req: header background).
        header.append("<div style=\"background-color:#1a237e;color:#ffffff;")
              .append("padding:20px 24px;text-align:center;\">");
        header.append("<div style=\"font-size:22px;font-weight:bold;letter-spacing:0.5px;\">")
              .append(esc(settings.getSocietyName().trim())).append("</div>");

        String address = buildAddress(settings);
        if (StringUtils.hasText(address)) {
            header.append("<div style=\"font-size:13px;margin-top:6px;color:#e8eaf6;\">")
                  .append(esc(address)).append("</div>");
        }
        header.append("<div style=\"font-size:12px;margin-top:6px;color:#c5cae9;\">")
              .append("Reg. No: ").append(esc(settings.getRegistrationNumber().trim()))
              .append(" &nbsp;|&nbsp; Phone: ").append(esc(settings.getPhone().trim()))
              .append(" &nbsp;|&nbsp; Email: ").append(esc(settings.getEmail().trim()))
              .append("</div>");
        header.append("</div>");
        return header.toString();
    }

    /**
     * Renders the society footer as an HTML block. Every settings value is
     * HTML-escaped so it can never inject markup.
     */
    private String buildHtmlFooter(SocietySettings settings) {
        StringBuilder footer = new StringBuilder();
        // Letterhead footer band with a solid background colour (Req: footer background).
        footer.append("<div style=\"background-color:#1a237e;color:#ffffff;padding:20px 24px;\">");
        footer.append("<div style=\"font-size:13px;margin-bottom:12px;color:#e8eaf6;\">Regards,</div>");

        // Signatories on a single line: Chairman (left), Secretary (center), Treasurer (right).
        // A table is used for reliable column alignment across email clients.
        footer.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
              .append("style=\"width:100%;border-collapse:collapse;\"><tr>");

        footer.append("<td style=\"text-align:left;vertical-align:top;font-size:13px;color:#ffffff;\">")
              .append("<strong>Chairman</strong><br/>")
              .append("<span style=\"color:#c5cae9;\">").append(esc(settings.getChairmanName().trim()))
              .append("</span></td>");

        footer.append("<td style=\"text-align:center;vertical-align:top;font-size:13px;color:#ffffff;\">")
              .append("<strong>Secretary</strong><br/>")
              .append("<span style=\"color:#c5cae9;\">").append(esc(settings.getSecretaryName().trim()))
              .append("</span></td>");

        footer.append("<td style=\"text-align:right;vertical-align:top;font-size:13px;color:#ffffff;\">")
              .append("<strong>Treasurer</strong><br/>")
              .append("<span style=\"color:#c5cae9;\">").append(esc(settings.getTreasurerName().trim()))
              .append("</span></td>");

        footer.append("</tr></table>");
        footer.append("</div>");
        return footer.toString();
    }

    /**
     * HTML entity-escapes plain text so settings-sourced values can never inject markup.
     */
    private String esc(String value) {
        return HtmlUtils.htmlEscape(value);
    }
}
