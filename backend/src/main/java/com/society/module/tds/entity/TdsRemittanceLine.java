package com.society.module.tds.entity;

import com.society.common.BaseEntity;
import com.society.module.voucher.entity.Voucher;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Links a single deducted-TDS {@link Voucher} to a {@link TdsRemittance} batch. The unique
 * constraint on {@code voucher_id} enforces that a voucher can be remitted at most once.
 * {@code tdsAmount} is a snapshot of the amount deducted on the voucher at the time the batch
 * was created.
 */
@Entity
@Table(
        name = "tds_remittance_line",
        uniqueConstraints = @UniqueConstraint(name = "uk_tds_line_voucher", columnNames = "voucher_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TdsRemittanceLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tds_remittance_line_id")
    private Long tdsRemittanceLineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "remittance_id", nullable = false)
    private TdsRemittance remittance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voucher_id", nullable = false, unique = true)
    private Voucher voucher;

    @Column(name = "tds_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal tdsAmount;
}
