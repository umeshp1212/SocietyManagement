package com.society.module.owner.dto;

import com.society.enums.BhkType;
import com.society.enums.OccupancyStatus;
import com.society.enums.ParkingType;
import com.society.enums.UnitType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnitDTO {
    private Long unitId;
    private String unitNumber;
    private String wing;
    private String floor;
    private UnitType unitType;
    private BhkType bhkType;
    private BigDecimal areaSqft;
    private BigDecimal monthlyMaintenanceAmount;
    private BigDecimal waterCharges;
    private ParkingType parkingType;
    private String primaryOwnerName;
    private String allOwnerNames;
    private List<UnitOwnerDTO> owners;
    private OccupancyStatus occupancyStatus;
    private String status;
}
