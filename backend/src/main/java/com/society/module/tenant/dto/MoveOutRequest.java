package com.society.module.tenant.dto;

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
public class MoveOutRequest {

    @NotNull(message = "Move out date is required")
    private LocalDate moveOutDate;

    private String moveOutReason;
}
