package com.society.module.owner.dto;

import com.society.enums.BhkType;
import com.society.enums.ParkingType;
import com.society.enums.UnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnitCreateRequest {

    @NotBlank(message = "Unit number is required")
    @Size(max = 20, message = "Unit number must not exceed 20 characters")
    private String unitNumber;

    @Size(max = 10, message = "Wing must not exceed 10 characters")
    private String wing;

    @Size(max = 10, message = "Floor must not exceed 10 characters")
    private String floor;

    @NotNull(message = "Unit type is required")
    private UnitType unitType;

    private BhkType bhkType;

    private BigDecimal areaSqft;

    @NotNull(message = "Monthly maintenance amount is required")
    private BigDecimal monthlyMaintenanceAmount;

    /**
     * Water charges specific to this unit (based on tank configuration)
     */
    private BigDecimal waterCharges;

    /**
     * Parking type: NONE, TWO_WHEELER, FOUR_WHEELER, BOTH
     */
    private ParkingType parkingType;
}
