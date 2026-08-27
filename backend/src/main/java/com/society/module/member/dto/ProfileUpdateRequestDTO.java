package com.society.module.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileUpdateRequestDTO {

    private Long requestId;
    private Long ownerId;
    private String ownerName;
    private String unitNumber;
    private String fieldType;

    // For member view — masked
    private String oldMobileMasked;
    private String newMobileMasked;
    private String oldEmailMasked;
    private String newEmailMasked;

    // For admin view — full values
    private String oldMobile;
    private String newMobile;
    private String oldEmail;
    private String newEmail;

    private String reason;
    private String status;
    private String reviewedBy;
    private LocalDateTime reviewedOn;
    private String rejectionReason;
    private LocalDateTime createdOn;
}
