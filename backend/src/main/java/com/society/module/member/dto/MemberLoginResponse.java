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
public class MemberLoginResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long ownerId;
    private String ownerName;
    private String phone;
    private String email;
    private List<MemberUnitInfo> units;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberUnitInfo {
        private Long unitId;
        private String unitNumber;
        private String wing;
        private String floor;
    }
}
