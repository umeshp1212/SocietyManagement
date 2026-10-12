package com.society.module.visitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read model for a visitor/delivery gate request. Used by the guard (gate-portal),
 * the member portal (owner/tenant approval list), and the admin gate log.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitorRequestDTO {

    private Long visitorRequestId;

    private Long unitId;
    private String unitNumber;
    private String wing;
    private String floor;

    private String visitorName;
    private String company;
    private String purpose;

    /** Relative upload path to the visitor photo (null if none). */
    private String photoPath;

    private String status;

    /** Guard who created the request (from JPA auditing createdBy). */
    private String createdBy;
    private LocalDateTime createdOn;

    private String decidedBy;
    private LocalDateTime decidedOn;
    private String rejectionReason;

    private LocalDateTime expiresAt;

    /**
     * Names of the owners/tenant notified for this request (for the guard's context).
     * Populated on create; not persisted.
     */
    private List<String> notifiedRecipients;
}
