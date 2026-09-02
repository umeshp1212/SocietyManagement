package com.society.module.maintenance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request to reverse (void) a previously recorded maintenance payment.
 * A reason is mandatory because reversals are money-affecting and must be auditable.
 */
@Data
public class ReversePaymentRequest {

    @NotBlank(message = "A reason for the reversal is required")
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
