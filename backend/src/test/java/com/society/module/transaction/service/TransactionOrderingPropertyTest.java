package com.society.module.transaction.service;

import com.society.enums.OccupancyStatus;
import com.society.enums.UnitType;
import com.society.module.maintenance.entity.MaintenanceBill;
import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentMode;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentStatus;
import com.society.module.maintenance.repository.MaintenancePaymentRepository;
import com.society.module.owner.entity.Unit;
import com.society.module.transaction.dto.TransactionFilterRequest;
import com.society.module.transaction.specification.TransactionSpecificationBuilder;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Model-based property test for the ordering the Transaction read model applies.
 *
 * <p><b>Property 1: Result ordering is stable and descending.</b>
 * For all result pages produced by the read model, for any access scope and any filter
 * set, the returned transactions are ordered by payment date descending and, among
 * transactions sharing the same payment date, by payment identifier descending.</p>
 *
 * <p><b>Validates: Requirements 1.1, 2.4</b></p>
 *
 * <p><b>Approach.</b> The ordering is produced by the query engine, not by hand: the
 * service issues its query through
 * {@code PageRequest.of(page, size, Sort.by(desc("paymentDate"), desc("paymentId")))}
 * against a {@link Specification}-backed repository. This test drives the identical code
 * path — the real {@link TransactionSpecificationBuilder} plus the same {@link Sort}
 * clause the service uses — against an in-memory H2 database seeded with generated
 * {@link MaintenancePayment} records, then asserts the returned rows are non-increasing
 * under the (paymentDate desc, paymentId desc) comparator. Payment dates are drawn from a
 * deliberately narrow window so many rows share a date and exercise the payment-id
 * tie-breaker.</p>
 *
 * <p>The invariant is checked for a society-wide scope, member subset scopes, and empty
 * member scopes, and across randomly generated filter sets, because ordering must hold
 * regardless of which access scope or filters shaped the result set.</p>
 *
 * <p>Because jqwik's own lifecycle does not share a single Spring context, the property is
 * driven inside one JUnit {@code @Test} method over a stream of generated
 * (payment set, filter set, scope selection) samples (>= 100 tries), matching the project's
 * jqwik-based property tests while keeping a single injected persistence context.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        // Use an isolated in-memory H2 database (MySQL-compatible) instead of the
        // configured MySQL datasource, and let Hibernate create the schema from the
        // entity mappings so the real Specification + Sort run against a real query engine.
        "spring.datasource.url=jdbc:h2:mem:txn_order_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class TransactionOrderingPropertyTest {

    private static final int TRIES = 120;

    /** The exact ordering the service applies (see TransactionService.listTransactions). */
    private static final Sort ORDER =
            Sort.by(Sort.Order.desc("paymentDate"), Sort.Order.desc("paymentId"));

    /** Selects which kind of access scope a trial exercises. */
    private enum ScopeKind {SOCIETY_WIDE, MEMBER_SUBSET, MEMBER_EMPTY}

    @Autowired
    private MaintenancePaymentRepository paymentRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final TransactionSpecificationBuilder builder = new TransactionSpecificationBuilder();

    @Test
    void resultOrderingIsStableAndDescending() {
        // sampleStream() draws values outside of jqwik's own @Property lifecycle, which lets us
        // run the generation loop inside a single Spring-managed @DataJpaTest context.
        java.util.Iterator<Scenario> scenarios = scenarios().sampleStream().iterator();

        boolean sawSocietyWide = false;
        boolean sawMemberSubset = false;
        boolean sawMemberEmpty = false;
        // Ensure at least one trial actually exercised the payment-id tie-breaker: two returned
        // rows sharing the same payment date, ordered by descending payment id.
        boolean sawSameDateTieBreak = false;
        // Ensure at least one trial returned more than one row (an ordering with substance).
        boolean sawMultiRow = false;

        for (int i = 0; i < TRIES; i++) {
            Scenario scenario = scenarios.next();

            // Fresh data per trial so results are independent.
            clearDatabase();
            List<MaintenancePayment> persisted = persist(scenario.payments());

            List<Long> unitIds = persisted.stream()
                    .map(p -> p.getUnit().getUnitId())
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            AccessScope scope = buildScope(scenario, unitIds);
            TransactionFilterRequest filter = scenario.filter();

            // --- Real production code path under test: same Specification + same Sort as the service ---
            Specification<MaintenancePayment> spec = builder.build(scope, filter);
            // Ask for a page large enough to hold every possible row so the full ordering is observable.
            PageRequest pageRequest = PageRequest.of(0, Math.max(1, persisted.size()), ORDER);
            List<MaintenancePayment> result =
                    paymentRepository.findAll(spec, pageRequest).getContent();

            // --- Assert the ordering invariant on adjacent pairs ---
            for (int k = 1; k < result.size(); k++) {
                MaintenancePayment prev = result.get(k - 1);
                MaintenancePayment curr = result.get(k);
                assertThat(compare(prev, curr))
                        .as("trial %d: row %d (%s) must not sort after row %d (%s) under "
                                        + "(paymentDate desc, paymentId desc); scope=%s, filter=%s",
                                i, k - 1, describe(prev), k, describe(curr),
                                describeScope(scope), describe(filter))
                        .isLessThanOrEqualTo(0);

                if (prev.getPaymentDate().equals(curr.getPaymentDate())) {
                    sawSameDateTieBreak = true;
                    // Same date -> strictly descending payment id (ids are unique per row).
                    assertThat(prev.getPaymentId())
                            .as("trial %d: same payment date must break ties by descending payment id", i)
                            .isGreaterThan(curr.getPaymentId());
                }
            }

            if (result.size() >= 2) {
                sawMultiRow = true;
            }

            switch (scenario.scopeKind()) {
                case SOCIETY_WIDE -> sawSocietyWide = true;
                case MEMBER_SUBSET -> sawMemberSubset = true;
                case MEMBER_EMPTY -> sawMemberEmpty = true;
            }
        }

        // Guard that the generators actually exercised each scope kind and the tie-breaker.
        assertThat(sawSocietyWide)
                .as("generator should produce at least one society-wide scope")
                .isTrue();
        assertThat(sawMemberSubset)
                .as("generator should produce at least one member subset scope")
                .isTrue();
        assertThat(sawMemberEmpty)
                .as("generator should produce at least one empty member scope")
                .isTrue();
        assertThat(sawMultiRow)
                .as("generator should produce at least one multi-row result to order")
                .isTrue();
        assertThat(sawSameDateTieBreak)
                .as("generator should produce at least one same-date pair exercising the payment-id tie-breaker")
                .isTrue();
    }

    /**
     * Compares two payments under the read-model ordering: payment date descending, then
     * payment id descending. Returns a negative number when {@code a} sorts before {@code b},
     * zero when equal, positive when after — so a correctly ordered adjacent pair yields
     * {@code compare(prev, curr) <= 0}.
     */
    private static int compare(MaintenancePayment a, MaintenancePayment b) {
        int byDate = b.getPaymentDate().compareTo(a.getPaymentDate()); // descending
        if (byDate != 0) {
            return byDate;
        }
        return Long.compare(b.getPaymentId(), a.getPaymentId()); // descending
    }

    // ------------------------------------------------------------------
    // Access scope construction
    // ------------------------------------------------------------------

    private AccessScope buildScope(Scenario scenario, List<Long> unitIds) {
        return switch (scenario.scopeKind()) {
            case SOCIETY_WIDE -> AccessScope.societyWideScope();
            case MEMBER_EMPTY -> AccessScope.memberScoped(Set.of());
            case MEMBER_SUBSET -> {
                if (unitIds.isEmpty()) {
                    yield AccessScope.memberScoped(Set.of());
                }
                int keep = Math.max(1, unitIds.size() - scenario.dropCount() % unitIds.size());
                Set<Long> selected = new LinkedHashSet<>(unitIds.subList(0, keep));
                yield AccessScope.memberScoped(selected);
            }
        };
    }

    private static String describe(MaintenancePayment p) {
        return "id=" + p.getPaymentId() + ", date=" + p.getPaymentDate();
    }

    private static String describe(TransactionFilterRequest f) {
        return "{startDate=" + f.getStartDate() + ", endDate=" + f.getEndDate()
                + ", paymentMode=" + f.getPaymentMode() + ", statuses=" + f.getStatuses()
                + ", unitId=" + f.getUnitId() + ", payerType=" + f.getPayerType()
                + ", unitSearch='" + f.getUnitSearch() + "', reference='" + f.getReference() + "'}";
    }

    private static String describeScope(AccessScope s) {
        return s.societyWide() ? "SOCIETY_WIDE" : "MEMBER" + s.unitIds();
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

    private record Scenario(List<PaymentBlueprint> payments, TransactionFilterRequest filter,
                            ScopeKind scopeKind, int dropCount) {
    }

    private record PaymentBlueprint(
            String unitNumber, BigDecimal amount, LocalDate paymentDate, PaymentMode paymentMode,
            PaymentStatus status, String payerType, String payerName,
            String transactionId, String receiptNumber) {
    }

    /**
     * Generates a payment set, a filter set, a scope kind, and a drop count. Payment counts skew
     * higher and the date window is narrow so many rows share a date, exercising the payment-id
     * tie-breaker of the ordering.
     */
    private Arbitrary<Scenario> scenarios() {
        Arbitrary<List<PaymentBlueprint>> payments =
                paymentBlueprint().list().ofMinSize(2).ofMaxSize(14);
        Arbitrary<ScopeKind> scopeKinds = Arbitraries.frequency(
                net.jqwik.api.Tuple.of(5, ScopeKind.SOCIETY_WIDE),
                net.jqwik.api.Tuple.of(4, ScopeKind.MEMBER_SUBSET),
                net.jqwik.api.Tuple.of(2, ScopeKind.MEMBER_EMPTY));
        Arbitrary<Integer> dropCounts = Arbitraries.integers().between(0, 6);
        return Combinators.combine(payments, filters(), scopeKinds, dropCounts).as(Scenario::new);
    }

    private Arbitrary<PaymentBlueprint> paymentBlueprint() {
        Arbitrary<String> unitNumbers = Arbitraries.of("A-101", "A-102", "B-201", "B-202", "C-301");
        Arbitrary<BigDecimal> amounts = Arbitraries.longs().between(0L, 500_000L)
                .map(cents -> BigDecimal.valueOf(cents, 2));
        // Narrow date window (5 distinct days) so ties on payment date are common and the
        // payment-id tie-breaker is exercised frequently.
        Arbitrary<LocalDate> dates = Arbitraries.integers().between(0, 4)
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

    private Arbitrary<TransactionFilterRequest> filters() {
        Arbitrary<LocalDate> startDates = Arbitraries.integers().between(0, 4)
                .map(offset -> LocalDate.of(2024, 1, 1).plusDays(offset)).injectNull(0.6);
        Arbitrary<LocalDate> endDates = Arbitraries.integers().between(0, 4)
                .map(offset -> LocalDate.of(2024, 1, 1).plusDays(offset)).injectNull(0.6);
        Arbitrary<PaymentMode> modes = Arbitraries.of(PaymentMode.UPI, PaymentMode.CASH,
                PaymentMode.NEFT, PaymentMode.CHEQUE).injectNull(0.6);
        Arbitrary<List<PaymentStatus>> statusSets = Arbitraries.of(PaymentStatus.class)
                .list().ofMinSize(0).ofMaxSize(3).map(l -> l.isEmpty() ? null : dedup(l)).injectNull(0.5);
        Arbitrary<String> payerTypes = Arbitraries.of("OWNER", "TENANT").injectNull(0.6);
        Arbitrary<String> unitSearches = Arbitraries.of("A-1", "a-1", "B-2", "b-2", "101", "201")
                .injectNull(0.7);
        Arbitrary<String> references = Arbitraries.of("TXN", "txn", "RCPT", "rcpt", "REF", "ref")
                .injectNull(0.7);
        Arbitrary<String> blankUnitSearch = Arbitraries.of("", "   ", "\t");
        Arbitrary<String> unitSearchWithBlanks = Arbitraries.oneOf(unitSearches, blankUnitSearch.injectNull(0.7));

        return Combinators.combine(startDates, endDates, modes, statusSets, payerTypes)
                .as((start, end, mode, statusSet, payer) -> {
                    TransactionFilterRequest f = new TransactionFilterRequest();
                    f.setStartDate(start);
                    f.setEndDate(end);
                    f.setPaymentMode(mode);
                    f.setStatuses(statusSet);
                    f.setPayerType(payer);
                    return f;
                })
                .flatMap(f -> Combinators.combine(unitSearchWithBlanks, references)
                        .as((search, ref) -> {
                            f.setUnitSearch(search);
                            f.setReference(ref);
                            return f;
                        }));
    }

    private static List<PaymentStatus> dedup(List<PaymentStatus> in) {
        return new ArrayList<>(new LinkedHashSet<>(in));
    }
}
