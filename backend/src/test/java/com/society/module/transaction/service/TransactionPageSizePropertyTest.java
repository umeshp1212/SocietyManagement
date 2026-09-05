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
 * Property test for the effective page-size resolution the Transaction read model applies
 * ({@link TransactionService#listTransactions}, Subtask 7.1).
 *
 * <p><b>Property 13: Effective page size is bounded.</b>
 * For all requested page sizes (including absent, zero, negative, or greater than 100),
 * the effective page size used for the query lies within [1, 100], defaulting to the role
 * default (25 administrator / 50 member) when no valid size is supplied, and the returned
 * page contains at most that many transactions.</p>
 *
 * <p><b>Validates: Requirements 1.4, 2.1</b></p>
 *
 * <p><b>Approach.</b> The page-size resolution lives in the real service: given a requested
 * size it selects the role default (25 society-wide / 50 member) when no size is supplied and
 * then clamps the value to [1, 100] before issuing the query via {@code PageRequest.of}. This
 * test drives the real {@link TransactionService#listTransactions} path against an in-memory H2
 * database seeded with generated {@link MaintenancePayment} records, so the observed effective
 * size (surfaced by {@link PagedResponse#getSize()}, which the service sets from the executed
 * {@code PageRequest}) is the one the query engine actually used — not a re-derived value.</p>
 *
 * <p>The only stubbed collaborator is {@link AccessScopeResolver} (which would otherwise require
 * seeding users/owners/tenants); it is replaced with an in-test override returning the scope
 * selected for the trial so the service's own default-and-clamp branch runs for both the
 * society-wide (administrator) and member roles. The remaining collaborators
 * ({@link MaintenancePaymentRepository}, {@link UnitRepository}, the real
 * {@link TransactionSpecificationBuilder}) are the production beans.</p>
 *
 * <p>For each trial the same underlying data set is queried under three requested-size shapes:
 * <ul>
 *   <li><b>absent (null)</b>: the effective size must equal the role default (25 admin / 50 member);</li>
 *   <li><b>invalid low (zero or negative)</b>: the effective size must clamp up to the lower bound 1;</li>
 *   <li><b>above range (&gt; 100)</b>: the effective size must clamp down to the upper bound 100;</li>
 *   <li><b>in range [1, 100]</b>: the effective size must be used verbatim.</li>
 * </ul>
 * In every case the returned content never exceeds the effective size, and the effective size
 * itself always lies within [1, 100].</p>
 *
 * <p>Because jqwik's own lifecycle does not share a single Spring context, the property is driven
 * inside one JUnit {@code @Test} method over a stream of generated samples (&gt;= 100 tries),
 * matching the project's other jqwik-based property tests while keeping a single injected
 * persistence context.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        // Use an isolated in-memory H2 database (MySQL-compatible) instead of the
        // configured MySQL datasource, and let Hibernate create the schema from the
        // entity mappings so the real service query runs against a real query engine.
        "spring.datasource.url=jdbc:h2:mem:txn_page_size_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class TransactionPageSizePropertyTest {

    private static final int TRIES = 120;

    private static final int ADMIN_DEFAULT = 25;
    private static final int MEMBER_DEFAULT = 50;
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 100;

    @Autowired
    private MaintenancePaymentRepository paymentRepository;

    @Autowired
    private com.society.module.owner.repository.UnitRepository unitRepository;

    @Autowired
    private TestEntityManager entityManager;

    /**
     * A settable-scope stub of {@link AccessScopeResolver}: the resolver's collaborators are
     * irrelevant here because {@link #resolve(Authentication)} is overridden to return the
     * scope selected for the current trial, letting the real service default-and-clamp branch
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
    void effectivePageSizeIsBounded() {
        // sampleStream() draws values outside of jqwik's own @Property lifecycle, which lets us
        // run the generation loop inside a single Spring-managed @DataJpaTest context.
        java.util.Iterator<List<PaymentBlueprint>> scenarios = paymentSets().sampleStream().iterator();

        SettableScopeResolver scopeResolver = new SettableScopeResolver();
        TransactionService service = new TransactionService(
                scopeResolver,
                new TransactionSpecificationBuilder(),
                paymentRepository,
                unitRepository);

        // Authentication is irrelevant to the stubbed resolver but must be non-null for the call.
        Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "caller", "n/a", java.util.List.of());

        // No filters: the whole (scoped) data set is eligible so page fill is governed only by size.
        TransactionFilterRequest noFilter = new TransactionFilterRequest();

        // Coverage guards: ensure the generators exercised each requested-size branch and each role.
        boolean sawAbsentSize = false;
        boolean sawInvalidLow = false;
        boolean sawAboveRange = false;
        boolean sawInRange = false;
        boolean sawAdmin = false;
        boolean sawMember = false;
        // Ensure at least one trial had enough rows that a small effective size actually truncated it.
        boolean sawTruncatedPage = false;

        for (int i = 0; i < TRIES; i++) {
            List<PaymentBlueprint> blueprints = scenarios.next();

            // Fresh data per trial so results are independent.
            clearDatabase();
            List<MaintenancePayment> persisted = persist(blueprints);
            int total = persisted.size();

            // Alternate roles across trials so both role defaults are exercised.
            boolean societyWide = (i % 2 == 0);
            AccessScope scope = societyWide ? AccessScope.societyWideScope()
                    : AccessScope.memberScoped(allUnitIds(persisted));
            scopeResolver.setScope(scope);
            int roleDefault = societyWide ? ADMIN_DEFAULT : MEMBER_DEFAULT;
            if (societyWide) {
                sawAdmin = true;
            } else {
                sawMember = true;
            }

            // --- Absent size (null): effective size defaults to the role default (Req 1.4, 2.1) ---
            PagedResponse<TransactionSummaryDTO> absent = service.listTransactions(noFilter, 0, null, auth);
            assertBounded(absent, roleDefault, i, "absent size (null)");
            assertThat(absent.getSize())
                    .as("trial %d: absent size must default to role default %d", i, roleDefault)
                    .isEqualTo(roleDefault);
            sawAbsentSize = true;

            // --- Invalid low (zero or negative): effective size clamps up to the lower bound 1 ---
            for (int invalid : new int[]{0, -1, -50}) {
                PagedResponse<TransactionSummaryDTO> low =
                        service.listTransactions(noFilter, 0, invalid, auth);
                assertBounded(low, MIN_SIZE, i, "invalid low size " + invalid);
                assertThat(low.getSize())
                        .as("trial %d: invalid low requested size %d must clamp to lower bound %d",
                                i, invalid, MIN_SIZE)
                        .isEqualTo(MIN_SIZE);
                sawInvalidLow = true;
                if (total > MIN_SIZE) {
                    assertThat(low.getContent().size())
                            .as("trial %d: a page of size 1 over %d rows must contain at most 1 row",
                                    i, total)
                            .isLessThanOrEqualTo(MIN_SIZE);
                    sawTruncatedPage = true;
                }
            }

            // --- Above range (> 100): effective size clamps down to the upper bound 100 ---
            for (int over : new int[]{101, 250, 10_000}) {
                PagedResponse<TransactionSummaryDTO> high =
                        service.listTransactions(noFilter, 0, over, auth);
                assertBounded(high, MAX_SIZE, i, "above-range size " + over);
                assertThat(high.getSize())
                        .as("trial %d: above-range requested size %d must clamp to upper bound %d",
                                i, over, MAX_SIZE)
                        .isEqualTo(MAX_SIZE);
                sawAboveRange = true;
            }

            // --- In range [1, 100]: effective size is used verbatim ---
            for (int valid : new int[]{1, 25, 50, 100}) {
                PagedResponse<TransactionSummaryDTO> inRange =
                        service.listTransactions(noFilter, 0, valid, auth);
                assertBounded(inRange, valid, i, "in-range size " + valid);
                assertThat(inRange.getSize())
                        .as("trial %d: in-range requested size %d must be used verbatim", i, valid)
                        .isEqualTo(valid);
                sawInRange = true;
                if (total > valid) {
                    assertThat(inRange.getContent().size())
                            .as("trial %d: a page of size %d over %d rows must contain at most %d rows",
                                    i, valid, total, valid)
                            .isLessThanOrEqualTo(valid);
                    sawTruncatedPage = true;
                }
            }
        }

        assertThat(sawAbsentSize).as("generator should exercise the absent-size (default) branch").isTrue();
        assertThat(sawInvalidLow).as("generator should exercise the invalid-low (clamp up) branch").isTrue();
        assertThat(sawAboveRange).as("generator should exercise the above-range (clamp down) branch").isTrue();
        assertThat(sawInRange).as("generator should exercise the in-range (verbatim) branch").isTrue();
        assertThat(sawAdmin).as("generator should exercise the administrator role default (25)").isTrue();
        assertThat(sawMember).as("generator should exercise the member role default (50)").isTrue();
        assertThat(sawTruncatedPage)
                .as("generator should produce at least one data set large enough for the effective "
                        + "size to truncate the returned page")
                .isTrue();
    }

    /**
     * Asserts the core invariant for a single response: the effective page size lies within
     * [1, 100], equals the expected value, and the returned content never exceeds it.
     */
    private void assertBounded(PagedResponse<TransactionSummaryDTO> response, int expectedSize,
                               int trial, String label) {
        assertThat(response.getSize())
                .as("trial %d (%s): effective page size must lie within [%d, %d]",
                        trial, label, MIN_SIZE, MAX_SIZE)
                .isBetween(MIN_SIZE, MAX_SIZE);
        assertThat(response.getSize())
                .as("trial %d (%s): effective page size must be %d", trial, label, expectedSize)
                .isEqualTo(expectedSize);
        assertThat(response.getContent().size())
                .as("trial %d (%s): returned page must contain at most the effective size (%d)",
                        trial, label, expectedSize)
                .isLessThanOrEqualTo(expectedSize);
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

    private record PaymentBlueprint(
            String unitNumber, BigDecimal amount, LocalDate paymentDate, PaymentMode paymentMode,
            PaymentStatus status, String payerType, String payerName,
            String transactionId, String receiptNumber) {
    }

    /**
     * Generates a payment set whose size ranges from 1 up past the smallest bound (1) so that
     * some trials contain enough rows for a small effective page size to truncate the page,
     * exercising the "at most the effective size" clause of the property.
     */
    private Arbitrary<List<PaymentBlueprint>> paymentSets() {
        return paymentBlueprint().list().ofMinSize(1).ofMaxSize(8);
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
