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
 * <p><b>Property 4: Filters compose by logical AND with absent filters as identity.</b>
 * For all payment sets and any filter set, the transactions returned by the built
 * {@link Specification} are exactly those (within access scope) that satisfy every
 * active filter simultaneously (logical AND); a filter that is absent, empty, or
 * whitespace-only imposes no restriction, so an empty filter set returns the entire
 * scoped set.</p>
 *
 * <p><b>Validates: Requirements 3.7, 4.2, 5.2, 7.1, 7.4, 9.2</b></p>
 *
 * <p><b>Approach.</b> The specification requires a real JPA CriteriaBuilder/persistence
 * layer, so this is a model-based test that executes the real specification against an
 * in-memory H2 database seeded with generated {@link MaintenancePayment} records, and
 * compares the returned id set against an independent in-memory reference predicate that
 * applies the same filters with logical AND. The comparison is society-wide scope so the
 * property isolates filter composition (access-scope containment is covered by Property 2).</p>
 *
 * <p>Because jqwik's own lifecycle does not share a single Spring context, the property is
 * driven inside one JUnit {@code @Test} method over a stream of generated
 * (payment set, filter set) samples (>= 100 tries), matching the project's jqwik-based
 * property tests while keeping a single injected persistence context.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        // Use an isolated in-memory H2 database (MySQL-compatible) instead of the
        // configured MySQL datasource, and let Hibernate create the schema from the
        // entity mappings so the real Specification runs against a real query engine.
        "spring.datasource.url=jdbc:h2:mem:txn_spec_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class TransactionSpecificationAndCompositionPropertyTest {

    private static final int TRIES = 120;

    @Autowired
    private MaintenancePaymentRepository paymentRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final TransactionSpecificationBuilder builder = new TransactionSpecificationBuilder();

    @Test
    void filtersComposeByLogicalAndWithAbsentFiltersAsIdentity() {
        // sampleStream() draws values outside of jqwik's own @Property lifecycle, which lets us
        // run the generation loop inside a single Spring-managed @DataJpaTest context.
        java.util.Iterator<Scenario> scenarios = scenarios().sampleStream().iterator();

        boolean sawEmptyFilterCase = false;
        boolean sawMultiFilterCase = false;

        for (int i = 0; i < TRIES; i++) {
            Scenario scenario = scenarios.next();

            // Fresh data per trial so results are independent.
            clearDatabase();
            List<MaintenancePayment> persisted = persist(scenario.payments());

            TransactionFilterRequest filter = scenario.filter();

            // --- Real production code path under test ---
            Specification<MaintenancePayment> spec =
                    builder.build(AccessScope.societyWideScope(), filter);
            Set<Long> actualIds = paymentRepository.findAll(spec).stream()
                    .map(MaintenancePayment::getPaymentId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            // --- Independent in-memory reference: exactly the AND of every active filter ---
            Set<Long> expectedIds = persisted.stream()
                    .filter(p -> matchesAll(p, filter))
                    .map(MaintenancePayment::getPaymentId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            assertThat(actualIds)
                    .as("trial %d: spec result must equal the AND-composed reference filter for filter=%s",
                            i, describe(filter))
                    .isEqualTo(expectedIds);

            // Absent-filter-as-identity: an empty filter set returns the entire scoped set.
            if (isEmptyFilter(filter)) {
                sawEmptyFilterCase = true;
                Set<Long> allIds = persisted.stream()
                        .map(MaintenancePayment::getPaymentId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                assertThat(actualIds)
                        .as("trial %d: an absent/blank filter set must return the entire scoped set", i)
                        .isEqualTo(allIds);
            }
            if (activeFilterCount(filter) >= 2) {
                sawMultiFilterCase = true;
            }
        }

        // Guard that the generators actually exercised the two headline behaviours.
        assertThat(sawEmptyFilterCase)
                .as("generator should produce at least one empty (identity) filter set")
                .isTrue();
        assertThat(sawMultiFilterCase)
                .as("generator should produce at least one multi-filter AND case")
                .isTrue();
    }

    // ------------------------------------------------------------------
    // In-memory reference filter (mirrors TransactionSpecificationBuilder semantics)
    // ------------------------------------------------------------------

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

    private static boolean isEmptyFilter(TransactionFilterRequest f) {
        return activeFilterCount(f) == 0;
    }

    private static int activeFilterCount(TransactionFilterRequest f) {
        int count = 0;
        if (f.getStartDate() != null) count++;
        if (f.getEndDate() != null) count++;
        if (f.getPaymentMode() != null) count++;
        if (f.getStatuses() != null && !f.getStatuses().isEmpty()) count++;
        if (f.getUnitId() != null) count++;
        if (f.getPayerType() != null) count++;
        if (StringUtils.hasText(f.getUnitSearch())) count++;
        if (StringUtils.hasText(f.getReference())) count++;
        return count;
    }

    private static String describe(TransactionFilterRequest f) {
        return "{startDate=" + f.getStartDate() + ", endDate=" + f.getEndDate()
                + ", paymentMode=" + f.getPaymentMode() + ", statuses=" + f.getStatuses()
                + ", unitId=" + f.getUnitId() + ", payerType=" + f.getPayerType()
                + ", unitSearch='" + f.getUnitSearch() + "', reference='" + f.getReference() + "'}";
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
     * unit-id and unit-search filters can select subsets. Returns detached copies carrying the
     * generated ids for reference comparison.
     */
    private List<MaintenancePayment> persist(List<PaymentBlueprint> blueprints) {
        // Distinct unit numbers used by the blueprints -> one persisted Unit each, and one shared
        // bill per unit (the bill has a unique (unit, month, year) constraint and is not filtered on).
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
            // guarantee uniqueness. transaction_id has no such constraint but is suffixed too
            // so both fields stay realistic and distinct.
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

    private record Scenario(List<PaymentBlueprint> payments, TransactionFilterRequest filter) {
    }

    private record PaymentBlueprint(
            String unitNumber, BigDecimal amount, LocalDate paymentDate, PaymentMode paymentMode,
            PaymentStatus status, String payerType, String payerName,
            String transactionId, String receiptNumber) {
    }

    /**
     * Generates a payment set plus a filter set. To make filters bite meaningfully, values are
     * drawn from small overlapping domains (few unit numbers, a narrow date window, short
     * reference tokens) so a random filter often matches some-but-not-all payments.
     */
    private Arbitrary<Scenario> scenarios() {
        Arbitrary<List<PaymentBlueprint>> payments =
                paymentBlueprint().list().ofMinSize(0).ofMaxSize(12);
        return Combinators.combine(payments, filters()).as(Scenario::new);
    }

    private Arbitrary<PaymentBlueprint> paymentBlueprint() {
        Arbitrary<String> unitNumbers = Arbitraries.of("A-101", "A-102", "B-201", "b-201");
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
                .map(offset -> LocalDate.of(2024, 1, 1).plusDays(offset)).injectNull(0.5);
        Arbitrary<LocalDate> endDates = Arbitraries.integers().between(0, 30)
                .map(offset -> LocalDate.of(2024, 1, 1).plusDays(offset)).injectNull(0.5);
        Arbitrary<PaymentMode> modes = Arbitraries.of(PaymentMode.UPI, PaymentMode.CASH,
                PaymentMode.NEFT, PaymentMode.CHEQUE).injectNull(0.5);
        Arbitrary<List<PaymentStatus>> statusSets = Arbitraries.of(PaymentStatus.class)
                .list().ofMinSize(0).ofMaxSize(3).map(l -> l.isEmpty() ? null : dedup(l)).injectNull(0.3);
        Arbitrary<String> payerTypes = Arbitraries.of("OWNER", "TENANT").injectNull(0.5);
        // Unit ids are unknown ahead of persistence, so drive unit selection through unitSearch
        // (which the reference filter and the specification both evaluate against unit number).
        Arbitrary<String> unitSearches = Arbitraries.of("A-1", "a-1", "B-2", "b-2", "101", "201")
                .injectNull(0.6);
        Arbitrary<String> references = Arbitraries.of("TXN", "txn", "RCPT", "rcpt", "REF", "ref", "-")
                .injectNull(0.6);
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
