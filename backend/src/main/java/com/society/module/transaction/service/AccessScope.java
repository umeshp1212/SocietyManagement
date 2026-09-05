package com.society.module.transaction.service;

import java.util.Set;

/**
 * Immutable value object describing the data access scope resolved for a caller.
 *
 * <p>A society-wide scope imposes no unit restriction (administrators and
 * society-wide roles). A member scope restricts results to the given set of unit
 * IDs; an empty member scope yields an empty result set rather than an error.
 *
 * @param societyWide {@code true} when the caller may access all transactions
 * @param unitIds     the units a member caller is limited to (empty for society-wide)
 */
public record AccessScope(boolean societyWide, Set<Long> unitIds) {

    /**
     * Creates a society-wide scope with no unit restriction.
     *
     * <p>Named {@code societyWideScope} rather than {@code societyWide} because
     * the record component {@code societyWide} already generates a no-arg accessor
     * {@code boolean societyWide()}; Java does not permit a second no-arg method
     * of the same name with a different return type. Downstream code reads the
     * flag via the {@code societyWide()} accessor as shown in the design.
     */
    public static AccessScope societyWideScope() {
        return new AccessScope(true, Set.of());
    }

    /**
     * Creates a member scope restricted to the given unit IDs.
     *
     * @param ids the unit IDs the member may access (may be empty)
     */
    public static AccessScope memberScoped(Set<Long> ids) {
        return new AccessScope(false, ids);
    }
}
