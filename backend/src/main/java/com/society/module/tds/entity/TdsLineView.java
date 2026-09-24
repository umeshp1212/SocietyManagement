package com.society.module.tds.entity;

import com.society.enums.TdsRemittanceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read-only projection over the {@code tds_line_view} database view. Each instance is one
 * deducted-TDS line: one eligible source {@code Voucher} (FINAL, tds-applicable, positive
 * {@code tdsAmount}, referencing a vendor) joined to the vendor and, when present, to its
 * remittance line/batch.
 *
 * <p>This entity is {@link Immutable} — it is never inserted or updated through JPA. It is
 * mapped with a Hibernate {@link Subselect} (a read-only derived query) rather than a physical
 * {@code tds_line_view} table/view, so Hibernate never emits DDL for it (in particular it will
 * not create an empty base table under {@code ddl-auto: update}). The {@code remittance_status}
 * column is produced by {@code COALESCE(r.status, 'DEDUCTED')}, so a voucher with no associated
 * remittance line defaults to {@link TdsRemittanceStatus#DEDUCTED}.
 */
@Entity
@Immutable
@Synchronize({"vouchers", "vendors", "tds_remittance_line", "tds_remittance"})
@Subselect("""
    SELECT v.voucher_id              AS voucher_id,
           v.voucher_number          AS voucher_number,
           v.voucher_date            AS deduction_date,
           v.financial_year          AS financial_year,
           v.tds_section             AS tds_section,
           v.tds_rate                AS tds_rate,
           v.tds_amount              AS tds_amount,
           v.vendor_id               AS vendor_id,
           ven.vendor_name           AS vendor_name,
           l.remittance_id           AS remittance_id,
           COALESCE(r.status, 'DEDUCTED') AS remittance_status,
           r.paid_to_accountant_date AS paid_to_accountant_date,
           r.paid_to_it_date         AS paid_to_it_date,
           r.challan_number          AS challan_number
    FROM vouchers v
    JOIN vendors ven                ON ven.vendor_id = v.vendor_id
    LEFT JOIN tds_remittance_line l ON l.voucher_id = v.voucher_id
    LEFT JOIN tds_remittance r      ON r.tds_remittance_id = l.remittance_id
    WHERE v.tds_applicable = TRUE
      AND v.tds_amount IS NOT NULL
      AND v.tds_amount > 0
      AND v.status = 'FINAL'
      AND v.vendor_id IS NOT NULL
    """)
@Getter
@Setter
@NoArgsConstructor
public class TdsLineView {

    @Id
    @Column(name = "voucher_id")
    private Long voucherId;

    @Column(name = "voucher_number")
    private String voucherNumber;

    @Column(name = "deduction_date")
    private LocalDate deductionDate;

    @Column(name = "financial_year")
    private String financialYear;

    @Column(name = "tds_section")
    private String tdsSection;

    @Column(name = "tds_rate", precision = 5, scale = 2)
    private BigDecimal tdsRate;

    @Column(name = "tds_amount", precision = 12, scale = 2)
    private BigDecimal tdsAmount;

    @Column(name = "vendor_id")
    private Long vendorId;

    @Column(name = "vendor_name")
    private String vendorName;

    @Column(name = "remittance_id")
    private Long remittanceId;

    /**
     * Remittance status of this line. Defaults to {@link TdsRemittanceStatus#DEDUCTED} for a
     * voucher that has no associated remittance line (via {@code COALESCE} in the view).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "remittance_status")
    private TdsRemittanceStatus remittanceStatus;

    @Column(name = "paid_to_accountant_date")
    private LocalDate paidToAccountantDate;

    @Column(name = "paid_to_it_date")
    private LocalDate paidToItDate;

    @Column(name = "challan_number")
    private String challanNumber;
}
