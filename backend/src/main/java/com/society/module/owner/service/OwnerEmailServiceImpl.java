package com.society.module.owner.service;

import com.society.enums.OwnerStatus;
import com.society.exception.BusinessException;
import com.society.module.owner.dto.NotEmailedEntry;
import com.society.module.owner.dto.NotEmailedReason;
import com.society.module.owner.dto.RecipientScope;
import com.society.module.owner.dto.SendOwnerEmailRequest;
import com.society.module.owner.dto.SendReportDTO;
import com.society.module.owner.entity.Owner;
import com.society.module.owner.repository.OwnerRepository;
import com.society.module.settings.entity.SocietySettings;
import com.society.module.settings.service.SocietySettingsService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default implementation of {@link OwnerEmailService}.
 *
 * <p>Resolves recipients from the request scope, assembles the templated email via
 * {@link OwnerEmailTemplateBuilder}, and performs a best-effort per-recipient send,
 * returning a {@link SendReportDTO} whose counts reconcile
 * ({@code sentCount + notEmailedCount == totalAttempted == resolvedRecipientCount}).</p>
 *
 * <p>Mail handling mirrors {@code TenantNotificationService}: the {@link JavaMailSender}
 * is injected with {@code required = false} so its absence degrades gracefully rather
 * than failing, and a per-recipient failure never halts delivery to the remaining
 * recipients.</p>
 *
 * Requirements: 2.3, 2.4, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 7.1, 7.2, 7.3, 8.1, 8.2, 8.3, 8.4, 8.5
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OwnerEmailServiceImpl implements OwnerEmailService {

    // Nullable: when mail is not configured the bean is absent and we degrade gracefully.
    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@society.com}")
    private String fromEmail;

    private final OwnerRepository ownerRepository;
    private final SocietySettingsService societySettingsService;
    private final OwnerEmailTemplateBuilder templateBuilder;
    private final OwnerEmailSanitizer sanitizer;
    private final OwnerEmailPlainTextRenderer plainTextRenderer;

    @Override
    public SendReportDTO sendOwnerEmail(SendOwnerEmailRequest request) {
        return sendOwnerEmail(request, null);
    }

    @Override
    public SendReportDTO sendOwnerEmail(SendOwnerEmailRequest request, List<MultipartFile> attachments) {
        // Attachments are optional: normalise to a list of non-empty files only.
        List<MultipartFile> validAttachments = normaliseAttachments(attachments);

        // Resolve the authoritative recipient set server-side (Req 2.3, 2.4).
        List<Owner> recipients = resolveRecipients(request);
        int totalAttempted = recipients.size();

        // Authoritative server-side sanitization, once per request, before any template
        // assembly or send. Only the Sanitized_Body is ever embedded; the raw
        // Rich_Text_Body is never used past this point (Req 3.1, 3.6).
        String sanitizedBody = sanitizer.sanitize(request.getBody());

        // Visible-text validation on the sanitized body, whatever will actually be sent.
        // A failing check throws a BusinessException that propagates before any send loop
        // runs, so no email is sent and no partial record is kept (Req 3.7, 4.4, 4.5, 4.6).
        String visible = sanitizer.visibleText(sanitizedBody);
        if (visible.isEmpty()) {
            throw new BusinessException("Message content is required"); // Req 4.4
        }
        if (visible.length() > 10_000) {
            throw new BusinessException("Message exceeds the 10,000 character limit"); // Req 4.5
        }
        if (sanitizedBody.length() > 50_000) {
            throw new BusinessException("Message body is too large"); // Req 4.6 (defence in depth)
        }

        // Assemble the template up-front from the SANITIZED body. A BusinessException
        // (missing settings / empty user fields) propagates and is mapped by
        // GlobalExceptionHandler (Req 4.6, 4.7). The HTML template preserves the allowed
        // formatting (Req 2.2, 2.3) and the plain-text alternative is derived from the same
        // sanitized content (Req 2.4, 7.1, 7.2, 7.3).
        SocietySettings settings = societySettingsService.getSettings();
        String assembledSubject = templateBuilder.buildSubject(request.getSubject());
        String assembledHtml = templateBuilder.buildHtmlBody(settings, request.getSubject(), sanitizedBody);
        String plainText = plainTextRenderer.render(sanitizedBody, request.getSubject(), settings);

        // Mail transport absent: send nothing, report every attempted recipient as
        // MAIL_NOT_CONFIGURED, and return normally without an error (Req 8.1, 8.2).
        if (mailSender == null) {
            List<NotEmailedEntry> notEmailed = new ArrayList<>();
            for (Owner owner : recipients) {
                notEmailed.add(toEntry(owner, NotEmailedReason.MAIL_NOT_CONFIGURED));
            }
            log.warn("Mail sender not configured. Owner email not sent to {} attempted recipient(s).",
                    totalAttempted);
            return buildReport(totalAttempted, 0, notEmailed, false);
        }

        int sentCount = 0;
        List<NotEmailedEntry> notEmailed = new ArrayList<>();

        for (Owner owner : recipients) {
            String email = owner.getEmail();

            // Invalid recipient: null/blank email is never sent to (Req 5.1, 5.2, 5.3).
            if (!StringUtils.hasText(email)) {
                notEmailed.add(toEntry(owner, NotEmailedReason.MISSING_EMAIL));
                continue;
            }

            // Best-effort send: a failure for one recipient must not stop the others (Req 8.3, 8.5).
            try {
                // multipart=true is required so optional attachments can be added.
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true);
                helper.setFrom(fromEmail);
                helper.setTo(email.trim());
                helper.setSubject(assembledSubject);
                // multipart/alternative: plain-text first, HTML second. JavaMail nests the
                // multipart/alternative inside the outer multipart/mixed that carries the
                // attachments, so both readable-in-any-client and attachments work (Req 7.1).
                helper.setText(plainText, assembledHtml);
                attachAll(helper, validAttachments);
                mailSender.send(message);
                sentCount++;
            } catch (Exception e) {
                log.error("Failed to email owner {} ({}): {}",
                        owner.getOwnerId(), owner.getFullName(), e.getMessage());
                notEmailed.add(toEntry(owner, NotEmailedReason.SEND_FAILURE));
            }
        }

        return buildReport(totalAttempted, sentCount, notEmailed, true);
    }

    /**
     * Resolves recipients according to the request scope (Req 2.3, 2.4).
     *
     * <ul>
     *     <li>{@link RecipientScope#ALL}: the active-owner set (same query behind
     *     {@code getActiveOwnersList()}).</li>
     *     <li>{@link RecipientScope#SELECTED}: the owners for the supplied IDs; a
     *     null/empty {@code ownerIds} resolves to zero recipients.</li>
     * </ul>
     */
    private List<Owner> resolveRecipients(SendOwnerEmailRequest request) {
        if (request.getRecipientScope() == RecipientScope.SELECTED) {
            List<Long> ownerIds = request.getOwnerIds();
            if (ownerIds == null || ownerIds.isEmpty()) {
                return Collections.emptyList();
            }
            return ownerRepository.findAllById(ownerIds);
        }
        return ownerRepository.findByStatusOrderByFullNameAsc(OwnerStatus.ACTIVE);
    }

    /**
     * Filters the incoming attachment list down to the files that actually carry content.
     * A {@code null} argument, an empty list, or entries with no content all resolve to an
     * empty list, keeping attachments strictly optional.
     */
    private List<MultipartFile> normaliseAttachments(List<MultipartFile> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return Collections.emptyList();
        }
        List<MultipartFile> valid = new ArrayList<>();
        for (MultipartFile file : attachments) {
            if (file != null && !file.isEmpty()) {
                valid.add(file);
            }
        }
        return valid;
    }

    /**
     * Adds every supplied attachment to the message. Uses a {@link ByteArrayResource} so the
     * multipart file's bytes are read once and reused across every recipient's message.
     */
    private void attachAll(MimeMessageHelper helper, List<MultipartFile> attachments) throws Exception {
        for (MultipartFile file : attachments) {
            String filename = StringUtils.hasText(file.getOriginalFilename())
                    ? file.getOriginalFilename()
                    : "attachment";
            String contentType = StringUtils.hasText(file.getContentType())
                    ? file.getContentType()
                    : "application/octet-stream";
            helper.addAttachment(filename, new ByteArrayResource(file.getBytes()), contentType);
        }
    }

    private NotEmailedEntry toEntry(Owner owner, NotEmailedReason reason) {
        return NotEmailedEntry.builder()
                .ownerId(owner.getOwnerId())
                .ownerName(owner.getFullName())
                .reason(reason)
                .build();
    }

    /**
     * Builds the report preserving the invariant
     * {@code sentCount + notEmailedCount == totalAttempted} and
     * {@code notEmailed.size() == notEmailedCount} (Req 5.4, 7.1, 8.4).
     */
    private SendReportDTO buildReport(int totalAttempted, int sentCount,
                                      List<NotEmailedEntry> notEmailed, boolean mailConfigured) {
        return SendReportDTO.builder()
                .totalAttempted(totalAttempted)
                .sentCount(sentCount)
                .notEmailedCount(notEmailed.size())
                .mailConfigured(mailConfigured)
                .notEmailed(notEmailed)
                .build();
    }
}
