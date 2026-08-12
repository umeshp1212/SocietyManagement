package com.society.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 150)
    private String fullName;

    @Size(max = 100)
    private String email;

    @Size(max = 15)
    private String phone;

    private List<String> roles;

    private Long ownerId;

    private Long tenantId;
}
