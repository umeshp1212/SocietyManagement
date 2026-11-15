package com.society.module.maintenance.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnitTagDTO {

    private Long unitTagId;

    @NotBlank(message = "Tag code is required")
    private String tagCode;

    @NotBlank(message = "Tag name is required")
    private String tagName;

    private String description;

    private Boolean isActive;

    /**
     * Number of units currently carrying this tag. Populated on reads; ignored on writes.
     */
    private Long assignedUnitCount;
}
