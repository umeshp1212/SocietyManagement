package com.society.module.owner.dto;

/**
 * Identifies which owners an email should be sent to.
 *
 * <ul>
 *     <li>{@link #ALL} — every active owner, resolved server-side.</li>
 *     <li>{@link #SELECTED} — only the owners whose IDs are supplied in the request.</li>
 * </ul>
 */
public enum RecipientScope {
    ALL,
    SELECTED
}
