package com.society.module.owner.dto;

import com.society.enums.TransferType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnershipTransferRequest {

    @NotNull(message = "Unit ID is required")
    private Long unitId;

    @NotNull(message = "New owner ID is required")
    private Long newOwnerId;

    @NotNull(message = "Transfer date is required")
    private LocalDate transferDate;

    @NotNull(message = "Transfer type is required")
    private TransferType transferType;

    private String remarks;
}
