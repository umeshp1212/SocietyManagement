package com.society.module.transaction.service;

import com.society.exception.BusinessException;
import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentMode;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentStatus;
import com.society.module.maintenance.repository.MaintenancePaymentRepository;
import com.society.module.owner.repository.UnitRepository;
import com.society.module.transaction.dto.TransactionFilterRequest;
import com.society.module.transaction.dto.TransactionSummaryDTO;
import com.society.module.transaction.specification.TransactionSpecificationBuilder;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Property test for the date-range validation the Transaction read model applies
 * before it builds or executes any query
 * ({@link TransactionService#listTransactions}, Subtask 7.1).
 *
 * <p><b>Property 6: Invalid date range is rejected.</b>
 * For all start/end date pairs where the start date is strictly later than the end
 * date, the read model raises a validation error ({@link BusinessException}) and
 * returns no result set — no query is built and no query is executed, so the
 * previously displayed transaction list is retained unchanged.</p>
 *
 * <p><b>Validates: Requirements 3.4</b></p>
 *
 * <p><b>Approach.</b> The rejection is a pure pre-query guard: the service validates
 * the filter (including the cross-field {@code startDate.isAfter(endDate)} ordering)
 * <em>before</em> it asks {@link TransactionSpecificationBuilder} for a specification
 * or calls {@link MaintenancePaymentRepository#findAll}. To assert the "returns no
 * result set / runs no query" half of the property directly, this test wires the real
 * {@link TransactionService} with mocked collaborators (the specification builder and
 * the payment repository) and verifies they are never touched when the date range is
 * invalid. The access-scope resolver is a settable stub so the guarded branch runs for
 * both society-wide (administrator) and member callers without seeding users. Because
 * the whole check runs before any query engine is involved, no database is required.</p>
 *
 * <p>Generation is intelligently constrained to the invalid input space: it always
 * produces pairs with {@code startDate} strictly after {@code endDate} (the exact
 * precondition of the property), while randomizing the surrounding — always valid —
 * filter fields (payment mode, status set, payer type, unit search, reference) so the
 * date-range guard is shown to fire regardless of the other filter values. As a
 * companion soundness check, each trial also confirms that swapping the two dates
 * (a now-valid, non-strictly-after range) does <em>not</em> raise, so the property
 * isolates the strictly-after case rather than rejecting every date pair.</p>
 *
 * <p>Because jqwik's own lifecycle does not share a single fixture, the property is
 * driven inside one JUnit {@code @Test} method over a stream of generated samples
 * (&gt;= 100 tries), matching the project's other jqwik-based property tests.</p>
 */
class TransactionInvalidDateRangePropertyTest {

    private static final int TRIES = 200;

    /**
     * A settable-scope stub of {@link AccessScopeResolver}: its collaborators are
     * irrelevant because {@link #resolve(Authentication)} is overridden to return the
     * scope selected for the current trial, letting the real service validation branch
     * run without seeding users/owners/tenants.
     */
    private static final class SettableScopeResolver extends AccessScopeResolver {
        private AccessScope scope;

        SettableScopeResolver() {
            super(null, null, null);
        }

        void setScope(AccessScope scope) {
            this.scope = scope;
        }

        @Override
        public AccessScope resolve(Authentication auth) {
            return scope;
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void invalidDateRangeIsRejectedAndRunsNoQuery() {
        // sampleStream() draws values outside jqwik's own @Property lifecycle so the
        // generation loop runs inside a single JUnit fixture, matching the sibling tests.
        java.util.Iterator<Scenario> scenarios = scenarios().sampleStream().iterator();

        SettableScopeResolver scopeResolver = new SettableScopeResolver();
        // Mocked collaborators: the whole point of the property is that these are never
        // reached when the date range is invalid.
        TransactionSpecificationBuilder specificationBuilder = mock(TransactionSpecificationBuilder.class);
        MaintenancePaymentRepository paymentRepository = mock(MaintenancePaymentRepository.class);
        UnitRepository unitRepository = mock(UnitRepository.class);

        TransactionService service = new TransactionService(
                scopeResolver, specificationBuilder, paymentRepository, unitRepository);

        Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "caller", "n/a", java.util.List.of());

        // Coverage guards: ensure both roles and a spread of invalid gaps were exercised.
        boolean sawAdmin = false;
        boolean sawMember = false;
        boolean sawAdjacentDayGap = false;
        boolean sawWideGap = false;

        for (int i = 0; i < TRIES; i++) {
            Scenario scenario = scenarios.next();

            boolean societyWide = scenario.societyWide();
            scopeResolver.setScope(societyWide
                    ? AccessScope.societyWideScope()
                    : AccessScope.memberScoped(Set.of(1L, 2L, 3L)));
            if (societyWide) {
                sawAdmin = true;
            } else {
                sawMember = true;
            }

            LocalDate start = scenario.start();
            LocalDate end = scenario.end();
            // Generator invariant: start is strictly after end (the property's precondition).
            assertThat(start).as("trial %d: generated start must be strictly after end", i).isAfter(end);
            long gapDays = java.time.temporal.ChronoUnit.DAYS.between(end, start);
            if (start.equals(end.plusDays(1))) {
                sawAdjacentDayGap = true;
            }
            if (gapDays >= 30) {
                sawWideGap = true;
            }

            TransactionFilterRequest invalid = scenario.toFilter(start, end);

            // --- The property: an invalid (strictly-after) range must raise a validation error ---
            reset(specificationBuilder, paymentRepository);
            assertThatThrownBy(() -> service.listTransactions(invalid, 0, null, auth))
                    .as("trial %d: startDate %s strictly after endDate %s must be rejected", i, start, end)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(start.toString())
                    .hasMessageContaining(end.toString());

            // --- ...and no query is built or executed, so no result set is produced (Req 3.4) ---
            verifyNoInteractions(specificationBuilder, paymentRepository);

            // --- Soundness: the same fields with a now-valid (start <= end) range must NOT raise ---
            // Swapping the dates makes start <= end; the service must then reach the query path,
            // proving the guard isolates the strictly-after case rather than rejecting all ranges.
            reset(specificationBuilder, paymentRepository);
            org.mockito.Mockito.when(specificationBuilder.build(any(), any()))
                    .thenReturn((Specification<MaintenancePayment>) mock(Specification.class));
            org.mockito.Mockito.when(paymentRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(org.springframework.data.domain.Page.empty());

            TransactionFilterRequest valid = scenario.toFilter(end, start); // swapped -> start <= end
            List<TransactionSummaryDTO> content =
                    service.listTransactions(valid, 0, null, auth).getContent();
            assertThat(content)
                    .as("trial %d: a valid (start <= end) range must not be rejected", i)
                    .isEmpty();
            // The valid path must build a specification and execute exactly one query.
            verify(specificationBuilder).build(any(), any());
            verify(paymentRepository).findAll(any(Specification.class), any(Pageable.class));
            verify(paymentRepository, never()).findById(any());
        }

        assertThat(sawAdmin).as("generator should exercise the society-wide (administrator) caller").isTrue();
        assertThat(sawMember).as("generator should exercise the member caller").isTrue();
        assertThat(sawAdjacentDayGap)
                .as("generator should exercise at least one minimal (one-day) invalid gap").isTrue();
        assertThat(sawWideGap)
                .as("generator should exercise at least one wide (>= 30 day) invalid gap").isTrue();
    }

    // ------------------------------------------------------------------
    // Generators
    // ------------------------------------------------------------------

    /**
     * A generated trial: an invalid ordered date pair (start strictly after end), the
     * caller role, and the surrounding (always valid) filter fields.
     */
    private record Scenario(
            LocalDate start, LocalDate end, boolean societyWide,
            PaymentMode paymentMode, List<PaymentStatus> statuses,
            String payerType, String unitSearch, String reference) {

        TransactionFilterRequest toFilter(LocalDate startDate, LocalDate endDate) {
            TransactionFilterRequest f = new TransactionFilterRequest();
            f.setStartDate(startDate);
            f.setEndDate(endDate);
            f.setPaymentMode(paymentMode);
            f.setStatuses(statuses);
            f.setPayerType(payerType);
            f.setUnitSearch(unitSearch);
            f.setReference(reference);
            return f;
        }
    }

    /**
     * Generates scenarios whose date pair is always strictly {@code start > end} (the
     * property's precondition): an end date within a wide window plus a strictly positive
     * gap added to form the start. The remaining filter fields are drawn from valid values
     * (or left absent) so the date-range guard is shown to fire independent of them.
     */
    private Arbitrary<Scenario> scenarios() {
        LocalDate epoch = LocalDate.of(2020, 1, 1);
        // End date anywhere in a ~5.5 year window.
        Arbitrary<LocalDate> endDates = Arbitraries.integers().between(0, 2000).map(epoch::plusDays);
        // Strictly positive gap so start = end + gap is strictly after end.
        Arbitrary<Integer> gaps = Arbitraries.integers().between(1, 3000);

        Arbitrary<Boolean> societyWideFlags = Arbitraries.of(true, false);
        Arbitrary<PaymentMode> modes = Arbitraries.of(PaymentMode.UPI, PaymentMode.CASH,
                PaymentMode.NEFT, PaymentMode.CHEQUE).injectNull(0.5);
        Arbitrary<List<PaymentStatus>> statusSets = Arbitraries.of(PaymentStatus.class)
                .list().ofMinSize(0).ofMaxSize(3)
                .map(l -> l.isEmpty() ? null : l.stream().distinct().toList())
                .injectNull(0.5);
        Arbitrary<String> payerTypes = Arbitraries.of("OWNER", "TENANT").injectNull(0.6);
        // All within the valid 1-50 length bound so only the date range is invalid.
        Arbitrary<String> unitSearches = Arbitraries.of("A-1", "b-2", "101", null);
        // All within the valid 1-100 length bound (blank is ignored).
        Arbitrary<String> references = Arbitraries.of("TXN", "rcpt", "REF-7", "  ", null);

        return Combinators.combine(endDates, gaps, societyWideFlags, modes, statusSets)
                .as((end, gap, societyWide, mode, statusSet) ->
                        new Object[]{end, gap, societyWide, mode, statusSet})
                .flatMap(base -> Combinators.combine(payerTypes, unitSearches, references)
                        .as((payerType, unitSearch, reference) -> {
                            LocalDate end = (LocalDate) base[0];
                            int gap = (Integer) base[1];
                            boolean societyWide = (Boolean) base[2];
                            PaymentMode mode = (PaymentMode) base[3];
                            @SuppressWarnings("unchecked")
                            List<PaymentStatus> statusSet = (List<PaymentStatus>) base[4];
                            LocalDate start = end.plusDays(gap);
                            return new Scenario(start, end, societyWide, mode, statusSet,
                                    payerType, unitSearch, reference);
                        }));
    }
}
