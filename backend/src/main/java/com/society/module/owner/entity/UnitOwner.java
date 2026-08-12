package com.society.module.owner.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "unit_owners", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"unit_id", "owner_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnitOwner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id", nullable = false)
    private Owner owner;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = false;

    @Column(name = "ownership_percentage", precision = 5, scale = 2)
    private BigDecimal ownershipPercentage = new BigDecimal("100.00");

    @Column(name = "added_on")
    private LocalDateTime addedOn = LocalDateTime.now();

    @Column(name = "added_by", length = 100)
    private String addedBy;
}
