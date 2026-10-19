package com.society.module.owner.service;

import com.society.module.owner.dto.EmailJobDTO;
import com.society.module.owner.dto.EmailJobStatus;
import com.society.module.owner.dto.SendReportDTO;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mutable, thread-safe server-side state for a single asynchronous owner-email send.
 *
 * <p>Created when a send request is accepted and mutated by the background send loop
 * running on the {@code ownerEmailExecutor}. Reads happen concurrently from polling
 * HTTP requests, so all mutable fields are {@code volatile} / atomic and the
 * {@link #toDto()} snapshot is consistent enough for progress reporting.</p>
 */
public class EmailJob {

    private final String jobId;
    private final int totalRecipients;
    private final Instant createdAt;

    /**
     * The prepared, request-independent payload this job must deliver. Set by the submit
     * path before the background loop starts; the async worker reads it back by job id so
     * no package-private type has to cross the service proxy boundary.
     */
    private volatile OwnerEmailServiceImpl.PreparedEmail prepared;

    private volatile EmailJobStatus status;
    private final AtomicInteger processed = new AtomicInteger(0);
    private volatile SendReportDTO report;
    private volatile String error;
    private volatile Instant updatedAt;

    EmailJob(String jobId, int totalRecipients) {
        this.jobId = jobId;
        this.totalRecipients = totalRecipients;
        this.status = EmailJobStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    String getJobId() {
        return jobId;
    }

    OwnerEmailServiceImpl.PreparedEmail getPrepared() {
        return prepared;
    }

    void setPrepared(OwnerEmailServiceImpl.PreparedEmail prepared) {
        this.prepared = prepared;
    }

    void markInProgress() {
        this.status = EmailJobStatus.IN_PROGRESS;
        touch();
    }

    void incrementProcessed() {
        processed.incrementAndGet();
        touch();
    }

    void markCompleted(SendReportDTO report) {
        this.report = report;
        this.status = EmailJobStatus.COMPLETED;
        touch();
    }

    void markFailed(String error) {
        this.error = error;
        this.status = EmailJobStatus.FAILED;
        touch();
    }

    EmailJobStatus getStatus() {
        return status;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    /** Point-in-time snapshot for the polling API. */
    EmailJobDTO toDto() {
        return EmailJobDTO.builder()
                .jobId(jobId)
                .status(status)
                .totalRecipients(totalRecipients)
                .processed(processed.get())
                .report(report)
                .error(error)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }
}
