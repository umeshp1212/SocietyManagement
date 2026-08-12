package com.society.module.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleDTO {
    private Long vehicleId;
    private String vehicleType;
    private String vehicleNumber;
    private String parkingSlot;
}
