package com.society.module.transaction.specification;

import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.transaction.dto.TransactionFilterRequest;
import com.society.module.transaction.service.AccessScope;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a single {@link Specification} over {@link MaintenancePayment} by
 * composing the caller's access scope with every active filter using logical AND.
 *
 * <p>Absent, empty, or whitespace-only filters contribute no predicate (identity),
 * so an empty filter set returns the entire scoped result set. Multi-value status
 * uses {@code IN(...)} for OR-within-filter semantics, while the remaining filters
 * are combined with {@code cb.and(...)}.
 */
@Component
public class TransactionSpecificationBuilder {

    /**
     * Composes a specification from the resolved access scope and the (validated)
     * filter request.
     *
     * @param scope  the caller's data access scope (society-wide or member-scoped)
     * @param filter the requested filters; individual absent/blank values are ignored
     * @return a specification ANDing the access-scope constraint with every active filter
     */
    public Specification<MaintenancePayment> build(AccessScope scope, TransactionFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // --- Access scope (Req 10.2 / 10.4) ---
            if (!scope.societyWide()) {
                if (scope.unitIds().isEmpty()) {
                    // Empty member scope -> always-false predicate -> empty result set.
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("unit").get("unitId").in(scope.unitIds()));
                }
            }

            // --- Date range, inclusive (Req 3.1 / 3.2 / 3.3) ---
            if (filter.getStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("paymentDate"), filter.getStartDate()));
            }
            if (filter.getEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("paymentDate"), filter.getEndDate()));
            }

            // --- Payment mode (Req 4.1) ---
            if (filter.getPaymentMode() != null) {
                predicates.add(cb.equal(root.get("paymentMode"), filter.getPaymentMode()));
            }

            // --- Status: OR within, AND with rest (Req 5.1 / 5.3) ---
            if (filter.getStatuses() != null && !filter.getStatuses().isEmpty()) {
                predicates.add(root.get("status").in(filter.getStatuses()));
            }

            // --- Unit id filter (Req 6.1) ---
            if (filter.getUnitId() != null) {
                predicates.add(cb.equal(root.get("unit").get("unitId"), filter.getUnitId()));
            }

            // --- Payer type (Req 6.3) ---
            if (filter.getPayerType() != null) {
                predicates.add(cb.equal(root.get("payerType"), filter.getPayerType()));
            }

            // --- Unit-number search, case-insensitive contains (Req 6.4) ---
            if (StringUtils.hasText(filter.getUnitSearch())) {
                predicates.add(cb.like(cb.lower(root.get("unit").get("unitNumber")),
                        "%" + filter.getUnitSearch().toLowerCase() + "%"));
            }

            // --- Reference search: receiptNumber OR transactionId (Req 9.1) ---
            if (StringUtils.hasText(filter.getReference())) {
                String like = "%" + filter.getReference().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("receiptNumber")), like),
                        cb.like(cb.lower(root.get("transactionId")), like)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
