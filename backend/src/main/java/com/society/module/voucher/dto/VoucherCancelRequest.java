package com.society.module.voucher.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherCancelRequest {

    @NotBlank(message = "Cancellation reason is required")
    private String cancellationReason;
}
