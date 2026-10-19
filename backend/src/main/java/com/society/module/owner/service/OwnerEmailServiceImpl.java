package com.society.module.owner.service;

import com.society.enums.OwnerStatus;
import com.society.exception.BusinessException;
import com.society.module.owner.dto.EmailJobDTO;
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
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
 * <p><b>Asynchronous delivery.</b> {@link #submitOwnerEmail} performs all fast-failing
 * preparation (recipient resolution, sanitization, validation, template assembly, and
 * reading attachment bytes into memory) synchronously on the request thread, then hands
 * the actual SMTP loop to the {@code ownerEmailExecutor} and returns a pollable job id.
 * This keeps bad requests failing fast with a 400 while a large recipient set no longer
 * blocks the HTTP response past the client's request timeout. The pre-existing synchronous
 * {@link #sendOwnerEmail} methods are retained for callers/tests that want the inline
 * behaviour and share the same preparation and send-loop code.</p>
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
    private final EmailJobStore emailJobStore;

    /**
     * Self-reference through the Spring proxy. {@code @Async} advice is applied by the proxy,
     * so the background send must be invoked via {@code self.runSendLoopAsync(...)} rather than
     * {@code this.runSendLoopAsync(...)} (a direct {@code this} call would run inline). Typed as
     * the {@link OwnerEmailService} interface — the bean is exposed as a JDK dynamic proxy of the
     * interface, not a subclass of this concrete type, so injecting the concrete type would fail.
     * Injected lazily to break the self-referential construction cycle.
     */
    @Lazy
    @Autowired
    private OwnerEmailService self;

    @Override
    public SendReportDTO sendOwnerEmail(SendOwnerEmailRequest request) {
        return sendOwnerEmail(request, null);
    }

    @Override
    public SendReportDTO sendOwnerEmail(SendOwnerEmailRequest request, List<MultipartFile> attachments) {
        PreparedEmail prepared = prepare(request, attachments);
        return runSendLoop(prepared, null);
    }

    @Override
    public EmailJobDTO submitOwnerEmail(SendOwnerEmailRequest request, List<MultipartFile> attachments) {
        // Prepare synchronously so validation/sanitization failures surface to the HTTP
        // client as a 400 and no job is created for a request that could never send.
        PreparedEmail prepared = prepare(request, attachments);

        EmailJob job = emailJobStore.create(prepared.recipients.size());
        job.setPrepared(prepared);
        // Dispatch the SMTP loop to the background executor. The self-injected proxy
        // reference ensures the @Async advice is applied (a direct this.* call would not).
        // Only the job id crosses the call so no package-private type touches the interface.
        self.runSendLoopAsync(job.getJobId());
        return job.toDto();
    }

    @Override
    public Optional<EmailJobDTO> getJob(String jobId) {
        return emailJobStore.find(jobId).map(EmailJob::toDto);
    }

    /**
     * Background entry point for the per-recipient send loop. Runs on the dedicated
     * {@code ownerEmailExecutor}; failures are captured on the job rather than thrown
     * (nothing awaits this method's completion). Looks the job up by id so the method
     * signature on the interface carries only a {@code String}.
     */
    @Override
    @Async("ownerEmailExecutor")
    public void runSendLoopAsync(String jobId) {
        EmailJob job = emailJobStore.find(jobId).orElse(null);
        if (job == null) {
            log.warn("Owner email job {} not found when the background send started.", jobId);
            return;
        }
        PreparedEmail prepared = job.getPrepared();
        if (prepared == null) {
            log.error("Owner email job {} has no prepared payload; aborting send.", jobId);
            job.markFailed("Internal error: prepared email missing");
            return;
        }
        try {
            job.markInProgress();
            SendReportDTO report = runSendLoop(prepared, job);
            job.markCompleted(report);
        } catch (Exception e) {
            log.error("Owner email job {} failed: {}", jobId, e.getMessage(), e);
            job.markFailed("Email sending failed: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------------
    // Preparation (synchronous, fast-failing)
    // ---------------------------------------------------------------------------

    /**
     * Resolves recipients, sanitizes and validates the body, assembles the HTML and
     * plain-text templates, and materialises attachment bytes. All failures here are
     * client-facing {@link BusinessException}s thrown before any send is attempted
     * (Req 3.7, 4.4, 4.5, 4.6).
     */
    private PreparedEmail prepare(SendOwnerEmailRequest request, List<MultipartFile> attachments) {
        // Attachment bytes must be read now: the MultipartFile instances are tied to the
        // request and are not usable from a background thread after the request completes.
        List<Attachment> materialisedAttachments = materialiseAttachments(attachments);

        // Resolve the authoritative recipient set server-side (Req 2.3, 2.4).
        List<Owner> recipients = resolveRecipients(request);

        // Authoritative server-side sanitization, once per request, before any template
        // assembly or send. Only the Sanitized_Body is ever embedded; the raw
        // Rich_Text_Body is never used past this point (Req 3.1, 3.6).
        String sanitizedBody;
        String visible;
        try {
            sanitizedBody = sanitizer.sanitize(request.getBody());
            visible = sanitizer.visibleText(sanitizedBody);
        } catch (Exception e) {
            log.error("Failed to sanitize owner email body: {}", e.getMessage());
            throw new BusinessException("Message content could not be processed"); // Req 3.7
        }

        // Visible-text validation on the sanitized body, whatever will actually be sent.
        if (visible.isEmpty()) {
            throw new BusinessException("Message content is required"); // Req 4.4
        }
        if (visible.length() > 10_000) {
            throw new BusinessException("Message exceeds the 10,000 character limit"); // Req 4.5
        }
        if (sanitizedBody.length() > 50_000) {
            throw new BusinessException("Message body is too large"); // Req 4.6 (defence in depth)
        }

        // Assemble the template up-front from the SANITIZED body. The HTML template preserves
        // the allowed formatting (Req 2.2, 2.3) and the plain-text alternative is derived from
        // the same sanitized content (Req 2.4, 7.1, 7.2, 7.3).
        SocietySettings settings = societySettingsService.getSettings();
        String assembledSubject = templateBuilder.buildSubject(request.getSubject());
        String assembledHtml = templateBuilder.buildHtmlBody(settings, request.getSubject(), sanitizedBody);
        String plainText;
        try {
            plainText = plainTextRenderer.render(sanitizedBody, request.getSubject(), settings);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to derive plain-text alternative for owner email: {}", e.getMessage());
            throw new BusinessException("Message content could not be processed"); // Req 3.7
        }

        return new PreparedEmail(recipients, assembledSubject, assembledHtml, plainText, materialisedAttachments);
    }

    // ---------------------------------------------------------------------------
    // Send loop (shared by the synchronous and asynchronous paths)
    // ---------------------------------------------------------------------------

    /**
     * Performs the best-effort per-recipient send for an already-prepared email and builds
     * the report. When {@code job} is non-null its processed counter is advanced per
     * recipient so pollers see progress. A per-recipient failure never halts the rest
     * (Req 8.3, 8.5).
     */
    private SendReportDTO runSendLoop(PreparedEmail prepared, EmailJob job) {
        int totalAttempted = prepared.recipients.size();

        // Mail transport absent: send nothing, report every attempted recipient as
        // MAIL_NOT_CONFIGURED, and return normally without an error (Req 8.1, 8.2).
        if (mailSender == null) {
            List<NotEmailedEntry> notEmailed = new ArrayList<>();
            for (Owner owner : prepared.recipients) {
                notEmailed.add(toEntry(owner, NotEmailedReason.MAIL_NOT_CONFIGURED));
                if (job != null) {
                    job.incrementProcessed();
                }
            }
            log.warn("Mail sender not configured. Owner email not sent to {} attempted recipient(s).",
                    totalAttempted);
            return buildReport(totalAttempted, 0, notEmailed, false);
        }

        int sentCount = 0;
        List<NotEmailedEntry> notEmailed = new ArrayList<>();

        for (Owner owner : prepared.recipients) {
            String email = owner.getEmail();

            // Invalid recipient: null/blank email is never sent to (Req 5.1, 5.2, 5.3).
            if (!StringUtils.hasText(email)) {
                notEmailed.add(toEntry(owner, NotEmailedReason.MISSING_EMAIL));
                if (job != null) {
                    job.incrementProcessed();
                }
                continue;
            }

            try {
                // multipart=true is required so optional attachments can be added.
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true);
                helper.setFrom(fromEmail);
                helper.setTo(email.trim());
                helper.setSubject(prepared.subject);
                // multipart/alternative: plain-text first, HTML second (Req 7.1).
                helper.setText(prepared.plainText, prepared.html);
                attachAll(helper, prepared.attachments);
                mailSender.send(message);
                sentCount++;
            } catch (Exception e) {
                log.error("Failed to email owner {} ({}): {}",
                        owner.getOwnerId(), owner.getFullName(), e.getMessage());
                notEmailed.add(toEntry(owner, NotEmailedReason.SEND_FAILURE));
            } finally {
                if (job != null) {
                    job.incrementProcessed();
                }
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
     * Reads every non-empty attachment's bytes into memory up-front so they can be reused
     * across every recipient's message and, critically, remain usable after the originating
     * HTTP request has completed (the background send runs on another thread).
     */
    private List<Attachment> materialiseAttachments(List<MultipartFile> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return Collections.emptyList();
        }
        List<Attachment> valid = new ArrayList<>();
        for (MultipartFile file : attachments) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String filename = StringUtils.hasText(file.getOriginalFilename())
                    ? file.getOriginalFilename()
                    : "attachment";
            String contentType = StringUtils.hasText(file.getContentType())
                    ? file.getContentType()
                    : "application/octet-stream";
            try {
                valid.add(new Attachment(filename, contentType, file.getBytes()));
            } catch (Exception e) {
                throw new BusinessException("Failed to read attachment '" + filename + "': " + e.getMessage());
            }
        }
        return valid;
    }

    /** Adds every materialised attachment to the message. */
    private void attachAll(MimeMessageHelper helper, List<Attachment> attachments) throws Exception {
        for (Attachment file : attachments) {
            helper.addAttachment(file.filename(), new ByteArrayResource(file.bytes()), file.contentType());
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

    /** Immutable, request-independent snapshot of everything a send needs. */
    static final class PreparedEmail {
        final List<Owner> recipients;
        final String subject;
        final String html;
        final String plainText;
        final List<Attachment> attachments;

        PreparedEmail(List<Owner> recipients, String subject, String html, String plainText,
                      List<Attachment> attachments) {
            this.recipients = recipients;
            this.subject = subject;
            this.html = html;
            this.plainText = plainText;
            this.attachments = attachments;
        }
    }

    /** An attachment with its bytes already read, safe to use off the request thread. */
    record Attachment(String filename, String contentType, byte[] bytes) {
    }
}
