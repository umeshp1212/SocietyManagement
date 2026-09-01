package com.society.module.ownernoc.entity;

import com.society.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Admin-configurable type of owner NOC request (e.g. Loan Transfer,
 * Property Tax Name Change, Electricity Bill Name Change, Passport/Residence).
 * Holds a default certificate template body that pre-fills the certificate at
 * approval time; the approving admin may still edit the final body.
 */
@Entity
@Table(name = "noc_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NocType extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "noc_type_id")
    private Long nocTypeId;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    /**
     * Default certificate body template. Supports simple placeholders that are
     * substituted when generating the certificate:
     *   {ownerName}, {unitNumber}, {societyName}, {addressee}, {details}, {date}
     */
    @Column(name = "default_template", columnDefinition = "TEXT")
    private String defaultTemplate;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
