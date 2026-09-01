package com.society.module.ownernoc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Read model for an owner NOC request (member view + admin review list).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerNocRequestDTO {
    private Long requestId;

    private Long ownerId;
    private String ownerName;
    private String ownerEmail;

    private Long unitId;
    private String unitNumber;

    private Long nocTypeId;
    private String nocTypeCode;
    private String nocTypeName;

    private String addressee;
    private String details;
    private String finalContent;

    private String status;
    private String reviewedBy;
    private LocalDateTime reviewedOn;
    private String rejectionReason;

    private LocalDateTime createdOn;
}
