package com.society.module.maintenance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request to reassign a wrongly-attributed maintenance payment to the correct unit's bill.
 *
 * <p>Reassignment is implemented as an atomic reverse-and-recreate: the original payment is
 * reversed (restoring the wrong bill's balance) and an equivalent payment is created against
 * {@code targetBillId}. A reason is mandatory because this moves money between owners and must
 * be fully auditable.
 */
@Data
public class ReassignPaymentRequest {

    @NotNull(message = "Target bill ID is required")
    private Long targetBillId;

    @NotBlank(message = "A reason for the reassignment is required")
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
