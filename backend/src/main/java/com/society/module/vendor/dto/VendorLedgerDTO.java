package com.society.module.vendor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorLedgerDTO {
    private Long vendorId;
    private String vendorName;
    private BigDecimal totalAmount;
    private List<LedgerEntry> entries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LedgerEntry {
        private Long voucherId;
        private String voucherNumber;
        private LocalDate voucherDate;
        private String voucherType;
        private String category;
        private String description;
        private BigDecimal amount;
        private String paymentMode;
        private String referenceNumber;
        private String status;
        private String financialYear;
        private BigDecimal runningTotal;
    }
}
