package com.society.module.visitor.service;

import com.society.module.visitor.dto.VisitorRecipient;
import com.society.module.visitor.entity.VisitorRequest;

import java.util.List;

/**
 * Port the visitor service uses to notify a flat's owners/tenant that a new
 * delivery/visitor is waiting at the gate. The real adapter (Task 6) sends an FCM
 * web-push to each recipient's registered device tokens; it must degrade gracefully
 * (no-op) when push is not configured so the in-app approval flow still works.
 *
 * <p>Kept as an interface so the create-request flow (Task 3) does not depend on the
 * notification module directly and remains unit-testable without any push backend.</p>
 */
public interface VisitorNotifier {

    /**
     * Notify the given recipients about a newly-created pending request. Implementations
     * must never throw: a notification failure must not roll back or block the gate
     * request, which is already persisted and visible in-app.
     */
    void notifyNewRequest(VisitorRequest request, List<VisitorRecipient> recipients);
}
