package com.society.module.owner.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of asynchronous owner-email jobs, keyed by a generated job id.
 *
 * <p>Jobs live only for the lifetime of the JVM; they are a transient handle the UI
 * polls while a bulk send runs in the background, not a durable audit record. A
 * terminal job is retained for {@link #RETENTION} after its last update so the client
 * has time to fetch the final report, then becomes eligible for eviction. Eviction is
 * opportunistic (on access) to avoid a scheduler dependency.</p>
 */
@Component
public class EmailJobStore {

    /** How long a job's result is kept available for polling after its last update. */
    private static final Duration RETENTION = Duration.ofMinutes(30);

    private final ConcurrentHashMap<String, EmailJob> jobs = new ConcurrentHashMap<>();

    /** Creates and registers a new job for the given resolved recipient count. */
    public EmailJob create(int totalRecipients) {
        evictExpired();
        String jobId = UUID.randomUUID().toString();
        EmailJob job = new EmailJob(jobId, totalRecipients);
        jobs.put(jobId, job);
        return job;
    }

    /** Looks up a job by id, if it exists and has not been evicted. */
    public Optional<EmailJob> find(String jobId) {
        evictExpired();
        return Optional.ofNullable(jobs.get(jobId));
    }

    /** Removes terminal jobs whose results have outlived the retention window. */
    private void evictExpired() {
        Instant cutoff = Instant.now().minus(RETENTION);
        jobs.values().removeIf(job -> isTerminal(job) && job.getUpdatedAt().isBefore(cutoff));
    }

    private boolean isTerminal(EmailJob job) {
        return switch (job.getStatus()) {
            case COMPLETED, FAILED -> true;
            default -> false;
        };
    }
}
