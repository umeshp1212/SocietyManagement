package com.society.module.vendor.dto;

import com.society.enums.PaymentFrequency;
// import com.society.enums.VendorCategory;
import com.society.enums.VendorStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorDTO {
    private Long vendorId;
    private String vendorName;
    // private VendorCategory category;
    private Long categoryId;
    private String categoryCode;
    private String CategoryName;
    private String contactPerson;
    private String phone;
    private String email;
    private String address;
    private String panNumber;
    private String gstNumber;
    private String bankAccountNumber;
    private String bankIfsc;
    private String bankName;
    private LocalDate agreementStartDate;
    private LocalDate agreementEndDate;
    private BigDecimal contractedAmount;
    private PaymentFrequency paymentFrequency;
    private VendorStatus status;
    private List<VendorDocumentDTO> documents;
    private String createdBy;
    private LocalDateTime createdOn;
    private String modifiedBy;
    private LocalDateTime modifiedOn;
    // Computed fields
    private Long daysUntilExpiry;
    private Boolean isContractExpired;
}
