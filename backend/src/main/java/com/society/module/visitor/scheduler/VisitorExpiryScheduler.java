package com.society.module.visitor.scheduler;

import com.society.module.visitor.service.VisitorRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically auto-expires still-PENDING visitor/delivery requests whose configurable
 * timeout has elapsed, flipping them to TIMED_OUT so stale requests don't linger on the
 * guard's screen or the owner's approval list.
 *
 * <p>The per-request timeout itself is the configurable
 * {@code visitorApprovalTimeoutMinutes} setting (applied at creation time, Task 2/3).
 * This job only needs to run often enough to pick up expired requests promptly; its
 * fixed delay is configurable via {@code app.visitor.expiry-scan-ms} (default 60s).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VisitorExpiryScheduler {

    private final VisitorRequestService service;

    @Scheduled(fixedDelayString = "${app.visitor.expiry-scan-ms:60000}")
    public void expireOverdueRequests() {
        try {
            int expired = service.expireOverdueRequests();
            if (expired > 0) {
                log.debug("Visitor expiry scan flipped {} request(s) to TIMED_OUT.", expired);
            }
        } catch (RuntimeException e) {
            // A scheduled scan failure must never crash the scheduler thread.
            log.warn("Visitor expiry scan failed: {}", e.toString());
        }
    }
}
