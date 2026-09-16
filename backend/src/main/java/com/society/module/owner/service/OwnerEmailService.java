package com.society.module.owner.service;

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
}
