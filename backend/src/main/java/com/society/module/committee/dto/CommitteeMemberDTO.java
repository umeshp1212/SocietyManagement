package com.society.module.committee.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitteeMemberDTO {
    private Long memberId;
    private String fullName;
    private String designation;
    private String photoPath;
    private String phone;
    private String email;
    private Integer displayOrder;
    private Boolean isActive;
    private String createdBy;
    private LocalDateTime createdOn;
    private String modifiedBy;
    private LocalDateTime modifiedOn;
}
