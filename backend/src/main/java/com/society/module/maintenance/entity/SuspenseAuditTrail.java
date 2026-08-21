package com.society.module.maintenance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "suspense_audit_trail")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuspenseAuditTrail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suspense_id", nullable = false)
    private SuspenseEntry suspenseEntry;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private SuspenseAction action;

    @Column(name = "unit_id")
    private Long unitId;

    @Column(name = "unit_number", length = 20)
    private String unitNumber;

    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;

    @Column(name = "performed_on", nullable = false)
    @Builder.Default
    private LocalDateTime performedOn = LocalDateTime.now();

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    public enum SuspenseAction {
        CREATED,
        ASSIGNED,
        REVERSED,
        REASSIGNED
    }
}
