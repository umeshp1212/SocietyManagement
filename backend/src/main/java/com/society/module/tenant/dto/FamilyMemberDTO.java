package com.society.module.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FamilyMemberDTO {
    private Long memberId;
    private String memberName;
    private Integer age;
    private String relation;
    private String aadharNumber;
    private String contactNumber;
}
