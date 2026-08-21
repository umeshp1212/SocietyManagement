package com.society.module.maintenance.entity;

import com.society.common.BaseEntity;
import com.society.enums.PaymentMode;
import com.society.module.owner.entity.Unit;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "suspense_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuspenseEntry extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "suspense_id")
    private Long suspenseId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode")
    private PaymentMode paymentMode;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private SuspenseStatus status = SuspenseStatus.UNASSIGNED;

    // Assignment details
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_unit_id")
    private Unit assignedToUnit;

    @Column(name = "assigned_by", length = 100)
    private String assignedBy;

    @Column(name = "assigned_on")
    private LocalDateTime assignedOn;

    @Column(name = "assignment_remarks", columnDefinition = "TEXT")
    private String assignmentRemarks;

    @Column(name = "apply_to_opening_balance")
    @Builder.Default
    private Boolean applyToOpeningBalance = false;

    // Audit trail
    @OneToMany(mappedBy = "suspenseEntry", cascade = CascadeType.ALL)
    @Builder.Default
    private List<SuspenseAuditTrail> auditTrails = new ArrayList<>();

    public enum SuspenseStatus {
        UNASSIGNED,
        ASSIGNED,
        REVERSED
    }
}
