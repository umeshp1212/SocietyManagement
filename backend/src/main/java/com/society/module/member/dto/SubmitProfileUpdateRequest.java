package com.society.module.member.dto;

import lombok.Data;

@Data
public class SubmitProfileUpdateRequest {
    private String newMobile;
    private String newEmail;
    private String reason;
}
