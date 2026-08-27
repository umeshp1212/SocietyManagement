package com.society.module.member.entity;

import com.society.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "member_registration_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberRegistrationRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Column(name = "mobile", nullable = false, length = 15)
    private String mobile;

    /**
     * Unit the member claims to belong to.
     */
    @Column(name = "unit_id")
    private Long unitId;

    @Column(name = "unit_number", length = 20)
    private String unitNumber;

    @Column(name = "email_verified")
    @Builder.Default
    private Boolean emailVerified = false;

    /**
     * PENDING, APPROVED, REJECTED
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    /**
     * Admin links this request to an existing owner on approval.
     */
    @Column(name = "linked_owner_id")
    private Long linkedOwnerId;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_on")
    private LocalDateTime reviewedOn;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;
}
