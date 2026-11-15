package com.society.module.maintenance.entity;

import com.society.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Join row linking one unit to one {@link UnitTag}. The presence of a row means the
 * unit carries that tag; absence means it does not. Kept as an explicit entity (rather
 * than a JPA @ManyToMany collection on Unit) so tag membership can be queried in bulk
 * during bill generation without touching the Unit aggregate.
 *
 * <p>The pair (unitId, tagId) is unique.</p>
 */
@Entity
@Table(name = "unit_tag_assignments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_unit_tag",
                columnNames = {"unit_id", "unit_tag_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnitTagAssignment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    private Long assignmentId;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "unit_tag_id", nullable = false)
    private Long unitTagId;
}
