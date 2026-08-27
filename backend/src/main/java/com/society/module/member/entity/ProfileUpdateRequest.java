package com.society.module.member.entity;

import com.society.common.BaseEntity;
import com.society.module.owner.entity.Owner;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "profile_update_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileUpdateRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private Owner owner;

    /**
     * Which field is being changed: MOBILE, EMAIL, BOTH
     */
    @Column(name = "field_type", nullable = false, length = 20)
    private String fieldType;

    // Old values (masked for privacy in DTOs, stored plain for admin)
    @Column(name = "old_mobile", length = 15)
    private String oldMobile;

    @Column(name = "new_mobile", length = 15)
    private String newMobile;

    @Column(name = "old_email", length = 100)
    private String oldEmail;

    @Column(name = "new_email", length = 100)
    private String newEmail;

    @Column(name = "reason", length = 500)
    private String reason;

    /**
     * PENDING, APPROVED, REJECTED
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_on")
    private LocalDateTime reviewedOn;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;
}
