package com.society.module.maintenance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

/**
 * Replaces the full set of units assigned to a tag with the supplied list.
 * Units present in {@code unitIds} will carry the tag; any not listed will have
 * the tag removed. An empty list clears all assignments for the tag.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnitTagAssignmentRequest {

    @NotNull(message = "unitIds is required (may be empty)")
    private List<Long> unitIds;
}
