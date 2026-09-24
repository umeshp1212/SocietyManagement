package com.society.module.tds.specification;

import com.society.module.tds.dto.TdsFilterRequest;
import com.society.module.tds.entity.TdsLineView;
import com.society.module.tds.entity.TdsRemittance;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a single {@link Specification} over {@link TdsLineView} by composing every
 * active filter with logical AND.
 *
 * <p>Absent, empty, or whitespace-only filters contribute no predicate (identity), so an
 * empty filter set returns the entire deducted-TDS read model. Multi-value status uses
 * {@code IN(...)} for OR-within-filter semantics, the remittance-date range matches either
 * remittance stage date, and the remaining filters are combined with {@code cb.and(...)} so
 * the result set can only ever <em>narrow</em> relative to the unfiltered scope.
 *
 * <p>Mirrors the existing {@code TransactionSpecificationBuilder} pattern.
 */
@Component
public class TdsSpecificationBuilder {

    /**
     * Composes a specification from the (validated) filter request. Individual absent/blank
     * values are ignored.
     *
     * @param f the requested filters
     * @return a specification ANDing every active filter over {@link TdsLineView}
     */
    public Specification<TdsLineView> build(TdsFilterRequest f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // --- Deduction date range (voucher_date), inclusive (Req 2.3 / 2.4) ---
            if (f.getDeductionStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("deductionDate"), f.getDeductionStartDate()));
            }
            if (f.getDeductionEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("deductionDate"), f.getDeductionEndDate()));
            }

            // --- Remittance date range: match either stage date within [start, end] (Req 2.5 / 2.6) ---
            if (f.getRemittanceStartDate() != null) {
                predicates.add(cb.or(
                        cb.greaterThanOrEqualTo(root.get("paidToAccountantDate"), f.getRemittanceStartDate()),
                        cb.greaterThanOrEqualTo(root.get("paidToItDate"), f.getRemittanceStartDate())));
            }
            if (f.getRemittanceEndDate() != null) {
                predicates.add(cb.or(
                        cb.lessThanOrEqualTo(root.get("paidToAccountantDate"), f.getRemittanceEndDate()),
                        cb.lessThanOrEqualTo(root.get("paidToItDate"), f.getRemittanceEndDate())));
            }

            // --- Vendor-wise (Req 2.7 / 2.8) ---
            if (f.getVendorId() != null) {
                predicates.add(cb.equal(root.get("vendorId"), f.getVendorId()));
            }
            if (StringUtils.hasText(f.getVendorSearch())) {
                predicates.add(cb.like(cb.lower(root.get("vendorName")),
                        "%" + f.getVendorSearch().toLowerCase() + "%"));
            }

            // --- Status: OR within, AND with rest (Req 2.9) ---
            if (f.getStatuses() != null && !f.getStatuses().isEmpty()) {
                predicates.add(root.get("remittanceStatus").in(f.getStatuses()));
            }

            // --- TDS section (Req 2.10) ---
            if (StringUtils.hasText(f.getTdsSection())) {
                predicates.add(cb.equal(root.get("tdsSection"), f.getTdsSection()));
            }

            // --- Financial year (Req 2.11) ---
            if (StringUtils.hasText(f.getFinancialYear())) {
                predicates.add(cb.equal(root.get("financialYear"), f.getFinancialYear()));
            }

            // --- AND-composition so results only ever narrow (Req 2.13) ---
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Composes a specification over the {@link TdsRemittance} batch entity itself (rather than the
     * {@link TdsLineView} line read model) for listing remittance batches (Req 8.5, 8.6, 8.7).
     *
     * <p>Only the filters that map onto a batch's own columns are honoured — financial year,
     * lifecycle status, and the two stage remittance dates. Line-level filters (vendor, TDS
     * section, deduction date) are not batch attributes and therefore contribute no predicate
     * here. As with {@link #build(TdsFilterRequest)}, absent/blank values are ignored and the
     * remaining predicates are combined with logical AND so the result set can only ever narrow;
     * an empty filter returns every batch.
     *
     * @param f the requested filters
     * @return a specification ANDing every applicable batch-level filter over {@link TdsRemittance}
     */
    public Specification<TdsRemittance> buildForBatch(TdsFilterRequest f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // --- Remittance date range: match either stage date within [start, end] (Req 2.5 / 2.6) ---
            if (f.getRemittanceStartDate() != null) {
                predicates.add(cb.or(
                        cb.greaterThanOrEqualTo(root.get("paidToAccountantDate"), f.getRemittanceStartDate()),
                        cb.greaterThanOrEqualTo(root.get("paidToItDate"), f.getRemittanceStartDate())));
            }
            if (f.getRemittanceEndDate() != null) {
                predicates.add(cb.or(
                        cb.lessThanOrEqualTo(root.get("paidToAccountantDate"), f.getRemittanceEndDate()),
                        cb.lessThanOrEqualTo(root.get("paidToItDate"), f.getRemittanceEndDate())));
            }

            // --- Status: OR within, AND with rest (Req 2.9) ---
            if (f.getStatuses() != null && !f.getStatuses().isEmpty()) {
                predicates.add(root.get("status").in(f.getStatuses()));
            }

            // --- Financial year (Req 2.11) ---
            if (StringUtils.hasText(f.getFinancialYear())) {
                predicates.add(cb.equal(root.get("financialYear"), f.getFinancialYear()));
            }

            // --- AND-composition so results only ever narrow (Req 2.13) ---
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
