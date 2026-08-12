package com.society.module.owner.dto;

import com.society.enums.TransferType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnershipHistoryDTO {
    private Long historyId;
    private Long unitId;
    private String unitNumber;
    private Long ownerId;
    private String ownerName;
    private LocalDate ownershipStartDate;
    private LocalDate ownershipEndDate;
    private TransferType transferType;
    private String transferDocumentPath;
    private String remarks;
    private String recordedBy;
    private LocalDateTime recordedOn;
}
