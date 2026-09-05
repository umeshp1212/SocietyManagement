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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Model-based property test for {@link TransactionSpecificationBuilder}.
 *
 * <p><b>Property 9: Unit-id filter selects a single unit.</b>
 * For all payment sets spanning several units and any existing unit id, every transaction
 * returned by the built {@link Specification} belongs to that unit (soundness), and every
 * scoped transaction whose unit is the selected unit is included (completeness). In other
 * words, the unit-id predicate partitions the scoped set on equality of the payment's unit
 * id with the chosen unit id and imposes no other restriction.</p>
 *
 * <p><b>Validates: Requirements 6.1</b></p>
 *
 * <p><b>Approach.</b> The specification requires a real JPA CriteriaBuilder/persistence
 * layer, so this is a model-based test that executes the real specification against an
 * in-memory H2 database seeded with generated {@link MaintenancePayment} records spread
 * across a small pool of real units, and compares the returned id set against an independent
 * in-memory reference predicate that applies only the unit-id equality. A society-wide scope
 * is used and no other filters are set so the property isolates unit-id selection
 * (access-scope containment is covered by Property 2 and AND composition by Property 4).</p>
 *
 * <p>Because unit ids are database-generated, the selected unit id is chosen at runtime from
 * the actually persisted unit ids for each trial. Occasionally a non-existent unit id (an id
 * not present in the persisted data) is selected to confirm that filtering on an absent unit
 * yields an empty result set.</p>
 *
 * <p>Because jqwik's own lifecycle does not share a single Spring context, the property is
 * driven inside one JUnit {@code @Test} method over a stream of generated
 * (payment set, unit selector) samples (>= 100 tries), matching the project's jqwik-based
 * property tests while keeping a single injected persistence context.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        // Use an isolated in-memory H2 database (MySQL-compatible) instead of the
        // configured MySQL datasource, and let Hibernate create the schema from the
        // entity mappings so the real Specification runs against a real query engine.
        "spring.datasource.url=jdbc:h2:mem:txn_unitid_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class TransactionSpecificationUnitIdPropertyTest {

    private static final int TRIES = 120;

    @Autowired
    private MaintenancePaymentRepository paymentRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final TransactionSpecificationBuilder builder = new TransactionSpecificationBuilder();

    @Test
    void unitIdFilterSelectsASingleUnit() {
        // sampleStream() draws values outside of jqwik's own @Property lifecycle, which lets us
        // run the generation loop inside a single Spring-managed @DataJpaTest context.
        java.util.Iterator<Scenario> scenarios = scenarios().sampleStream().iterator();

        // Guards that the generators actually exercised the property in a meaningful way:
        // a selected unit that matched some but not all payments (a real partition across units),
        // a selection matching at least one payment, a non-existent unit id (empty result), and
        // that more than one distinct unit id was selected across trials.
        boolean sawSelectionThatDroppedSomething = false;   // some scoped payment excluded by unit
        boolean sawNonEmptySelection = false;               // at least one payment matched the unit
        boolean sawExistingUnitSpanningMultipleUnits = false; // trial had payments in >1 unit
        boolean sawNonExistentUnit = false;                 // selected a unit id not in the data
        Set<Long> selectedUnitIdsSeen = new LinkedHashSet<>();

        for (int i = 0; i < TRIES; i++) {
            Scenario scenario = scenarios.next();

            // Fresh data per trial so results are independent.
            clearDatabase();
            List<MaintenancePayment> persisted = persist(scenario.payments());

            // Real, persisted unit ids present in this trial's data set (sorted for determinism).
            List<Long> unitIds = persisted.stream()
                    .map(p -> p.getUnit().getUnitId())
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            // Choose the unit id to filter on. Most trials pick an existing unit id (drawn
            // deterministically from the generated selector); a fraction pick a non-existent id.
            long selectedUnitId = chooseUnitId(scenario, unitIds);
            boolean existing = unitIds.contains(selectedUnitId);

            TransactionFilterRequest filter = new TransactionFilterRequest();
            filter.setUnitId(selectedUnitId);

            // --- Real production code path under test ---
            Specification<MaintenancePayment> spec =
                    builder.build(AccessScope.societyWideScope(), filter);
            List<MaintenancePayment> returned = paymentRepository.findAll(spec);
            Set<Long> actualIds = returned.stream()
                    .map(MaintenancePayment::getPaymentId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            // --- Independent in-memory reference: exactly the unit-id equality predicate ---
            Set<Long> expectedIds = persisted.stream()
                    .filter(p -> p.getUnit() != null
                            && selectedUnitId == p.getUnit().getUnitId())
                    .map(MaintenancePayment::getPaymentId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            // Soundness (every returned payment belongs to the selected unit) + completeness
            // (no matching payment omitted) in a single equality comparison.
            assertThat(actualIds)
                    .as("trial %d: spec result must equal the unit-id-equality reference "
                                    + "(selectedUnitId=%d, existing=%b, unitIds=%s)",
                            i, selectedUnitId, existing, unitIds)
                    .isEqualTo(expectedIds);

            // Explicit per-row soundness check: every returned payment belongs to the selected unit.
            for (MaintenancePayment p : returned) {
                assertThat(p.getUnit())
                        .as("trial %d: returned payment %d must have a unit", i, p.getPaymentId())
                        .isNotNull();
                assertThat(p.getUnit().getUnitId())
                        .as("trial %d: returned payment %d must belong to the selected unit",
                                i, p.getPaymentId())
                        .isEqualTo(selectedUnitId);
            }

            // Filtering on a non-existent unit id must return nothing.
            if (!existing) {
                sawNonExistentUnit = true;
                assertThat(actualIds)
                        .as("trial %d: a non-existent unit id must return an empty result", i)
                        .isEmpty();
            }

            // Coverage bookkeeping.
            if (existing) {
                selectedUnitIdsSeen.add(selectedUnitId);
            }
            if (!expectedIds.isEmpty()) {
                sawNonEmptySelection = true;
            }
            if (expectedIds.size() < persisted.size()) {
                sawSelectionThatDroppedSomething = true;
            }
            if (unitIds.size() > 1) {
                sawExistingUnitSpanningMultipleUnits = true;
            }
        }

        assertThat(sawNonEmptySelection)
                .as("generator should select a unit that matches at least one payment")
                .isTrue();
        assertThat(sawSelectionThatDroppedSomething)
                .as("generator should produce at least one selection that excludes some payments")
                .isTrue();
        assertThat(sawExistingUnitSpanningMultipleUnits)
                .as("generator should produce at least one trial whose payments span multiple units")
                .isTrue();
        assertThat(sawNonExistentUnit)
                .as("generator should exercise at least one non-existent unit id (empty result)")
                .isTrue();
        assertThat(selectedUnitIdsSeen.size())
                .as("generator should exercise more than one existing unit id across trials")
                .isGreaterThan(1);
    }

    /**
     * Picks the unit id to filter on for a trial. When the selector requests a non-existent unit
     * (and there is data), returns an id guaranteed to be absent from the persisted set; otherwise
     * returns an existing unit id chosen deterministically from the generated seed. When no units
     * were persisted, always returns a guaranteed-absent id.
     */
    private long chooseUnitId(Scenario scenario, List<Long> unitIds) {
        if (unitIds.isEmpty()) {
            return -1L; // no data -> any id is non-existent
        }
        if (scenario.selectNonExistent()) {
            long maxId = unitIds.get(unitIds.size() - 1);
            return maxId + 1L; // guaranteed absent from the persisted set
        }
        return unitIds.get(scenario.unitSelector() % unitIds.size());
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
     * Persists the generated payment blueprints across a small pool of real units (one bill per
     * unit) so that a unit-id filter can select the payments of a single unit out of several.
     * Returns the persisted copies carrying the generated ids.
     */
    private List<MaintenancePayment> persist(List<PaymentBlueprint> blueprints) {
        Map<String, Unit> unitsByNumber = new LinkedHashMap<>();
        Map<String, MaintenanceBill> billsByUnit = new LinkedHashMap<>();
        for (PaymentBlueprint bp : blueprints) {
            Unit unit = unitsByNumber.computeIfAbsent(bp.unitNumber(), this::persistUnit);
            billsByUnit.computeIfAbsent(bp.unitNumber(), k -> persistBill(unit));
        }

        List<MaintenancePayment> persisted = new ArrayList<>();
        int index = 0;
        for (PaymentBlueprint bp : blueprints) {
            Unit unit = unitsByNumber.get(bp.unitNumber());
            MaintenanceBill bill = billsByUnit.get(bp.unitNumber());
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

    private record Scenario(List<PaymentBlueprint> payments, int unitSelector,
                            boolean selectNonExistent) {
    }

    private record PaymentBlueprint(String unitNumber, BigDecimal amount, LocalDate paymentDate,
                                    PaymentMode paymentMode, PaymentStatus status) {
    }

    /**
     * Generates a payment set spread across a small pool of unit numbers, a non-negative selector
     * used to pick an existing unit id at runtime, and a flag that occasionally selects a
     * non-existent unit id. Drawing unit numbers from a small pool means many trials span several
     * units, so selecting one unit frequently matches some but not all payments (a real partition).
     */
    private Arbitrary<Scenario> scenarios() {
        Arbitrary<List<PaymentBlueprint>> payments =
                paymentBlueprint().list().ofMinSize(1).ofMaxSize(12);
        Arbitrary<Integer> unitSelectors = Arbitraries.integers().between(0, 1_000);
        // Occasionally target a unit id that is not present in the data (empty-result case).
        Arbitrary<Boolean> selectNonExistent = Arbitraries.frequency(
                net.jqwik.api.Tuple.of(4, Boolean.FALSE),
                net.jqwik.api.Tuple.of(1, Boolean.TRUE));
        return Combinators.combine(payments, unitSelectors, selectNonExistent).as(Scenario::new);
    }

    private Arbitrary<PaymentBlueprint> paymentBlueprint() {
        // Several distinct unit numbers so a single-unit selection is a meaningful partition.
        Arbitrary<String> unitNumbers = Arbitraries.of("A-101", "A-102", "B-201", "B-202", "C-301");
        Arbitrary<BigDecimal> amounts = Arbitraries.longs().between(0L, 500_000L)
                .map(cents -> BigDecimal.valueOf(cents, 2));
        Arbitrary<PaymentMode> modes = Arbitraries.of(PaymentMode.class);
        Arbitrary<PaymentStatus> statuses = Arbitraries.of(PaymentStatus.class);
        return Combinators.combine(unitNumbers, amounts, paymentDate(), modes, statuses)
                .as(PaymentBlueprint::new);
    }

    /** A fixed-window date; the payment date is irrelevant to this property but must be non-null. */
    private Arbitrary<LocalDate> paymentDate() {
        return Arbitraries.integers().between(0, 30)
                .map(LocalDate.of(2024, 1, 1)::plusDays);
    }
}
