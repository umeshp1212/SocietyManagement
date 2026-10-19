package com.society.module.owner.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Pollable view of an asynchronous owner-email send job.
 *
 * <p>Returned by the accept endpoint (with {@code status = PENDING} and no report)
 * and by the job-status endpoint. Once {@code status} reaches {@link EmailJobStatus#COMPLETED}
 * the {@code report} is populated; on {@link EmailJobStatus#FAILED} the {@code error}
 * carries a client-facing message and {@code report} is {@code null}.</p>
 */
@Data
@Builder
public class EmailJobDTO {

    /** Opaque identifier the client polls with. */
    private String jobId;

    private EmailJobStatus status;

    /** Number of recipients resolved for this job (known as soon as the job is accepted). */
    private int totalRecipients;

    /** Recipients attempted so far; grows while {@link EmailJobStatus#IN_PROGRESS}. */
    private int processed;

    /** Final outcome; non-null only when {@code status == COMPLETED}. */
    private SendReportDTO report;

    /** Client-facing error message; non-null only when {@code status == FAILED}. */
    private String error;

    private Instant createdAt;
    private Instant updatedAt;
}
