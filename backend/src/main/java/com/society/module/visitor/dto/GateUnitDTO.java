package com.society.module.visitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Minimal unit descriptor the gate portal uses to build its tappable
 * Wing &rarr; Floor &rarr; Flat picker. Driven by the existing units the admin
 * manages, so the picker stays in sync with the Units screen automatically.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GateUnitDTO {
    private Long unitId;
    private String unitNumber;
    /** Wing column if set; otherwise derived from the unit number. */
    private String wing;
    /** Floor column if set; otherwise derived from the unit number. */
    private String floor;
}
