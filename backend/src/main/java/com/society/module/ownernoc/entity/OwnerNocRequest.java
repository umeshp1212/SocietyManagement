package com.society.module.ownernoc.entity;

import com.society.common.BaseEntity;
import com.society.enums.OwnerNocStatus;
import com.society.module.owner.entity.Owner;
import com.society.module.owner.entity.Unit;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * An owner-submitted request for a general-purpose No Objection Certificate
 * (loan transfer, property-tax name change, electricity-bill name change,
 * passport/residence certificate, etc.). Approved by an admin / authorized role;
 * on approval a certificate PDF is generated and emailed to the owner.
 */
@Entity
@Table(name = "owner_noc_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnerNocRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private Owner owner;

    /** Optional: the specific unit the NOC concerns (owner may own several). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "noc_type_id", nullable = false)
    private NocType nocType;

    /** Whom the certificate should be addressed to, e.g. "HDFC Bank Ltd." */
    @Column(name = "addressee", length = 255)
    private String addressee;

    /** Free-text purpose / details supplied by the owner (bank/loan account, reason, etc.). */
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    /**
     * Final certificate body used to render the PDF. Pre-filled from the NocType
     * template at approval time; the admin may edit it before issuing.
     */
    @Column(name = "final_content", columnDefinition = "TEXT")
    private String finalContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private OwnerNocStatus status = OwnerNocStatus.PENDING;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_on")
    private LocalDateTime reviewedOn;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /** Path to the generated certificate PDF, if stored. */
    @Column(name = "certificate_path", length = 500)
    private String certificatePath;
}
