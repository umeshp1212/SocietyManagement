package com.society.module.tds.entity;

import com.society.enums.TdsRemittanceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read-only projection over the {@code tds_line_view} database view. Each instance is one
 * deducted-TDS line: one eligible source {@code Voucher} (FINAL, tds-applicable, positive
 * {@code tdsAmount}, referencing a vendor) joined to the vendor and, when present, to its
 * remittance line/batch.
 *
 * <p>This entity is {@link Immutable} — it is never inserted or updated through JPA; the
 * underlying view is maintained by the DB migration ({@code migration_tds_management.sql}).
 * The {@code remittance_status} column is produced by {@code COALESCE(r.status, 'DEDUCTED')},
 * so a voucher with no associated remittance line defaults to
 * {@link TdsRemittanceStatus#DEDUCTED}.
 */
@Entity
@Immutable
@Table(name = "tds_line_view")
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
