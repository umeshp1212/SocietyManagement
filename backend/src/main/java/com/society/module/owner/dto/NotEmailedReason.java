package com.society.module.owner.dto;

/**
 * Categorized reason why a resolved recipient was not emailed.
 *
 * <ul>
 *     <li>{@link #MISSING_EMAIL} — owner has a null or blank email address.</li>
 *     <li>{@link #SEND_FAILURE} — mail transport threw while sending to this recipient.</li>
 *     <li>{@link #MAIL_NOT_CONFIGURED} — no mail sender is configured, so nothing was sent.</li>
 * </ul>
 */
public enum NotEmailedReason {
    MISSING_EMAIL,
    SEND_FAILURE,
    MAIL_NOT_CONFIGURED
}
