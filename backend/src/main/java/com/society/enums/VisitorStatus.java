package com.society.enums;

/**
 * Lifecycle of a visitor/delivery gate-entry request.
 *
 * <ul>
 *   <li>{@code PENDING} — created by a guard, awaiting an owner/tenant decision.</li>
 *   <li>{@code APPROVED} — an owner/tenant allowed entry (first decision wins).</li>
 *   <li>{@code REJECTED} — an owner/tenant denied entry (first decision wins).</li>
 *   <li>{@code TIMED_OUT} — no decision before the configurable timeout elapsed;
 *       set automatically by the auto-expiry scheduler.</li>
 * </ul>
 */
public enum VisitorStatus {
    PENDING,
    APPROVED,
    REJECTED,
    TIMED_OUT
}
