package com.society.module.voucher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherAuditDTO {
    private Long auditId;
    private Long voucherId;
    private String voucherNumber;
    private String fieldChanged;
    private String oldValue;
    private String newValue;
    private String changeReason;
    private String changedBy;
    private LocalDateTime changedOn;
    private String ipAddress;
}
