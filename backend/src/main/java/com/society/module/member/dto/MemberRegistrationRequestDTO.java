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
public class MemberRegistrationRequestDTO {

    private Long requestId;
    private String email;
    private String mobile;
    private Long unitId;
    private String unitNumber;
    private Boolean emailVerified;
    private String status;
    private Long linkedOwnerId;
    private String linkedOwnerName;
    private String reviewedBy;
    private LocalDateTime reviewedOn;
    private String rejectionReason;
    private LocalDateTime createdOn;
}
