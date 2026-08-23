package com.society.module.committee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommitteeMemberUpdateRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 150)
    private String fullName;

    @NotBlank(message = "Designation is required")
    @Size(max = 100)
    private String designation;

    @Size(max = 15)
    private String phone;

    @Size(max = 100)
    private String email;

    private Integer displayOrder;

    private Boolean isActive;
}
