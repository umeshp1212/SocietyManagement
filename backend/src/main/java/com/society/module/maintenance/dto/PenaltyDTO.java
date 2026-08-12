package com.society.module.maintenance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PenaltyDTO {
    private Long penaltyId;
    private Long unitId;
    private String unitNumber;
    private BigDecimal amount;
    private String reason;
    private String category;
    private Integer billMonth;
    private Integer billYear;
    private String status;
    private String imposedBy;
    private LocalDateTime createdOn;
}
