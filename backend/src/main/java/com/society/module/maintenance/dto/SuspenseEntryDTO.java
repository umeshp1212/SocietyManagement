package com.society.module.maintenance.dto;

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
public class SuspenseEntryDTO {
    private Long suspenseId;
    private BigDecimal amount;
    private LocalDate receivedDate;
    private String paymentMode;
    private String referenceNumber;
    private String description;
    private String status;

    // Assignment details
    private Long assignedToUnitId;
    private String assignedToUnitNumber;
    private String assignedToOwnerName;
    private String assignedBy;
    private LocalDateTime assignedOn;
    private String assignmentRemarks;
    private Boolean applyToOpeningBalance;

    private LocalDateTime createdOn;
    private String createdBy;

    // Audit trail
    private List<SuspenseAuditDTO> auditTrail;
}
