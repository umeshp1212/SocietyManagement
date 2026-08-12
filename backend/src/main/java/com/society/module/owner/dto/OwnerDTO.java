package com.society.module.owner.dto;

import com.society.enums.OwnerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerDTO {
    private Long ownerId;
    private String fullName;
    private String contactNumber;
    private String alternateNumber;
    private String email;
    private String aadharNumber;
    private String panNumber;
    private String permanentAddress;
    private String occupation;
    private String photoPath;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private OwnerStatus status;
    private String unitNumbers;
    private String createdBy;
    private LocalDateTime createdOn;
    private String modifiedBy;
    private LocalDateTime modifiedOn;
}
