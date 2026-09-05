package com.society.module.transaction.specification;

import com.society.enums.OccupancyStatus;
import com.society.enums.UnitType;
import com.society.module.maintenance.entity.MaintenanceBill;
import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentMode;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentStatus;
import com.society.module.maintenance.repository.MaintenancePaymentRepository;
import com.society.module.owner.entity.Unit;
import com.society.module.transaction.dto.TransactionFilterRequest;
import com.society.module.transaction.service.AccessScope;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Model-based property test for {@link TransactionSpecificationBuilder}.
 *
 * <p><b>Property 2: Member results never escape access scope.</b>
 * For all member access scopes (a set of linked unit IDs) and any set of payments and
 * any filter set, every transaction returned in the list belongs to a unit within that
 * member's scope, and no in-scope transaction that satisfies the filters is omitted. For
 * an administrator (society-wide) scope, no unit restriction is imposed.</p>
 *
 * <p><b>Validates: Requirements 2.1, 7.2, 7.3, 10.2, 10.4</b></p>
 *
 * <p><b>Approach.</b> The specification requires a real JPA CriteriaBuilder/persistence
 * layer, so this is a model-based test that executes the real specification against an
 * in-memory H2 database seeded with generated {@link MaintenancePayment} records, and
 * compares the returned id set against an independent in-memory reference predicate. The
 * reference applies the same filters (logical AND) and then intersects with the access
 * scope: for a society-wide scope no unit restriction is applied, for a member scope only
 * payments whose unit id is in the scope survive, and for an empty member scope the result
 * is always empty. The comparison asserts both containment (nothing escapes the scope) and
 * completeness (no in-scope, filter-matching payment is omitted).</p>
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
        // entity mappings so the real Specification runs against a real query engine.
        "spring.datasource.url=jdbc:h2:mem:txn_scope_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class TransactionSpecificationAccessScopePropertyTest {

    private static final int TRIES = 120;

    /** Selects which kind of access scope a trial exercises. */
    private enum ScopeKind {SOCIETY_WIDE, MEMBER_SUBSET, MEMBER_EMPTY}

    @Autowired
    private MaintenancePaymentRepository paymentRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final TransactionSpecificationBuilder builder = new TransactionSpecificationBuilder();

    @Test
    void memberResultsNeverEscapeAccessScope() {
        // sampleStream() draws values outside of jqwik's own @Property lifecycle, which lets us
        // run the generation loop inside a single Spring-managed @DataJpaTest context.
        java.util.Iterator<Scenario> scenarios = scenarios().sampleStream().iterator();

        boolean sawSocietyWide = false;
        boolean sawMemberSubset = false;
        boolean sawMemberEmpty = false;
        // Ensure the member-subset case actually restricts results at least once.
        boolean sawMemberSubsetThatDroppedSomething = false;

        for (int i = 0; i < TRIES; i++) {
            Scenario scenario = scenarios.next();

            // Fresh data per trial so results are independent.
            clearDatabase();
            List<MaintenancePayment> persisted = persist(scenario.payments());

            // Real, persisted unit ids present in this trial's data set.
            List<Long> unitIds = persisted.stream()
                    .map(p -> p.getUnit().getUnitId())
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            AccessScope scope = buildScope(scenario, unitIds);
            TransactionFilterRequest filter = scenario.filter();

            // --- Real production code path under test ---
            Specification<MaintenancePayment> spec = builder.build(scope, filter);
            Set<Long> actualIds = paymentRepository.findAll(spec).stream()
                    .map(MaintenancePayment::getPaymentId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            // --- Independent reference: AND-of-filters intersected with the access scope ---
            Set<Long> expectedIds = persisted.stream()
                    .filter(p -> matchesAll(p, filter))
                    .filter(p -> inScope(p, scope))
                    .map(MaintenancePayment::getPaymentId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            // Containment + completeness in one comparison: the returned set equals exactly the
            // in-scope, filter-matching payments (nothing escapes, nothing is omitted).
            assertThat(actualIds)
                    .as("trial %d: spec result must equal the in-scope, filter-matching payments "
                            + "(scope=%s, filter=%s)", i, describeScope(scope), describe(filter))
                    .isEqualTo(expectedIds);

            // Explicit containment guard for member scopes: no returned unit id is outside the scope.
            if (!scope.societyWide()) {
                Set<Long> returnedUnitIds = paymentRepository.findAll(spec).stream()
                        .map(p -> p.getUnit().getUnitId())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                assertThat(scope.unitIds())
                        .as("trial %d: every returned unit id must lie within the member scope", i)
                        .containsAll(returnedUnitIds);
            }

            switch (scenario.scopeKind()) {
                case SOCIETY_WIDE -> sawSocietyWide = true;
                case MEMBER_SUBSET -> {
                    sawMemberSubset = true;
                    // A subset scope that excluded at least one otherwise-matching payment.
                    Set<Long> filterOnly = persisted.stream()
                            .filter(p -> matchesAll(p, filter))
                            .map(MaintenancePayment::getPaymentId)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    if (filterOnly.size() > expectedIds.size()) {
                        sawMemberSubsetThatDroppedSomething = true;
                    }
                }
                case MEMBER_EMPTY -> {
                    sawMemberEmpty = true;
                    assertThat(actualIds)
                            .as("trial %d: an empty member scope must always return an empty result", i)
                            .isEmpty();
                }
            }
        }

        // Guard that the generators actually exercised each scope kind.
        assertThat(sawSocietyWide)
                .as("generator should produce at least one society-wide scope")
                .isTrue();
        assertThat(sawMemberSubset)
                .as("generator should produce at least one member subset scope")
                .isTrue();
        assertThat(sawMemberEmpty)
                .as("generator should produce at least one empty member scope")
                .isTrue();
        assertThat(sawMemberSubsetThatDroppedSomething)
                .as("generator should produce at least one member scope that restricts the result set")
                .isTrue();
    }

    // ------------------------------------------------------------------
    // Access scope construction + reference predicates
    // ------------------------------------------------------------------

    /**
     * Builds the access scope for a trial from the generated selection and the real persisted
     * unit ids. A member subset keeps a deterministic slice of the present unit ids so the scope
     * often matches some-but-not-all payments; when only one unit exists the "subset" degrades to
     * that single unit (still a valid, meaningful member scope).
     */
    private AccessScope buildScope(Scenario scenario, List<Long> unitIds) {
        return switch (scenario.scopeKind()) {
            case SOCIETY_WIDE -> AccessScope.societyWideScope();
            case MEMBER_EMPTY -> AccessScope.memberScoped(Set.of());
            case MEMBER_SUBSET -> {
                if (unitIds.isEmpty()) {
                    // No units at all -> an empty member scope is the only meaningful member scope.
                    yield AccessScope.memberScoped(Set.of());
                }
                // Keep roughly half of the present unit ids (at least one) using the generated seed.
                int keep = Math.max(1, unitIds.size() - scenario.dropCount() % unitIds.size());
                Set<Long> selected = new LinkedHashSet<>(unitIds.subList(0, keep));
                yield AccessScope.memberScoped(selected);
            }
        };
    }

    private static boolean inScope(MaintenancePayment p, AccessScope scope) {
        if (scope.societyWide()) {
            return true;
        }
        if (scope.unitIds().isEmpty()) {
            return false;
        }
        return p.getUnit() != null && scope.unitIds().contains(p.getUnit().getUnitId());
    }

    /**
     * In-memory reference filter mirroring {@link TransactionSpecificationBuilder} filter
     * semantics (access scope is applied separately via {@link #inScope}).
     */
    private static boolean matchesAll(MaintenancePayment p, TransactionFilterRequest f) {
        if (f.getStartDate() != null && p.getPaymentDate().isBefore(f.getStartDate())) {
            return false;
        }
        if (f.getEndDate() != null && p.getPaymentDate().isAfter(f.getEndDate())) {
            return false;
        }
        if (f.getPaymentMode() != null && p.getPaymentMode() != f.getPaymentMode()) {
            return false;
        }
        if (f.getStatuses() != null && !f.getStatuses().isEmpty()
                && !f.getStatuses().contains(p.getStatus())) {
            return false;
        }
        if (f.getUnitId() != null
                && (p.getUnit() == null || !f.getUnitId().equals(p.getUnit().getUnitId()))) {
            return false;
        }
        if (f.getPayerType() != null && !f.getPayerType().equals(p.getPayerType())) {
            return false;
        }
        if (StringUtils.hasText(f.getUnitSearch())) {
            String number = p.getUnit() == null ? null : p.getUnit().getUnitNumber();
            if (number == null
                    || !number.toLowerCase().contains(f.getUnitSearch().toLowerCase())) {
                return false;
            }
        }
        if (StringUtils.hasText(f.getReference())) {
            String term = f.getReference().toLowerCase();
            boolean receiptMatch = p.getReceiptNumber() != null
                    && p.getReceiptNumber().toLowerCase().contains(term);
            boolean txnMatch = p.getTransactionId() != null
                    && p.getTransactionId().toLowerCase().contains(term);
            if (!receiptMatch && !txnMatch) {
                return false;
            }
        }
        return true;
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

    /**
     * Persists the generated payment blueprints, reusing a small pool of real units so that
     * access scopes can select subsets of unit ids. Returns detached copies carrying the
     * generated ids for reference comparison.
     */
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
            // receipt_number carries a unique constraint; keep the generated token as a prefix
            // (so reference-search substrings still match) and append a per-payment suffix to
            // guarantee uniqueness.
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
     * Generates a payment set, a filter set, a scope kind, and a drop count used to derive a
     * member subset from the trial's persisted unit ids. Values are drawn from small overlapping
     * domains (a few unit numbers, a narrow date window, short reference tokens) so scopes and
     * filters frequently match some-but-not-all payments.
     */
    private Arbitrary<Scenario> scenarios() {
        // Require at least a couple of payments so member subsets can meaningfully restrict results.
        Arbitrary<List<PaymentBlueprint>> payments =
                paymentBlueprint().list().ofMinSize(1).ofMaxSize(12);
        // Bias toward member subsets (the headline case) while still exercising the others.
        Arbitrary<ScopeKind> scopeKinds = Arbitraries.frequency(
                net.jqwik.api.Tuple.of(5, ScopeKind.MEMBER_SUBSET),
                net.jqwik.api.Tuple.of(2, ScopeKind.SOCIETY_WIDE),
                net.jqwik.api.Tuple.of(2, ScopeKind.MEMBER_EMPTY));
        Arbitrary<Integer> dropCounts = Arbitraries.integers().between(0, 6);
        return Combinators.combine(payments, filters(), scopeKinds, dropCounts).as(Scenario::new);
    }

    private Arbitrary<PaymentBlueprint> paymentBlueprint() {
        // Several distinct unit numbers so subsets of unit ids are meaningful.
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

        // Build in two stages because Combinators.combine supports up to 8 arbitraries and the
        // record has 9 components. The first stage packs the reference tokens into a String[2].
        Arbitrary<String[]> refPair = Combinators.combine(txnIds, receipts)
                .as((txn, receipt) -> new String[]{txn, receipt});

        return Combinators.combine(unitNumbers, amounts, dates, modes, statuses, payerTypes, payerNames, refPair)
                .as((unitNumber, amount, date, mode, status, payerType, payerName, refs) ->
                        new PaymentBlueprint(unitNumber, amount, date, mode, status,
                                payerType, payerName, refs[0], refs[1]));
    }

    private Arbitrary<TransactionFilterRequest> filters() {
        Arbitrary<LocalDate> startDates = Arbitraries.integers().between(0, 30)
                .map(offset -> LocalDate.of(2024, 1, 1).plusDays(offset)).injectNull(0.6);
        Arbitrary<LocalDate> endDates = Arbitraries.integers().between(0, 30)
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
        // Whitespace-only values must behave as identity (no restriction).
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
