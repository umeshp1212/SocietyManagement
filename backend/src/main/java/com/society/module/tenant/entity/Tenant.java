package com.society.module.tenant.entity;

import com.society.common.BaseEntity;
import com.society.enums.NocStatus;
import com.society.enums.PoliceVerificationStatus;
import com.society.enums.TenantStatus;
import com.society.module.owner.entity.Unit;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tenant_id")
    private Long tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Column(name = "tenant_name", nullable = false, length = 150)
    private String tenantName;

    @Column(name = "contact_number", nullable = false, length = 15)
    private String contactNumber;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "aadhar_number", length = 255)
    private String aadharNumber;

    @Column(name = "pan_number", length = 20)
    private String panNumber;

    @Column(name = "permanent_address", columnDefinition = "TEXT")
    private String permanentAddress;

    @Column(name = "photo_path", length = 500)
    private String photoPath;

    @Column(name = "rent_start_date", nullable = false)
    private LocalDate rentStartDate;

    @Column(name = "rent_end_date")
    private LocalDate rentEndDate;

    @Column(name = "monthly_rent_amount", precision = 10, scale = 2)
    private BigDecimal monthlyRentAmount;

    @Column(name = "security_deposit", precision = 10, scale = 2)
    private BigDecimal securityDeposit;

    @Column(name = "agreement_document_path", length = 500)
    private String agreementDocumentPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "police_verification_status", nullable = false)
    private PoliceVerificationStatus policeVerificationStatus = PoliceVerificationStatus.NOT_INITIATED;

    @Column(name = "police_verification_document_path", length = 500)
    private String policeVerificationDocumentPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "noc_status", nullable = false)
    private NocStatus nocStatus = NocStatus.PENDING;

    @Column(name = "noc_document_path", length = 500)
    private String nocDocumentPath;

    @Column(name = "noc_approved_by", length = 100)
    private String nocApprovedBy;

    @Column(name = "noc_approved_on")
    private LocalDateTime nocApprovedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TenantStatus status = TenantStatus.ACTIVE;

    @Column(name = "move_out_date")
    private LocalDate moveOutDate;

    @Column(name = "move_out_reason", length = 500)
    private String moveOutReason;

    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TenantFamilyMember> familyMembers = new ArrayList<>();

    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TenantVehicle> vehicles = new ArrayList<>();

    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TenantDocument> documents = new ArrayList<>();
}
