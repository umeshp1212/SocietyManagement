package com.society.module.maintenance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuspenseAuditDTO {
    private Long auditId;
    private String action;
    private Long unitId;
    private String unitNumber;
    private String performedBy;
    private LocalDateTime performedOn;
    private String reason;
}
