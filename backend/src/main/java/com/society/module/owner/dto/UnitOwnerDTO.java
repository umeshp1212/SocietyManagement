package com.society.module.owner.dto;

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
public class UnitOwnerDTO {
    private Long id;
    private Long ownerId;
    private String ownerName;
    private String ownerContact;
    private Boolean isPrimary;
    private BigDecimal ownershipPercentage;
    private LocalDateTime addedOn;
}
