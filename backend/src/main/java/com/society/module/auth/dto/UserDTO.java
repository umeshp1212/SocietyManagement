package com.society.module.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long userId;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private Boolean isActive;
    private LocalDateTime lastLogin;
    private LocalDateTime passwordChangedOn;
    private List<String> roles;
    private Long ownerId;
    private Long tenantId;
    private LocalDateTime createdOn;
}
