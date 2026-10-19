package com.society.module.owner.dto;

/**
 * Lifecycle status of an asynchronous owner-email send job.
 *
 * <ul>
 *     <li>{@link #PENDING}: accepted and queued, send loop not yet started.</li>
 *     <li>{@link #IN_PROGRESS}: the background send loop is running.</li>
 *     <li>{@link #COMPLETED}: the send loop finished; a {@link SendReportDTO} is available.</li>
 *     <li>{@link #FAILED}: the job aborted before producing a report (unexpected error).</li>
 * </ul>
 */
public enum EmailJobStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
