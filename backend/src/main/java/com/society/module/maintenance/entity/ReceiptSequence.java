package com.society.module.maintenance.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Per-period counter for maintenance payment receipt numbers.
 *
 * <p>Mirrors the {@code voucher_sequences} pattern: a single row per period whose
 * {@code last_number} is incremented under a pessimistic (SELECT ... FOR UPDATE) lock,
 * guaranteeing unique, gap-free, concurrency-safe receipt numbers. Replaces the previous
 * random 4-digit suffix which could collide.
 */
@Entity
@Table(name = "receipt_sequences", uniqueConstraints = {
        @UniqueConstraint(name = "uk_receipt_period", columnNames = {"period"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceiptSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sequence_id")
    private Long sequenceId;

    /** Period key, format YYYYMM (e.g. "202609"). */
    @Column(name = "period", nullable = false, length = 6)
    private String period;

    @Column(name = "last_number", nullable = false)
    @Builder.Default
    private Integer lastNumber = 0;
}
