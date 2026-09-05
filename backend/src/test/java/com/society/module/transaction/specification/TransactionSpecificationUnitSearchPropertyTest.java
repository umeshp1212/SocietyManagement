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
import net.jqwik.api.Tuple;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Model-based property test for {@link TransactionSpecificationBuilder}.
 *
 * <p><b>Property 11: Unit-search matches unit number case-insensitively.</b>
 * For all payment sets and any unit search term of length 1-50, every transaction returned
 * by the built {@link Specification} has a unit number that contains the search term under
 * case-insensitive matching (soundness), and every scoped transaction whose unit number
 * contains the term (case-insensitively) is included (completeness). In other words, the
 * unit-search predicate keeps exactly the scoped payments whose {@code unit.unitNumber}
 * lower-cased contains the term lower-cased, and imposes no other restriction.</p>
 *
 * <p><b>Validates: Requirements 6.4</b></p>
 *
 * <p><b>Approach.</b> The specification requires a real JPA CriteriaBuilder/persistence
 * layer, so this is a model-based test that executes the real specification against an
 * in-memory H2 database seeded with generated {@link MaintenancePayment} records spread
 * across a small pool of mixed-case real unit numbers, and compares the returned id set
 * against an independent in-memory reference predicate that applies only the
 * case-insensitive "contains" match on the unit number. A society-wide scope is used and no
 * other filters are set so the property isolates unit-search selection (access-scope
 * containment is covered by Property 2 and AND composition by Property 4).</p>
 *
 * <p>Search terms are drawn so they are frequently substrings of some persisted unit number
 * (in varied letter casing, to exercise case-insensitivity) and occasionally match nothing.
 * Term length is constrained to the specified 1-50 range.</p>
 *
 * <p>Because jqwik's own lifecycle does not share a single Spring context, the property is
 * driven inside one JUnit {@code @Test} method over a stream of generated
 * (payment set, search term) samples (>= 100 tries), matching the project's jqwik-based
 * property tests while keeping a single injected persistence context.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        // Use an isolated in-memory H2 database (MySQL-compatible) instead of the
        // configured MySQL datasource, and let Hibernate create the schema from the
        // entity mappings so the real Specification runs against a real query engine.
        "spring.datasource.url=jdbc:h2:mem:txn_unitsearch_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class TransactionSpecificationUnitSearchPropertyTest {

    private static final int TRIES = 120;

    /**
     * Mixed-case unit numbers so the search term (in varied casing) exercises the
     * case-insensitive match against differently cased stored values.
     */
    private static final String[] UNIT_NUMBERS =
            {"A-101", "a-102", "B-201", "b-202", "Wing-C-301", "wing-c-302"};

    @Autowired
    private MaintenancePaymentRepository paymentRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final TransactionSpecificationBuilder builder = new TransactionSpecificationBuilder();

    @Test
    void unitSearchMatchesUnitNumberCaseInsensitively() {
        // sampleStream() draws values outside of jqwik's own @Property lifecycle, which lets us
        // run the generation loop inside a single Spring-managed @DataJpaTest context.
        java.util.Iterator<Scenario> scenarios = scenarios().sampleStream().iterator();

        // Guards that the generators actually exercised the property in a meaningful way:
        // a term that matched some but not all payments (a real partition), a term whose casing
        // differed from the stored casing yet still matched (true case-insensitivity), a term
        // matching nothing (empty result), and more than one distinct term across trials.
        boolean sawSelectionThatDroppedSomething = false;  // some scoped payment excluded by term
        boolean sawNonEmptySelection = false;              // at least one payment matched the term
        boolean sawCaseDifferingMatch = false;             // matched despite differing letter case
        boolean sawEmptyResult = false;                    // a term matched nothing
        Set<String> termsSeen = new TreeSet<>();

        for (int i = 0; i < TRIES; i++) {
            Scenario scenario = scenarios.next();

            // Fresh data per trial so results are independent.
            clearDatabase();
            List<MaintenancePayment> persisted = persist(scenario.payments());

            String term = scenario.term();

            TransactionFilterRequest filter = new TransactionFilterRequest();
            filter.setUnitSearch(term);

            // --- Real production code path under test ---
            Specification<MaintenancePayment> spec =
                    builder.build(AccessScope.societyWideScope(), filter);
            List<MaintenancePayment> returned = paymentRepository.findAll(spec);
            Set<Long> actualIds = returned.stream()
                    .map(MaintenancePayment::getPaymentId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            // --- Independent in-memory reference: exactly the case-insensitive "contains" match ---
            String termLower = term.toLowerCase(Locale.ROOT);
            Set<Long> expectedIds = persisted.stream()
                    .filter(p -> p.getUnit() != null
                            && p.getUnit().getUnitNumber() != null
                            && p.getUnit().getUnitNumber().toLowerCase(Locale.ROOT).contains(termLower))
                    .map(MaintenancePayment::getPaymentId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            // Soundness (every returned payment's unit number contains the term) + completeness
            // (no matching payment omitted) in a single equality comparison.
            assertThat(actualIds)
                    .as("trial %d: spec result must equal the case-insensitive unit-search "
                                    + "reference (term=%s)",
                            i, term)
                    .isEqualTo(expectedIds);

            // Explicit per-row soundness check: every returned payment's unit number contains the
            // term case-insensitively.
            for (MaintenancePayment p : returned) {
                assertThat(p.getUnit())
                        .as("trial %d: returned payment %d must have a unit", i, p.getPaymentId())
                        .isNotNull();
                assertThat(p.getUnit().getUnitNumber().toLowerCase(Locale.ROOT))
                        .as("trial %d: returned payment %d unit number must contain the term "
                                        + "(term=%s)",
                                i, p.getPaymentId(), term)
                        .contains(termLower);
            }

            // Coverage bookkeeping.
            termsSeen.add(term);
            if (!expectedIds.isEmpty()) {
                sawNonEmptySelection = true;
            } else {
                sawEmptyResult = true;
            }
            if (expectedIds.size() < persisted.size()) {
                sawSelectionThatDroppedSomething = true;
            }
            // A match where the term's own casing is not identical to any matched unit number's
            // corresponding substring proves case-insensitivity is actually exercised: the term
            // contains an upper/lower letter that differs from the stored value's casing.
            if (!expectedIds.isEmpty() && !term.equals(termLower)
                    && hasUpperCaseMatchingLowerStored(term, persisted)) {
                sawCaseDifferingMatch = true;
            }
        }

        assertThat(sawNonEmptySelection)
                .as("generator should produce a term that matches at least one payment")
                .isTrue();
        assertThat(sawSelectionThatDroppedSomething)
                .as("generator should produce at least one term that excludes some payments")
                .isTrue();
        assertThat(sawEmptyResult)
                .as("generator should produce at least one term that matches nothing")
                .isTrue();
        assertThat(sawCaseDifferingMatch)
                .as("generator should produce at least one match where letter casing differed "
                        + "between the term and the stored unit number (case-insensitivity)")
                .isTrue();
        assertThat(termsSeen.size())
                .as("generator should exercise more than one distinct search term across trials")
                .isGreaterThan(1);
    }

    /**
     * Returns true when the (mixed-case) term contains at least one letter and some persisted unit
     * number contains the term case-insensitively while differing from it in raw case. This is a
     * conservative signal that the case-insensitive path (not a plain equals) produced the match.
     */
    private boolean hasUpperCaseMatchingLowerStored(String term, List<MaintenancePayment> persisted) {
        boolean hasLetter = term.chars().anyMatch(Character::isLetter);
        if (!hasLetter) {
            return false;
        }
        String termLower = term.toLowerCase(Locale.ROOT);
        for (MaintenancePayment p : persisted) {
            String unitNumber = p.getUnit() == null ? null : p.getUnit().getUnitNumber();
            if (unitNumber == null) {
                continue;
            }
            String stored = unitNumber.toLowerCase(Locale.ROOT);
            if (stored.contains(termLower) && !unitNumber.contains(term)) {
                // Case-insensitive match succeeded even though a case-sensitive contains would not.
                return true;
            }
        }
        return false;
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
     * Persists the generated payment blueprints across a small pool of mixed-case real unit numbers
     * (one bill per unit) so that a unit-search term can select the payments of matching units out
     * of several. Returns the persisted copies carrying the generated ids.
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

    private record Scenario(List<PaymentBlueprint> payments, String term) {
    }

    private record PaymentBlueprint(String unitNumber, BigDecimal amount, LocalDate paymentDate,
                                    PaymentMode paymentMode, PaymentStatus status) {
    }

    /**
     * Generates a payment set spread across the mixed-case unit-number pool plus a search term of
     * length 1-50. Terms are drawn so they are frequently substrings of a stored unit number (in
     * varied casing to exercise case-insensitivity), occasionally a full re-cased unit number, and
     * occasionally arbitrary text that usually matches nothing. Drawing from a small pool means
     * many trials span several units, so a term frequently matches some but not all payments.
     */
    private Arbitrary<Scenario> scenarios() {
        Arbitrary<List<PaymentBlueprint>> payments =
                paymentBlueprint().list().ofMinSize(1).ofMaxSize(12);
        return Combinators.combine(payments, term()).as(Scenario::new);
    }

    private Arbitrary<PaymentBlueprint> paymentBlueprint() {
        Arbitrary<String> unitNumbers = Arbitraries.of(UNIT_NUMBERS);
        Arbitrary<BigDecimal> amounts = Arbitraries.longs().between(0L, 500_000L)
                .map(cents -> BigDecimal.valueOf(cents, 2));
        Arbitrary<PaymentMode> modes = Arbitraries.of(PaymentMode.class);
        Arbitrary<PaymentStatus> statuses = Arbitraries.of(PaymentStatus.class);
        return Combinators.combine(unitNumbers, amounts, paymentDate(), modes, statuses)
                .as(PaymentBlueprint::new);
    }

    /**
     * A search term of length 1-50. Most terms are re-cased substrings of a pool unit number so
     * they meaningfully match (and exercise case-insensitivity); a smaller fraction are arbitrary
     * short strings that usually match nothing (empty-result case).
     */
    private Arbitrary<String> term() {
        Arbitrary<String> substringTerms = substringOfPoolUnit();
        Arbitrary<String> arbitraryTerms = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1).ofMaxLength(6)
                // Prefix with a character absent from the pool so these usually match nothing.
                .map(s -> "z" + s);
        return Arbitraries.frequencyOf(
                Tuple.of(4, substringTerms),
                Tuple.of(1, arbitraryTerms));
    }

    /**
     * Draws a non-empty substring of one of the pool unit numbers, then randomly re-cases each
     * letter. The result is guaranteed length 1-50 (pool unit numbers are short) and, when matched
     * case-insensitively, will hit the units bearing that substring regardless of stored casing.
     */
    private Arbitrary<String> substringOfPoolUnit() {
        Arbitrary<String> base = Arbitraries.of(UNIT_NUMBERS);
        return base.flatMap(unit -> {
            int len = unit.length();
            Arbitrary<Integer> start = Arbitraries.integers().between(0, len - 1);
            return start.flatMap(s -> {
                Arbitrary<Integer> end = Arbitraries.integers().between(s + 1, len);
                return end.flatMap(e -> {
                    String sub = unit.substring(s, e);
                    // Re-case each letter independently to vary the term's casing vs. the stored value.
                    return recase(sub);
                });
            });
        });
    }

    /** Randomly upper/lower-cases each character of the given string. */
    private Arbitrary<String> recase(String s) {
        Arbitrary<Boolean> upper = Arbitraries.of(Boolean.TRUE, Boolean.FALSE);
        return upper.list().ofSize(s.length()).map(flags -> {
            StringBuilder sb = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                sb.append(flags.get(i) ? Character.toUpperCase(c) : Character.toLowerCase(c));
            }
            return sb.toString();
        });
    }

    /** A fixed-window date; the payment date is irrelevant to this property but must be non-null. */
    private Arbitrary<LocalDate> paymentDate() {
        return Arbitraries.integers().between(0, 30)
                .map(LocalDate.of(2024, 1, 1)::plusDays);
    }
}
