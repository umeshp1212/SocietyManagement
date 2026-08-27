package com.society.module.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberProfileDTO {

    private Long ownerId;
    private String fullName;
    private String maskedMobile;
    private String maskedEmail;
    private String unitNumber;
    private String wing;
    private String floor;

    // Whether there's a pending update request
    private boolean hasPendingRequest;

    // History of update requests
    private List<ProfileUpdateRequestDTO> updateRequests;
}
