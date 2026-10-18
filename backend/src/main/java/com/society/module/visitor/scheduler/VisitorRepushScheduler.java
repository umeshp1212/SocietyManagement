package com.society.module.visitor.scheduler;

import com.society.module.visitor.service.VisitorRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Re-sends ("nags") the push notification for every still-live PENDING visitor/delivery
 * request every few seconds, so the flat owner keeps getting alerted on their phone
 * until they Approve/Reject — or the request auto-expires (TIMED_OUT).
 *
 * <p>This is how a PWA approximates "ring until answered" for a <b>closed</b> app: the
 * browser/OS plays the notification tone (+ vibration) each time a push arrives, so a
 * repeated push produces a repeated alert. It stops automatically once the request
 * leaves PENDING (decided or timed out). The interval is {@code app.visitor.repush-ms}
 * (default 10000ms = every 10 seconds).</p>
 *
 * <p>Note: web push cannot play a continuous custom ringtone for a closed app — this
 * repeated-ping approach is the closest achievable without a native app.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VisitorRepushScheduler {

    private final VisitorRequestService service;

    @Scheduled(fixedDelayString = "${app.visitor.repush-ms:10000}")
    public void repushPendingRequests() {
        try {
            service.repushPendingRequests();
        } catch (RuntimeException e) {
            // A scheduled re-push failure must never crash the scheduler thread.
            log.warn("Visitor re-push scan failed: {}", e.toString());
        }
    }
}
