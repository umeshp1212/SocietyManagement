package com.society.module.owner.dto;

import lombok.Builder;
import lombok.Data;

/**
 * A single resolved recipient that was not emailed, along with a categorized reason.
 *
 * <p>Only the owner id, name, and reason category are exposed; the owner's email
 * address is never echoed back.</p>
 */
@Data
@Builder
public class NotEmailedEntry {
    private Long ownerId;
    private String ownerName;
    private NotEmailedReason reason;
}
