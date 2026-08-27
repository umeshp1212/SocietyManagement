package com.society.module.voucher.dto;

import com.society.enums.PaymentMode;
import com.society.enums.VoucherType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherCreateRequest {

    @NotNull(message = "Voucher date is required")
    private LocalDate voucherDate;

    @NotNull(message = "Voucher type is required")
    private VoucherType voucherType;

    @NotBlank(message = "Category is required")
    private String category;

    private Long vendorId;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private PaymentMode paymentMode;

    private String referenceNumber;

    private String billInvoiceNumber;

    private LocalDate billDate;
}
