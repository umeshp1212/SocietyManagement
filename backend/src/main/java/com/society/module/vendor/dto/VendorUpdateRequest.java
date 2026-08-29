package com.society.module.vendor.dto;

import com.society.enums.PaymentFrequency;
// import com.society.enums.VendorCategory;
import com.society.enums.VendorStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class VendorUpdateRequest {

    @NotBlank(message = "Vendor name is required")
    @Size(max = 200, message = "Vendor name must not exceed 200 characters")
    private String vendorName;

    @NotNull(message = "Category is required")
    private Long categoryId;

    @Size(max = 150, message = "Contact person name must not exceed 150 characters")
    private String contactPerson;

    @NotBlank(message = "Phone number is required")
    @Size(max = 15, message = "Phone must not exceed 15 characters")
    private String phone;

    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;

    private String address;

    @Size(max = 20, message = "PAN must not exceed 20 characters")
    private String panNumber;

    @Size(max = 20, message = "GST must not exceed 20 characters")
    private String gstNumber;

    @Size(max = 30, message = "Account number must not exceed 30 characters")
    private String bankAccountNumber;

    @Size(max = 15, message = "IFSC must not exceed 15 characters")
    private String bankIfsc;

    @Size(max = 100, message = "Bank name must not exceed 100 characters")
    private String bankName;

    private LocalDate agreementStartDate;
    private LocalDate agreementEndDate;
    private BigDecimal contractedAmount;
    private PaymentFrequency paymentFrequency;

    @NotNull(message = "Status is required")
    private VendorStatus status;
}
