package com.society.module.owner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerUpdateRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 150, message = "Full name must not exceed 150 characters")
    private String fullName;

    @NotBlank(message = "Contact number is required")
    @Size(max = 15, message = "Contact number must not exceed 15 characters")
    private String contactNumber;

    @Size(max = 15, message = "Alternate number must not exceed 15 characters")
    private String alternateNumber;

    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;

    private String aadharNumber;

    @Size(max = 20, message = "PAN number must not exceed 20 characters")
    private String panNumber;

    private String permanentAddress;

    @Size(max = 100, message = "Occupation must not exceed 100 characters")
    private String occupation;

    @Size(max = 150, message = "Emergency contact name must not exceed 150 characters")
    private String emergencyContactName;

    @Size(max = 15, message = "Emergency contact phone must not exceed 15 characters")
    private String emergencyContactPhone;
}
