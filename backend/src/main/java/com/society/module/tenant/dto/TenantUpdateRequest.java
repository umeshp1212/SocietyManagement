package com.society.module.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantUpdateRequest {

    @NotBlank(message = "Tenant name is required")
    @Size(max = 150, message = "Tenant name must not exceed 150 characters")
    private String tenantName;

    @NotBlank(message = "Contact number is required")
    @Size(max = 15, message = "Contact number must not exceed 15 characters")
    private String contactNumber;

    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;

    private String aadharNumber;

    @Size(max = 20, message = "PAN must not exceed 20 characters")
    private String panNumber;

    private String permanentAddress;

    private LocalDate rentEndDate;

    private BigDecimal monthlyRentAmount;

    private BigDecimal securityDeposit;

    private List<FamilyMemberDTO> familyMembers;

    private List<VehicleDTO> vehicles;
}
