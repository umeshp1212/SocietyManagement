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
 * <p><b>Property 5: Date-range filter selects an inclusive interval.</b>
 * For all payment sets and any start date and/or end date, every transaction returned by
 * the built {@link Specification} has a payment date on or after the start date (when a
 * start date is provided) and on or before the end date (when an end date is provided),
 * and every scoped transaction whose payment date lies within those bounds is included.
 * The interval is inclusive of both endpoints, and an absent bound imposes no restriction
 * on that side.</p>
 *
 * <p><b>Validates: Requirements 3.1, 3.2, 3.3</b></p>
 *
 * <p><b>Approach.</b> The specification requires a real JPA CriteriaBuilder/persistence
 * layer, so this is a model-based test that executes the real specification against an
 * in-memory H2 database seeded with generated {@link MaintenancePayment} records, and
 * compares the returned id set against an independent in-memory reference predicate that
 * applies only the inclusive date-range bounds. A society-wide scope is used and no other
 * filters are set so the property isolates date-range selection (access-scope containment
 * is covered by Property 2 and AND composition by Property 4).</p>
 *
 * <p>Because jqwik's own lifecycle does not share a single Spring context, the property is
 * driven inside one JUnit {@code @Test} method over a stream of generated
 * (payment set, date-bound selection) samples (>= 100 tries), matching the project's
 * jqwik-based property tests while keeping a single injected persistence context.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        // Use an isolated in-memory H2 database (MySQL-compatible) instead of the
        // configured MySQL datasource, and let Hibernate create the schema from the
        // entity mappings so the real Specification runs against a real query engine.
        "spring.datasource.url=jdbc:h2:mem:txn_daterange_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class TransactionSpecificationDateRangePropertyTest {

    private static final int TRIES = 120;

    /** The date window payments and bounds are drawn from, so bounds frequently split the set. */
    private static final LocalDate WINDOW_START = LocalDate.of(2024, 1, 1);
    private static final int WINDOW_DAYS = 30;

    @Autowired
    private MaintenancePaymentRepository paymentRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final TransactionSpecificationBuilder builder = new TransactionSpecificationBuilder();

    @Test
    void dateRangeFilterSelectsAnInclusiveInterval() {
        // sampleStream() draws values outside of jqwik's own @Property lifecycle, which lets us
        // run the generation loop inside a single Spring-managed @DataJpaTest context.
        java.util.Iterator<Scenario> scenarios = scenarios().sampleStream().iterator();

        // Guards that the generators actually exercised each of the three requirement clauses
        // plus the inclusive-endpoint edge cases.
        boolean sawStartAndEnd = false;          // Req 3.1
        boolean sawStartOnly = false;            // Req 3.2
        boolean sawEndOnly = false;              // Req 3.3
        boolean sawNeither = false;              // identity on both sides
        boolean sawStartEndpointHit = false;     // a payment exactly on the start date was returned
        boolean sawEndEndpointHit = false;       // a payment exactly on the end date was returned
        boolean sawRangeThatDroppedSomething = false;

        for (int i = 0; i < TRIES; i++) {
            Scenario scenario = scenarios.next();

            // Fresh data per trial so results are independent.
            clearDatabase();
            List<MaintenancePayment> persisted = persist(scenario.payments());

            TransactionFilterRequest filter = new TransactionFilterRequest();
            filter.setStartDate(scenario.startDate());
            filter.setEndDate(scenario.endDate());

            // --- Real production code path under test ---
            Specification<MaintenancePayment> spec =
                    builder.build(AccessScope.societyWideScope(), filter);
            List<MaintenancePayment> returned = paymentRepository.findAll(spec);
            Set<Long> actualIds = returned.stream()
                    .map(MaintenancePayment::getPaymentId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            // --- Independent in-memory reference: exactly the inclusive date-range predicate ---
            Set<Long> expectedIds = persisted.stream()
                    .filter(p -> withinInclusiveRange(p.getPaymentDate(),
                            scenario.startDate(), scenario.endDate()))
                    .map(MaintenancePayment::getPaymentId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            // Containment (every returned date is within bounds) + completeness (no in-range
            // payment omitted) in a single equality comparison.
            assertThat(actualIds)
                    .as("trial %d: spec result must equal the inclusive date-range reference "
                                    + "(startDate=%s, endDate=%s)",
                            i, scenario.startDate(), scenario.endDate())
                    .isEqualTo(expectedIds);

            // Explicit per-row bound check: every returned payment date honours both bounds.
            for (MaintenancePayment p : returned) {
                if (scenario.startDate() != null) {
                    assertThat(!p.getPaymentDate().isBefore(scenario.startDate()))
                            .as("trial %d: returned paymentDate %s must be on/after startDate %s",
                                    i, p.getPaymentDate(), scenario.startDate())
                            .isTrue();
                }
                if (scenario.endDate() != null) {
                    assertThat(!p.getPaymentDate().isAfter(scenario.endDate()))
                            .as("trial %d: returned paymentDate %s must be on/before endDate %s",
                                    i, p.getPaymentDate(), scenario.endDate())
                            .isTrue();
                }
            }

            // Coverage bookkeeping.
            boolean hasStart = scenario.startDate() != null;
            boolean hasEnd = scenario.endDate() != null;
            if (hasStart && hasEnd) sawStartAndEnd = true;
            if (hasStart && !hasEnd) sawStartOnly = true;
            if (!hasStart && hasEnd) sawEndOnly = true;
            if (!hasStart && !hasEnd) sawNeither = true;

            if (hasStart) {
                boolean endpointReturned = returned.stream()
                        .anyMatch(p -> p.getPaymentDate().isEqual(scenario.startDate()));
                if (endpointReturned) sawStartEndpointHit = true;
            }
            if (hasEnd) {
                boolean endpointReturned = returned.stream()
                        .anyMatch(p -> p.getPaymentDate().isEqual(scenario.endDate()));
                if (endpointReturned) sawEndEndpointHit = true;
            }
            if ((hasStart || hasEnd) && expectedIds.size() < persisted.size()) {
                sawRangeThatDroppedSomething = true;
            }
        }

        assertThat(sawStartAndEnd)
                .as("generator should produce at least one start+end range (Req 3.1)")
                .isTrue();
        assertThat(sawStartOnly)
                .as("generator should produce at least one start-only range (Req 3.2)")
                .isTrue();
        assertThat(sawEndOnly)
                .as("generator should produce at least one end-only range (Req 3.3)")
                .isTrue();
        assertThat(sawNeither)
                .as("generator should produce at least one range with neither bound (identity)")
                .isTrue();
        assertThat(sawStartEndpointHit)
                .as("generator should return at least one payment exactly on the start date (inclusive)")
                .isTrue();
        assertThat(sawEndEndpointHit)
                .as("generator should return at least one payment exactly on the end date (inclusive)")
                .isTrue();
        assertThat(sawRangeThatDroppedSomething)
                .as("generator should produce at least one bounded range that excludes some payments")
                .isTrue();
    }

    // ------------------------------------------------------------------
    // In-memory reference predicate (inclusive interval)
    // ------------------------------------------------------------------

    /**
     * Inclusive date-range membership mirroring {@link TransactionSpecificationBuilder}:
     * a null bound imposes no restriction on that side; a present bound is inclusive.
     */
    private static boolean withinInclusiveRange(LocalDate paymentDate, LocalDate start, LocalDate end) {
        if (start != null && paymentDate.isBefore(start)) {
            return false;
        }
        if (end != null && paymentDate.isAfter(end)) {
            return false;
        }
        return true;
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
     * Persists the generated payment blueprints, reusing a single unit/bill pair (units are not
     * filtered on in this property). Returns the persisted copies carrying the generated ids.
     */
    private List<MaintenancePayment> persist(List<PaymentBlueprint> blueprints) {
        List<MaintenancePayment> persisted = new ArrayList<>();
        if (blueprints.isEmpty()) {
            return persisted;
        }
        Unit unit = persistUnit("A-101");
        MaintenanceBill bill = persistBill(unit);

        int index = 0;
        for (PaymentBlueprint bp : blueprints) {
            MaintenancePayment payment = MaintenancePayment.builder()
                    .bill(bill)
                    .unit(unit)
                    .amount(bp.amount())
                    .paymentDate(bp.paymentDate())
                    .paymentMode(bp.paymentMode())
                    .status(bp.status())
                    .payerType("OWNER")
                    .payerName("Payer" + index)
                    // receipt_number carries a unique constraint; make it unique per payment.
                    .receiptNumber("RCPT-" + index)
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

    private record Scenario(List<PaymentBlueprint> payments, LocalDate startDate, LocalDate endDate) {
    }

    private record PaymentBlueprint(BigDecimal amount, LocalDate paymentDate,
                                    PaymentMode paymentMode, PaymentStatus status) {
    }

    /**
     * Generates a payment set (payment dates drawn from a narrow window) plus a start and/or end
     * bound drawn from the same window so bounds frequently split the set and land exactly on
     * payment dates (exercising the inclusive endpoints). Each of start-only, end-only, both, and
     * neither is reachable because both bounds inject null independently.
     */
    private Arbitrary<Scenario> scenarios() {
        Arbitrary<List<PaymentBlueprint>> payments =
                paymentBlueprint().list().ofMinSize(1).ofMaxSize(12);
        Arbitrary<LocalDate> startDates = windowDate().injectNull(0.35);
        Arbitrary<LocalDate> endDates = windowDate().injectNull(0.35);
        return Combinators.combine(payments, startDates, endDates).as(Scenario::new);
    }

    private Arbitrary<PaymentBlueprint> paymentBlueprint() {
        Arbitrary<BigDecimal> amounts = Arbitraries.longs().between(0L, 500_000L)
                .map(cents -> BigDecimal.valueOf(cents, 2));
        Arbitrary<PaymentMode> modes = Arbitraries.of(PaymentMode.UPI, PaymentMode.CASH,
                PaymentMode.NEFT, PaymentMode.CHEQUE);
        Arbitrary<PaymentStatus> statuses = Arbitraries.of(PaymentStatus.class);
        return Combinators.combine(amounts, windowDate(), modes, statuses)
                .as((amount, date, mode, status) -> new PaymentBlueprint(amount, date, mode, status));
    }

    /** A date drawn from the shared narrow window [WINDOW_START, WINDOW_START + WINDOW_DAYS]. */
    private Arbitrary<LocalDate> windowDate() {
        return Arbitraries.integers().between(0, WINDOW_DAYS)
                .map(WINDOW_START::plusDays);
    }
}
