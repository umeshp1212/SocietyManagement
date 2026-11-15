package com.society.exception;

/**
 * Thrown when a payment is NOT a failure but not yet confirmed — the gateway reports the order
 * as still ACTIVE/PENDING (common with UPI, which settles a moment after the browser returns).
 * The webhook will confirm it shortly. Mapped to HTTP 202 (Accepted) so the member portal can
 * show a reassuring "being confirmed" screen instead of "Payment Failed".
 */
public class PaymentPendingException extends RuntimeException {

    public PaymentPendingException(String message) {
        super(message);
    }
}
