package com.society.module.owner.service;

import com.society.exception.BusinessException;
import com.society.module.settings.entity.SocietySettings;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
 * Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7
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
}
