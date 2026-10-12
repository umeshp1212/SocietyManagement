package com.society.module.visitor.entity;

import com.society.common.BaseEntity;
import com.society.enums.VisitorStatus;
import com.society.module.owner.entity.Unit;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A gate-entry request for an incoming visitor or delivery person, raised by a
 * security guard against a specific flat (unit) and decided by that flat's
 * owner(s) or active tenant.
 *
 * <p>The guard who created the request is recorded via {@code createdBy} (populated
 * by JPA auditing from the authenticated principal). The first owner/tenant decision
 * wins: it stamps {@code status}, {@code decidedBy}, and {@code decidedOn}; a later
 * decision attempt on an already-decided request is rejected at the service layer.
 * A scheduler flips still-PENDING requests past {@code expiresAt} to
 * {@link VisitorStatus#TIMED_OUT}.</p>
 */
@Entity
@Table(name = "visitor_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisitorRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "visitor_request_id")
    private Long visitorRequestId;

    /** The flat the visitor/delivery is destined for. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    /** Name of the delivery person / visitor as stated at the gate. */
    @Column(name = "visitor_name", nullable = false, length = 150)
    private String visitorName;

    /** Delivery company / courier (e.g. "Amazon", "Swiggy"), optional for plain visitors. */
    @Column(name = "company", length = 150)
    private String company;

    /** Free-text purpose (e.g. "parcel", "food delivery"), optional. */
    @Column(name = "purpose", length = 255)
    private String purpose;

    /**
     * Relative path (under the configured upload dir) to the photo of the visitor/ID
     * captured by the guard. Served publicly via {@code /files/view/**}.
     */
    @Column(name = "photo_path", length = 500)
    private String photoPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private VisitorStatus status = VisitorStatus.PENDING;

    /** Username/identifier of the owner/tenant who decided (first-wins). */
    @Column(name = "decided_by", length = 150)
    private String decidedBy;

    @Column(name = "decided_on")
    private LocalDateTime decidedOn;

    /** Reason supplied when an owner/tenant rejects entry. */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /**
     * Instant at which a still-PENDING request should auto-expire to TIMED_OUT.
     * Derived from the configurable {@code visitorApprovalTimeoutMinutes} setting
     * at creation time.
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
