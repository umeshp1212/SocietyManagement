package com.society.module.owner.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddCoOwnerRequest {

    @NotNull(message = "Unit ID is required")
    private Long unitId;

    @NotNull(message = "Owner ID is required")
    private Long ownerId;

    @NotNull(message = "Primary flag is required")
    private Boolean isPrimary;

    @NotNull(message = "Ownership percentage is required")
    private BigDecimal ownershipPercentage;
}
