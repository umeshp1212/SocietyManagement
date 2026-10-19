package com.society.module.owner.service;

import com.society.module.owner.dto.EmailJobDTO;
import com.society.module.owner.dto.SendOwnerEmailRequest;
import com.society.module.owner.dto.SendReportDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Service contract for sending emails to society owners.
 *
 * <p>Resolves recipients according to the request's {@code recipientScope},
 * assembles the templated email, performs a best-effort per-recipient send,
 * and returns a {@link SendReportDTO} describing the outcome.</p>
 */
public interface OwnerEmailService {

    /**
     * Sends an email to the owners resolved from the given request and reports the outcome.
     *
     * @param request the send request containing recipient scope, optional owner IDs,
     *                subject and body
     * @return a report reconciling attempted, sent and not-emailed recipients
     */
    SendReportDTO sendOwnerEmail(SendOwnerEmailRequest request);

    /**
     * Sends an email to the owners resolved from the given request, optionally including
     * one or more file attachments, and reports the outcome.
     *
     * <p>Attachments are entirely optional: a {@code null} or empty {@code attachments}
     * list produces a plain email identical to {@link #sendOwnerEmail(SendOwnerEmailRequest)}.</p>
     *
     * @param request     the send request containing recipient scope, optional owner IDs,
     *                    subject and body
     * @param attachments optional files to attach to every outgoing email; may be {@code null} or empty
     * @return a report reconciling attempted, sent and not-emailed recipients
     */
    SendReportDTO sendOwnerEmail(SendOwnerEmailRequest request, List<MultipartFile> attachments);

    /**
     * Validates and prepares an owner-email send, then dispatches the per-recipient
     * delivery to a background executor and returns immediately.
     *
     * <p>All fast-failing work happens synchronously on the caller's thread so invalid
     * requests still surface as an error to the HTTP client: recipients are resolved,
     * the body is sanitized and validated, the template is assembled, and any attachment
     * bytes are read into memory (the {@link MultipartFile} instances are not usable once
     * the request completes). A {@link com.society.exception.BusinessException} thrown here
     * propagates to the controller and is mapped to a 400, and no job is created.</p>
     *
     * <p>Once preparation succeeds a job is registered and the actual SMTP loop runs on the
     * background executor. The returned {@link EmailJobDTO} carries the job id the client
     * polls (via {@link #getJob(String)}) and starts in
     * {@link com.society.module.owner.dto.EmailJobStatus#PENDING}.</p>
     *
     * @param request     the send request (scope, optional owner IDs, subject, body)
     * @param attachments optional files to attach to every outgoing email; may be {@code null} or empty
     * @return a pollable job handle describing the accepted send
     */
    EmailJobDTO submitOwnerEmail(SendOwnerEmailRequest request, List<MultipartFile> attachments);

    /**
     * Returns the current state of a previously submitted send job.
     *
     * @param jobId the id returned by {@link #submitOwnerEmail}
     * @return the job snapshot, or empty if the id is unknown or its result has expired
     */
    java.util.Optional<EmailJobDTO> getJob(String jobId);
}
