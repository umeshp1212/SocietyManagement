package com.society.module.maintenance.entity;

import com.society.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * An admin-defined, runtime-created label that can be attached to a subset of units
 * (e.g. SOLAR_PANEL, EV_CHARGER, TERRACE_GARDEN). A maintenance charge can then be
 * configured to apply only to units carrying a given tag, without any code change.
 *
 * <p>This is the data-driven complement to {@link MaintenanceChargeConfig.ApplicableTo}:
 * the enum covers broad categories derived from a unit's own attributes
 * (flat/shop, rented, parking); a tag covers an arbitrary hand-picked group that the
 * society invents on demand.</p>
 */
@Entity
@Table(name = "unit_tags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnitTag extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "unit_tag_id")
    private Long unitTagId;

    /**
     * Stable machine code, unique (e.g. SOLAR_PANEL). Used for lookups and never changes.
     */
    @Column(name = "tag_code", nullable = false, unique = true, length = 50)
    private String tagCode;

    /**
     * Human-friendly label shown in the admin UI (e.g. "Solar Panel Users").
     */
    @Column(name = "tag_name", nullable = false, length = 100)
    private String tagName;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
