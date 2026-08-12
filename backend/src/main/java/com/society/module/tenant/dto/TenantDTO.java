package com.society.module.tenant.dto;

import com.society.enums.NocStatus;
import com.society.enums.PoliceVerificationStatus;
import com.society.enums.TenantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantDTO {
    private Long tenantId;
    private Long unitId;
    private String unitNumber;
    private String ownerName;
    private String tenantName;
    private String contactNumber;
    private String email;
    private String aadharNumber;
    private String panNumber;
    private String permanentAddress;
    private String photoPath;
    private LocalDate rentStartDate;
    private LocalDate rentEndDate;
    private BigDecimal monthlyRentAmount;
    private BigDecimal securityDeposit;
    private String agreementDocumentPath;
    private PoliceVerificationStatus policeVerificationStatus;
    private String policeVerificationDocumentPath;
    private NocStatus nocStatus;
    private String nocDocumentPath;
    private String nocApprovedBy;
    private LocalDateTime nocApprovedOn;
    private TenantStatus status;
    private LocalDate moveOutDate;
    private String moveOutReason;
    private List<FamilyMemberDTO> familyMembers;
    private List<VehicleDTO> vehicles;
    private List<TenantDocumentDTO> documents;
    private String createdBy;
    private LocalDateTime createdOn;
    // Computed
    private Long daysUntilAgreementExpiry;
    private Boolean isAgreementExpired;
}
