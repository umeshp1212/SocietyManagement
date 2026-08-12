package com.society.module.voucher.entity;

import com.society.enums.VoucherType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "voucher_sequences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoucherSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sequence_id")
    private Long sequenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "voucher_type", nullable = false)
    private VoucherType voucherType;

    @Column(name = "financial_year", nullable = false, length = 10)
    private String financialYear;

    @Column(name = "last_number", nullable = false)
    private Integer lastNumber = 0;
}
