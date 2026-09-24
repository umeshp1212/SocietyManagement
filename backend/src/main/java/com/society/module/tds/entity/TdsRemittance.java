package com.society.module.tds.entity;

import com.society.common.BaseEntity;
import com.society.enums.TdsRemittanceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A TDS remittance batch. A batch groups one or more deducted-TDS voucher lines and tracks
 * the two-stage remittance lifecycle (paid to accountant, then paid to the Income Tax
 * Department). A batch is created directly in the {@link TdsRemittanceStatus#PAID_TO_ACCOUNTANT}
 * stage; the {@code DEDUCTED} state is implicit for lines not yet attached to any batch.
 */
@Entity
@Table(name = "tds_remittance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TdsRemittance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tds_remittance_id")
    private Long tdsRemittanceId;

    @Column(name = "batch_reference", nullable = false, unique = true, length = 30)
    private String batchReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TdsRemittanceStatus status;

    @Column(name = "financial_year", nullable = false, length = 10)
    private String financialYear;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    // ===== Stage 1: Paid to Accountant =====

    @Column(name = "accountant_name", length = 100)
    private String accountantName;

    @Column(name = "paid_to_accountant_date")
    private LocalDate paidToAccountantDate;

    @Column(name = "accountant_reference", length = 100)
    private String accountantReference;

    @Column(name = "paid_to_accountant_by", length = 100)
    private String paidToAccountantBy;

    // ===== Stage 2: Paid to Income Tax Department =====

    @Column(name = "challan_number", length = 50)
    private String challanNumber;

    @Column(name = "paid_to_it_date")
    private LocalDate paidToItDate;

    @Column(name = "paid_to_it_by", length = 100)
    private String paidToItBy;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @OneToMany(mappedBy = "remittance", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TdsRemittanceLine> lines = new ArrayList<>();
}
