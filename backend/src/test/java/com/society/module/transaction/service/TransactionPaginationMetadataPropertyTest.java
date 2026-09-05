package com.society.module.transaction.service;

import com.society.common.PagedResponse;
import com.society.enums.OccupancyStatus;
import com.society.enums.UnitType;
import com.society.module.maintenance.entity.MaintenanceBill;
import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentMode;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentStatus;
import com.society.module.maintenance.repository.MaintenancePaymentRepository;
import com.society.module.owner.entity.Unit;
import com.society.module.transaction.dto.TransactionFilterRequest;
import com.society.module.transaction.dto.TransactionSummaryDTO;
import com.society.module.transaction.specification.TransactionSpecificationBuilder;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property test for the pagination metadata the Transaction read model surfaces
 * ({@link TransactionService#listTransactions}, Subtask 7.1).
 *
 * <p><b>Property 14: Pagination metadata is internally consistent.</b>
 * For all queries, the paged response satisfies:
 * {@code totalPages == ceil(totalElements / size)} (with {@code totalPages == 0} when
 * {@code totalElements == 0}), {@code content.size() <= size}, and {@code last == true}
 * iff the current page is the final page; requesting a page number outside the valid range
 * yields empty content while preserving accurate {@code totalElements}/{@code page}/
 * {@code size}/{@code totalPages}.</p>
 *
 * <p><b>Validates: Requirements 1.5, 1.6</b></p>
 *
 * <p><b>Approach.</b> The pagination metadata is produced by the query engine, not by hand:
 * the service issues its query via {@code PageRequest.of(page, size, ...)} against a
 * {@link org.springframework.data.jpa.domain.Specification}-backed repository and copies the
 * resulting {@code Page}'s number, size, total-elements, total-pages, and last flags straight
 * into the {@link PagedResponse}. This test drives the real
 * {@link TransactionService#listTransactions} path against an in-memory H2 database seeded with
 * generated {@link MaintenancePayment} records, so the observed metadata is the metadata the
 * query engine actually computed — not a re-derived value.</p>
 *
 * <p>For each generated data set the property is checked across a spread of requested page
 * numbers and page sizes, including:
 * <ul>
 *   <li>the first page, an interior page, and the final valid page;</li>
 *   <li>the first page just beyond the valid range and a far out-of-range page (Req 1.6),
 *       both of which must return empty content while still reporting accurate
 *       {@code totalElements}/{@code page}/{@code size}/{@code totalPages}.</li>
 * </ul>
 * In every case the invariants above are asserted against an independently computed expected
 * total-page count ({@code ceil(totalElements / size)}), so the test never trusts the value it
 * is validating.</p>
 *
 * <p>The only stubbed collaborator is {@link AccessScopeResolver} (which would otherwise require
 * seeding users/owners/tenants); it is replaced with an in-test override returning the scope
 * selected for the trial so both the society-wide (administrator) and member roles are exercised.
 * The remaining collaborators ({@link MaintenancePaymentRepository},
 * {@link com.society.module.owner.repository.UnitRepository}, the real
 * {@link TransactionSpecificationBuilder}) are the production beans.</p>
 *
 * <p>Because jqwik's own lifecycle does not share a single Spring context, the property is driven
 * inside one JUnit {@code @Test} method over a stream of generated samples (&gt;= 100 tries),
 * matching the project's other jqwik-based property tests while keeping a single injected
 * persistence context. Page numbers here follow the service's zero-based
 * {@code Pageable} convention (the controller surfaces the number back to the client); a page
 * index at or beyond {@code totalPages} is the "outside the valid range" case of Req 1.6.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        // Use an isolated in-memory H2 database (MySQL-compatible) instead of the
        // configured MySQL datasource, and let Hibernate create the schema from the
        // entity mappings so the real service query runs against a real query engine.
        "spring.datasource.url=jdbc:h2:mem:txn_pagination_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        // The production db/data.sql is MySQL-specific; skip SQL script init for this test.
        "spring.sql.init.mode=never",
        // Keep test output readable; the assertions, not the SQL log, verify behaviour.
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF"
})
class TransactionPaginationMetadataPropertyTest {

    private static final int TRIES = 120;

    @Autowired
    private MaintenancePaymentRepository paymentRepository;

    @Autowired
    private com.society.module.owner.repository.UnitRepository unitRepository;

    @Autowired
    private TestEntityManager entityManager;

    /**
     * A settable-scope stub of {@link AccessScopeResolver}: {@link #resolve(Authentication)} is
     * overridden to return the scope selected for the current trial, letting the real service
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
    void paginationMetadataIsInternallyConsistent() {
        // sampleStream() draws values outside of jqwik's own @Property lifecycle, which lets us
        // run the generation loop inside a single Spring-managed @DataJpaTest context.
        java.util.Iterator<Scenario> scenarios = scenarios().sampleStream().iterator();

        SettableScopeResolver scopeResolver = new SettableScopeResolver();
        TransactionService service = new TransactionService(
                scopeResolver,
                new TransactionSpecificationBuilder(),
                paymentRepository,
                unitRepository);

        // Authentication is irrelevant to the stubbed resolver but must be non-null for the call.
        Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "caller", "n/a", java.util.List.of());

        // No filters: the whole (scoped) data set is eligible so paging governs the outcome.
        TransactionFilterRequest noFilter = new TransactionFilterRequest();

        // Coverage guards: ensure the generators exercised the interesting shapes.
        boolean sawEmptyDataSet = false;      // totalElements == 0 -> totalPages == 0
        boolean sawSinglePage = false;        // one page holds every row -> last on page 0
        boolean sawMultiPage = false;         // more than one page -> intermediate + final pages
        boolean sawOutOfRangePage = false;    // page index >= totalPages -> empty content (Req 1.6)
        boolean sawAdmin = false;
        boolean sawMember = false;

        for (int i = 0; i < TRIES; i++) {
            Scenario scenario = scenarios.next();

            // Fresh data per trial so results are independent.
            clearDatabase();
            List<MaintenancePayment> persisted = persist(scenario.payments());
            long total = persisted.size();

            // Alternate roles across trials so both the admin and member scopes are exercised.
            boolean societyWide = (i % 2 == 0);
            AccessScope scope = societyWide ? AccessScope.societyWideScope()
                    : AccessScope.memberScoped(allUnitIds(persisted));
            scopeResolver.setScope(scope);
            if (societyWide) {
                sawAdmin = true;
            } else {
                sawMember = true;
            }

            int size = scenario.size();                       // always in [1, 100]
            int expectedTotalPages = expectedTotalPages(total, size);

            // The page indices to probe: first, an interior page, the final valid page, the first
            // page just beyond the valid range, and a far out-of-range page (Req 1.6).
            for (int page : pagesToProbe(expectedTotalPages)) {
                PagedResponse<TransactionSummaryDTO> response =
                        service.listTransactions(noFilter, page, size, auth);

                // --- totalElements is accurate regardless of the requested page (Req 1.5, 1.6) ---
                assertThat(response.getTotalElements())
                        .as("trial %d: totalElements must equal the scoped row count "
                                        + "(page=%d, size=%d, total=%d)", i, page, size, total)
                        .isEqualTo(total);

                // --- size is echoed back accurately (Req 1.5, 1.6) ---
                assertThat(response.getSize())
                        .as("trial %d: size must be echoed back (page=%d, size=%d)", i, page, size)
                        .isEqualTo(size);

                // --- page number is echoed back accurately (Req 1.5, 1.6) ---
                assertThat(response.getPage())
                        .as("trial %d: requested page number must be echoed back (page=%d)", i, page)
                        .isEqualTo(page);

                // --- totalPages == ceil(totalElements / size), and 0 when empty (Req 1.5) ---
                assertThat(response.getTotalPages())
                        .as("trial %d: totalPages must equal ceil(totalElements / size) "
                                        + "(total=%d, size=%d)", i, total, size)
                        .isEqualTo(expectedTotalPages);
                if (total == 0) {
                    assertThat(response.getTotalPages())
                            .as("trial %d: totalPages must be 0 when there are no transactions", i)
                            .isZero();
                }

                // --- content never exceeds the effective page size ---
                assertThat(response.getContent().size())
                        .as("trial %d: content size must not exceed the page size (size=%d)", i, size)
                        .isLessThanOrEqualTo(size);

                // --- last == true iff this is the final page ---
                boolean isFinalPage = isFinalPage(page, expectedTotalPages);
                assertThat(response.isLast())
                        .as("trial %d: last must be true iff the current page (%d) is the final page "
                                        + "(totalPages=%d)", i, page, expectedTotalPages)
                        .isEqualTo(isFinalPage);

                // --- out-of-range page yields empty content while preserving accurate metadata (Req 1.6) ---
                if (page >= expectedTotalPages) {
                    assertThat(response.getContent())
                            .as("trial %d: an out-of-range page (%d >= totalPages %d) must be empty",
                                    i, page, expectedTotalPages)
                            .isEmpty();
                    sawOutOfRangePage = true;
                } else {
                    // In-range non-final pages are full; the final in-range page holds the remainder.
                    int expectedRows = expectedRowsOnPage(total, size, page);
                    assertThat(response.getContent().size())
                            .as("trial %d: in-range page %d must hold exactly %d rows "
                                            + "(total=%d, size=%d)", i, page, expectedRows, total, size)
                            .isEqualTo(expectedRows);
                }
            }

            if (total == 0) {
                sawEmptyDataSet = true;
            } else if (expectedTotalPages == 1) {
                sawSinglePage = true;
            } else if (expectedTotalPages >= 2) {
                sawMultiPage = true;
            }
        }

        assertThat(sawEmptyDataSet)
                .as("generator should produce at least one empty data set (totalPages == 0)").isTrue();
        assertThat(sawSinglePage)
                .as("generator should produce at least one single-page result").isTrue();
        assertThat(sawMultiPage)
                .as("generator should produce at least one multi-page result").isTrue();
        assertThat(sawOutOfRangePage)
                .as("generator should probe at least one out-of-range page (Req 1.6)").isTrue();
        assertThat(sawAdmin).as("generator should exercise the administrator (society-wide) scope").isTrue();
        assertThat(sawMember).as("generator should exercise the member scope").isTrue();
    }

    /** ceil(totalElements / size); zero when there are no elements (mirrors Spring's Page). */
    private static int expectedTotalPages(long totalElements, int size) {
        if (totalElements == 0) {
            return 0;
        }
        return (int) ((totalElements + size - 1) / size);
    }

    /** Whether the zero-based {@code page} is the final page of a result with {@code totalPages}. */
    private static boolean isFinalPage(int page, int totalPages) {
        // Spring's Page: last is true when there is no next page. For an empty result (totalPages
        // == 0) every page is "last"; otherwise the last page is index totalPages - 1, and any
        // page at or beyond it is also reported as last.
        if (totalPages == 0) {
            return true;
        }
        return page >= totalPages - 1;
    }

    /** Rows expected on an in-range zero-based page. */
    private static int expectedRowsOnPage(long total, int size, int page) {
        long start = (long) page * size;
        long remaining = total - start;
        return (int) Math.min(size, Math.max(0, remaining));
    }

    /**
     * The page indices to probe for a result with {@code totalPages}: the first page, an interior
     * page (when one exists), the final valid page, the first page just beyond the valid range,
     * and a far out-of-range page. Deduplicated and kept non-negative.
     */
    private static java.util.List<Integer> pagesToProbe(int totalPages) {
        java.util.LinkedHashSet<Integer> pages = new java.util.LinkedHashSet<>();
        pages.add(0);                                   // first page
        if (totalPages >= 3) {
            pages.add(totalPages / 2);                  // an interior page
        }
        if (totalPages >= 1) {
            pages.add(totalPages - 1);                  // final valid page
        }
        pages.add(Math.max(totalPages, 0));             // first out-of-range page (Req 1.6)
        pages.add(Math.max(totalPages, 0) + 5);         // far out-of-range page (Req 1.6)
        return new java.util.ArrayList<>(pages);
    }

    private static java.util.Set<Long> allUnitIds(List<MaintenancePayment> payments) {
        return payments.stream()
                .map(p -> p.getUnit().getUnitId())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    // ------------------------------------------------------------------
    // Persistence helpers
    // ------------------------------------------------------------------

    private void clearDatabase() {
        entityManager.getEntityManager().createQuery("delete from MaintenancePayment").executeUpdate();
        entityManager.getEntityManager().createQuery("delete from MaintenanceBill").executeUpdate();
        entityManager.getEntityManager().createQuery("delete from Unit").executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    private List<MaintenancePayment> persist(List<PaymentBlueprint> blueprints) {
        java.util.Map<String, Unit> unitsByNumber = new java.util.LinkedHashMap<>();
        java.util.Map<String, MaintenanceBill> billsByUnit = new java.util.LinkedHashMap<>();
        for (PaymentBlueprint bp : blueprints) {
            Unit unit = unitsByNumber.computeIfAbsent(bp.unitNumber(), this::persistUnit);
            billsByUnit.computeIfAbsent(bp.unitNumber(), k -> persistBill(unit));
        }

        List<MaintenancePayment> persisted = new ArrayList<>();
        int index = 0;
        for (PaymentBlueprint bp : blueprints) {
            Unit unit = unitsByNumber.get(bp.unitNumber());
            MaintenanceBill bill = billsByUnit.get(bp.unitNumber());
            // receipt_number carries a unique constraint; append a per-payment suffix to keep it unique.
            String txn = bp.transactionId() == null ? null : bp.transactionId() + "-" + index;
            String receipt = bp.receiptNumber() == null ? null : bp.receiptNumber() + "-" + index;
            MaintenancePayment payment = MaintenancePayment.builder()
                    .bill(bill)
                    .unit(unit)
                    .amount(bp.amount())
                    .paymentDate(bp.paymentDate())
                    .paymentMode(bp.paymentMode())
                    .status(bp.status())
                    .payerType(bp.payerType())
                    .payerName(bp.payerName())
                    .transactionId(txn)
                    .receiptNumber(receipt)
                    .build();
            persisted.add(entityManager.persist(payment));
            index++;
        }
        entityManager.flush();
        entityManager.clear();
        return persisted;
    }

    private Unit persistUnit(String unitNumber) {
        Unit unit = Unit.builder()
                .unitNumber(unitNumber)
                .unitType(UnitType.FLAT)
                .occupancyStatus(OccupancyStatus.SELF_OCCUPIED)
                .monthlyMaintenanceAmount(BigDecimal.ZERO)
                .status("ACTIVE")
                .build();
        return entityManager.persist(unit);
    }

    private MaintenanceBill persistBill(Unit unit) {
        MaintenanceBill bill = MaintenanceBill.builder()
                .unit(unit)
                .billMonth(1)
                .billYear(2024)
                .billDate(LocalDate.of(2024, 1, 1))
                .dueDate(LocalDate.of(2024, 1, 10))
                .amount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .build();
        return entityManager.persist(bill);
    }

    // ------------------------------------------------------------------
    // Generators
    // ------------------------------------------------------------------

    private record Scenario(List<PaymentBlueprint> payments, int size) {
    }

    private record PaymentBlueprint(
            String unitNumber, BigDecimal amount, LocalDate paymentDate, PaymentMode paymentMode,
            PaymentStatus status, String payerType, String payerName,
            String transactionId, String receiptNumber) {
    }

    /**
     * Generates a payment set (including the empty set, so {@code totalPages == 0} is exercised)
     * and an in-range page size. Sizes are kept small relative to the maximum row count so that
     * single-page, multi-page, and empty shapes all occur, and out-of-range page probing has
     * substance.
     */
    private Arbitrary<Scenario> scenarios() {
        Arbitrary<List<PaymentBlueprint>> payments =
                paymentBlueprint().list().ofMinSize(0).ofMaxSize(12);
        // Small sizes (1..5) against up to 12 rows guarantee frequent multi-page results.
        Arbitrary<Integer> sizes = Arbitraries.integers().between(1, 5);
        return Combinators.combine(payments, sizes).as(Scenario::new);
    }

    private Arbitrary<PaymentBlueprint> paymentBlueprint() {
        Arbitrary<String> unitNumbers = Arbitraries.of("A-101", "A-102", "B-201", "B-202", "C-301");
        Arbitrary<BigDecimal> amounts = Arbitraries.longs().between(0L, 500_000L)
                .map(cents -> BigDecimal.valueOf(cents, 2));
        Arbitrary<LocalDate> dates = Arbitraries.integers().between(0, 30)
                .map(offset -> LocalDate.of(2024, 1, 1).plusDays(offset));
        Arbitrary<PaymentMode> modes = Arbitraries.of(PaymentMode.UPI, PaymentMode.CASH,
                PaymentMode.NEFT, PaymentMode.CHEQUE);
        Arbitrary<PaymentStatus> statuses = Arbitraries.of(PaymentStatus.class);
        Arbitrary<String> payerTypes = Arbitraries.of("OWNER", "TENANT");
        Arbitrary<String> payerNames = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(12);
        Arbitrary<String> txnIds = Arbitraries.of("TXN10", "txn20", "REF-7", null);
        Arbitrary<String> receipts = Arbitraries.of("RCPT-1", "rcpt-2", "REF-9", null);

        Arbitrary<String[]> refPair = Combinators.combine(txnIds, receipts)
                .as((txn, receipt) -> new String[]{txn, receipt});

        return Combinators.combine(unitNumbers, amounts, dates, modes, statuses, payerTypes, payerNames, refPair)
                .as((unitNumber, amount, date, mode, status, payerType, payerName, refs) ->
                        new PaymentBlueprint(unitNumber, amount, date, mode, status,
                                payerType, payerName, refs[0], refs[1]));
    }
}
