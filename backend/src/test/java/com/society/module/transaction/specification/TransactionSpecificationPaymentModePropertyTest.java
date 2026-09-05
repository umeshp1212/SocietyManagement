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
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Model-based property test for {@link TransactionSpecificationBuilder}.
 *
 * <p><b>Property 7: Payment-mode filter selects exactly one mode.</b>
 * For all payment sets and any recognized {@link PaymentMode}, every transaction returned
 * by the built {@link Specification} has that payment mode (soundness), and every scoped
 * transaction whose payment mode equals the selected mode is included (completeness). In
 * other words, the payment-mode predicate partitions the scoped set on equality with the
 * chosen mode and imposes no other restriction.</p>
 *
 * <p><b>Validates: Requirements 4.1</b></p>
 *
 * <p><b>Approach.</b> The specification requires a real JPA CriteriaBuilder/persistence
 * layer, so this is a model-based test that executes the real specification against an
 * in-memory H2 database seeded with generated {@link MaintenancePayment} records, and
 * compares the returned id set against an independent in-memory reference predicate that
 * applies only the payment-mode equality. A society-wide scope is used and no other
 * filters are set so the property isolates payment-mode selection (access-scope
 * containment is covered by Property 2 and AND composition by Property 4).</p>
 *
 * <p>Because jqwik's own lifecycle does not share a single Spring context, the property is
 * driven inside one JUnit {@code @Test} method over a stream of generated
 * (payment set, selected mode) samples (>= 100 tries), matching the project's
 * jqwik-based property tests while keeping a single injected persistence context.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        // Use an isolated in-memory H2 database (MySQL-compatible) instead of the
        // configured MySQL datasource, and let Hibernate create the schema from the
        // entity mappings so the real Specification runs against a real query engine.
        "spring.datasource.url=jdbc:h2:mem:txn_paymentmode_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class TransactionSpecificationPaymentModePropertyTest {

    private static final int TRIES = 120;

    /** The recognized payment modes payments and the selected filter value are drawn from. */
    private static final PaymentMode[] MODES = PaymentMode.values();

    @Autowired
    private MaintenancePaymentRepository paymentRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final TransactionSpecificationBuilder builder = new TransactionSpecificationBuilder();

    @Test
    void paymentModeFilterSelectsExactlyOneMode() {
        // sampleStream() draws values outside of jqwik's own @Property lifecycle, which lets us
        // run the generation loop inside a single Spring-managed @DataJpaTest context.
        java.util.Iterator<Scenario> scenarios = scenarios().sampleStream().iterator();

        // Guards that the generators actually exercised the property in a meaningful way:
        // a selected mode that matched some but not all payments (a real partition), and that
        // more than one distinct mode value was selected across trials.
        boolean sawSelectionThatDroppedSomething = false;   // some scoped payment excluded by mode
        boolean sawNonEmptySelection = false;               // at least one payment matched the mode
        Set<PaymentMode> selectedModesSeen = EnumSet.noneOf(PaymentMode.class);

        for (int i = 0; i < TRIES; i++) {
            Scenario scenario = scenarios.next();

            // Fresh data per trial so results are independent.
            clearDatabase();
            List<MaintenancePayment> persisted = persist(scenario.payments());

            TransactionFilterRequest filter = new TransactionFilterRequest();
            filter.setPaymentMode(scenario.selectedMode());

            // --- Real production code path under test ---
            Specification<MaintenancePayment> spec =
                    builder.build(AccessScope.societyWideScope(), filter);
            List<MaintenancePayment> returned = paymentRepository.findAll(spec);
            Set<Long> actualIds = returned.stream()
                    .map(MaintenancePayment::getPaymentId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            // --- Independent in-memory reference: exactly the payment-mode equality predicate ---
            Set<Long> expectedIds = persisted.stream()
                    .filter(p -> p.getPaymentMode() == scenario.selectedMode())
                    .map(MaintenancePayment::getPaymentId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            // Soundness (every returned payment has the selected mode) + completeness (no
            // matching payment omitted) in a single equality comparison.
            assertThat(actualIds)
                    .as("trial %d: spec result must equal the payment-mode-equality reference "
                                    + "(selectedMode=%s)",
                            i, scenario.selectedMode())
                    .isEqualTo(expectedIds);

            // Explicit per-row soundness check: every returned payment carries the selected mode.
            for (MaintenancePayment p : returned) {
                assertThat(p.getPaymentMode())
                        .as("trial %d: returned payment %d must have the selected mode",
                                i, p.getPaymentId())
                        .isEqualTo(scenario.selectedMode());
            }

            // Coverage bookkeeping.
            selectedModesSeen.add(scenario.selectedMode());
            if (!expectedIds.isEmpty()) {
                sawNonEmptySelection = true;
            }
            if (expectedIds.size() < persisted.size()) {
                sawSelectionThatDroppedSomething = true;
            }
        }

        assertThat(sawNonEmptySelection)
                .as("generator should select a mode that matches at least one payment")
                .isTrue();
        assertThat(sawSelectionThatDroppedSomething)
                .as("generator should produce at least one selection that excludes some payments")
                .isTrue();
        assertThat(selectedModesSeen.size())
                .as("generator should exercise more than one recognized payment mode across trials")
                .isGreaterThan(1);
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

    private record Scenario(List<PaymentBlueprint> payments, PaymentMode selectedMode) {
    }

    private record PaymentBlueprint(BigDecimal amount, LocalDate paymentDate,
                                    PaymentMode paymentMode, PaymentStatus status) {
    }

    /**
     * Generates a payment set (each payment carrying a mode drawn from the full recognized set)
     * plus a single selected mode drawn from the same recognized set. Because both the payment
     * modes and the selected mode are drawn from the same small set, the selection frequently
     * matches some but not all payments (a real partition) and occasionally matches none.
     */
    private Arbitrary<Scenario> scenarios() {
        Arbitrary<List<PaymentBlueprint>> payments =
                paymentBlueprint().list().ofMinSize(1).ofMaxSize(12);
        Arbitrary<PaymentMode> selectedMode = mode();
        return Combinators.combine(payments, selectedMode).as(Scenario::new);
    }

    private Arbitrary<PaymentBlueprint> paymentBlueprint() {
        Arbitrary<BigDecimal> amounts = Arbitraries.longs().between(0L, 500_000L)
                .map(cents -> BigDecimal.valueOf(cents, 2));
        Arbitrary<PaymentStatus> statuses = Arbitraries.of(PaymentStatus.class);
        return Combinators.combine(amounts, paymentDate(), mode(), statuses)
                .as((amount, date, m, status) -> new PaymentBlueprint(amount, date, m, status));
    }

    /** Any recognized payment mode, so the generated set spans the whole {@link PaymentMode} space. */
    private Arbitrary<PaymentMode> mode() {
        return Arbitraries.of(MODES);
    }

    /** A fixed-window date; the payment date is irrelevant to this property but must be non-null. */
    private Arbitrary<LocalDate> paymentDate() {
        return Arbitraries.integers().between(0, 30)
                .map(LocalDate.of(2024, 1, 1)::plusDays);
    }
}
