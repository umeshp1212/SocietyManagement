package com.society.module.tenant.dto;

import com.society.enums.NocStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NocApprovalRequest {

    @NotNull(message = "NOC status is required")
    private NocStatus nocStatus;

    private String remarks;
}
