package com.society.enums;

/**
 * Two-stage remittance lifecycle for deducted TDS.
 *
 * <p>Ordinal order encodes forward-only progression:
 * {@code DEDUCTED(0) -> PAID_TO_ACCOUNTANT(1) -> PAID_TO_IT_DEPARTMENT(2)}. Transitions
 * may only move forward along this ordering (Requirement 7.1); backward or skipping
 * transitions are rejected.
 *
 * <p>{@link #DEDUCTED} is the implicit default status of a deducted-TDS line that has not
 * yet been attached to a remittance batch. It is never persisted on a {@code TdsRemittance}
 * batch row; a batch is created directly in the {@link #PAID_TO_ACCOUNTANT} stage. The
 * {@code DEDUCTED} state is derived (e.g. via COALESCE) for lines with no associated batch
 * (Requirement 1.5).
 */
public enum TdsRemittanceStatus {
    /** TDS deducted at voucher payment; not yet handed to accountant (implicit default, never persisted on a batch). */
    DEDUCTED,

    /** Society has paid the deducted TDS amount to the accountant. */
    PAID_TO_ACCOUNTANT,

    /** Accountant has remitted the TDS to the Income Tax Department on behalf of the society. */
    PAID_TO_IT_DEPARTMENT
}
