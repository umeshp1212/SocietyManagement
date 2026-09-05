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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Model-based property test for {@link TransactionSpecificationBuilder}.
 *
 * <p><b>Property 12: Reference search matches receipt or transaction id
 * case-insensitively.</b> For all payment sets and any reference term of length
 * 1-100, every transaction returned by the built {@link Specification} has a
 * {@code receiptNumber} or {@code transactionId} that contains the term under
 * case-insensitive substring matching (soundness), and every scoped transaction
 * whose receipt number or transaction id contains the term (case-insensitively)
 * is included (completeness). In other words, the reference predicate keeps
 * exactly the scoped payments whose lower-cased {@code receiptNumber} OR lower-cased
 * {@code transactionId} contains the lower-cased term, and imposes no other
 * restriction.</p>
 *
 * <p><b>Validates: Requirements 9.1</b></p>
 *
 * <p><b>Approach.</b> The specification requires a real JPA CriteriaBuilder/persistence
 * layer, so this is a model-based test that executes the real specification against an
 * in-memory H2 database seeded with generated {@link MaintenancePayment} records whose
 * receipt numbers and transaction ids embed mixed-case tokens drawn from a small pool,
 * and compares the returned id set against an independent in-memory reference predicate
 * that applies only the case-insensitive "contains" match on receiptNumber OR transactionId.
 * A society-wide scope is used and no other filters are set so the property isolates
 * reference selection (access-scope containment is covered by Property 2 and AND
 * composition by Property 4).</p>
 *
 * <p>Search terms are drawn so they are frequently substrings of some persisted receipt
 * number or transaction id (in varied letter casing, to exercise case-insensitivity) and
 * occasionally match nothing. Term length is constrained to the specified 1-100 range.</p>
 *
 * <p>Because jqwik's own lifecycle does not share a single Spring context, the property is
 * driven inside one JUnit {@code @Test} method over a stream of generated
 * (payment set, reference term) samples (>= 100 tries), matching the project's jqwik-based
 * property tests while keeping a single injected persistence context.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        // Use an isolated in-memory H2 database (MySQL-compatible) instead of the
        // configured MySQL datasource, and let Hibernate create the schema from the
        // entity mappings so the real Specification runs against a real query engine.
        "spring.datasource.url=jdbc:h2:mem:txn_refsearch_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class TransactionSpecificationReferenceSearchPropertyTest {

    private static final int TRIES = 120;

    /**
     * Mixed-case reference tokens embedded into receipt numbers and transaction ids so the
     * search term (in varied casing) exercises the case-insensitive match against differently
     * cased stored values. Some tokens intentionally share a case-insensitive prefix
     * (e.g. "Txn"/"txn", "Rcpt"/"rcpt") so a re-cased term can select a subset of payments.
     */
    private static final String[] TOKENS =
            {"Rcpt", "rcpt", "Txn", "txn", "Ref9", "REF9", "PayA", "paya"};

    @Autowired
    private MaintenancePaymentRepository paymentRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final TransactionSpecificationBuilder builder = new TransactionSpecificationBuilder();

    @Test
    void referenceSearchMatchesReceiptOrTransactionIdCaseInsensitively() {
        // sampleStream() draws values outside of jqwik's own @Property lifecycle, which lets us
        // run the generation loop inside a single Spring-managed @DataJpaTest context.
        java.util.Iterator<Scenario> scenarios = scenarios().sampleStream().iterator();

        // Guards that the generators actually exercised the property in a meaningful way:
        // a term that matched some but not all payments (a real partition), a term whose casing
        // differed from the stored casing yet still matched (true case-insensitivity), a term
        // matching nothing (empty result), a match via the transactionId branch, a match via the
        // receiptNumber branch, and more than one distinct term across trials.
        boolean sawSelectionThatDroppedSomething = false;  // some scoped payment excluded by term
        boolean sawNonEmptySelection = false;              // at least one payment matched the term
        boolean sawCaseDifferingMatch = false;             // matched despite differing letter case
        boolean sawEmptyResult = false;                    // a term matched nothing
        boolean sawReceiptOnlyMatch = false;               // matched only via receipt number
        boolean sawTransactionOnlyMatch = false;           // matched only via transaction id
        Set<String> termsSeen = new TreeSet<>();

        for (int i = 0; i < TRIES; i++) {
            Scenario scenario = scenarios.next();

            // Fresh data per trial so results are independent.
            clearDatabase();
            List<MaintenancePayment> persisted = persist(scenario.payments());

            String term = scenario.term();

            TransactionFilterRequest filter = new TransactionFilterRequest();
            filter.setReference(term);

            // --- Real production code path under test ---
            Specification<MaintenancePayment> spec =
                    builder.build(AccessScope.societyWideScope(), filter);
            List<MaintenancePayment> returned = paymentRepository.findAll(spec);
            Set<Long> actualIds = returned.stream()
                    .map(MaintenancePayment::getPaymentId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            // --- Independent in-memory reference: exactly the case-insensitive "contains" match
            // over receiptNumber OR transactionId ---
            String termLower = term.toLowerCase(Locale.ROOT);
            Set<Long> expectedIds = persisted.stream()
                    .filter(p -> containsCaseInsensitive(p.getReceiptNumber(), termLower)
                            || containsCaseInsensitive(p.getTransactionId(), termLower))
                    .map(MaintenancePayment::getPaymentId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            // Soundness (every returned payment matches) + completeness (no matching payment
            // omitted) in a single equality comparison.
            assertThat(actualIds)
                    .as("trial %d: spec result must equal the case-insensitive reference-search "
                                    + "reference over receiptNumber OR transactionId (term=%s)",
                            i, term)
                    .isEqualTo(expectedIds);

            // Explicit per-row soundness check: every returned payment's receipt number or
            // transaction id contains the term case-insensitively.
            for (MaintenancePayment p : returned) {
                boolean matches = containsCaseInsensitive(p.getReceiptNumber(), termLower)
                        || containsCaseInsensitive(p.getTransactionId(), termLower);
                assertThat(matches)
                        .as("trial %d: returned payment %d must have a receiptNumber (%s) or "
                                        + "transactionId (%s) containing the term (term=%s)",
                                i, p.getPaymentId(), p.getReceiptNumber(), p.getTransactionId(), term)
                        .isTrue();
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
            for (MaintenancePayment p : persisted) {
                boolean inReceipt = containsCaseInsensitive(p.getReceiptNumber(), termLower);
                boolean inTxn = containsCaseInsensitive(p.getTransactionId(), termLower);
                if (inReceipt && !inTxn) {
                    sawReceiptOnlyMatch = true;
                }
                if (inTxn && !inReceipt) {
                    sawTransactionOnlyMatch = true;
                }
                // A match where the stored value differs from the term in raw case proves the
                // case-insensitive path (not a plain equals/contains) produced the match.
                if (!term.equals(termLower)) {
                    if (differsInCaseButMatches(p.getReceiptNumber(), term, termLower)
                            || differsInCaseButMatches(p.getTransactionId(), term, termLower)) {
                        sawCaseDifferingMatch = true;
                    }
                }
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
        assertThat(sawReceiptOnlyMatch)
                .as("generator should produce at least one match via the receiptNumber branch only")
                .isTrue();
        assertThat(sawTransactionOnlyMatch)
                .as("generator should produce at least one match via the transactionId branch only")
                .isTrue();
        assertThat(sawCaseDifferingMatch)
                .as("generator should produce at least one match where letter casing differed "
                        + "between the term and the stored reference (case-insensitivity)")
                .isTrue();
        assertThat(termsSeen.size())
                .as("generator should exercise more than one distinct search term across trials")
                .isGreaterThan(1);
    }

    private boolean containsCaseInsensitive(String stored, String termLower) {
        return stored != null && stored.toLowerCase(Locale.ROOT).contains(termLower);
    }

    /**
     * Returns true when the stored value matches the term case-insensitively but a raw
     * case-sensitive contains would not, proving the case-insensitive path produced the match.
     */
    private boolean differsInCaseButMatches(String stored, String term, String termLower) {
        if (stored == null) {
            return false;
        }
        boolean caseInsensitiveHit = stored.toLowerCase(Locale.ROOT).contains(termLower);
        boolean caseSensitiveHit = stored.contains(term);
        return caseInsensitiveHit && !caseSensitiveHit;
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
     * Persists the generated payment blueprints under a single unit/bill (the unit is irrelevant
     * to this property). Each payment carries a unique receipt number (the receipt token plus the
     * per-payment index, honouring the {@code uk_payment_receipt_number} unique constraint and the
     * 50-char column limit) and a transaction id built from its own token. Returns the persisted
     * copies carrying the generated ids.
     */
    private List<MaintenancePayment> persist(List<PaymentBlueprint> blueprints) {
        Unit unit = persistUnit("A-101");
        MaintenanceBill bill = persistBill(unit);

        List<MaintenancePayment> persisted = new ArrayList<>();
        int index = 0;
        for (PaymentBlueprint bp : blueprints) {
            // receipt_number is unique (constraint) and <= 50 chars: token + unique index suffix.
            String receiptNumber = bp.receiptToken() + "-" + index;
            // transaction_id has no unique constraint and <= 100 chars: token + index suffix.
            String transactionId = bp.transactionToken() + "-" + index;
            MaintenancePayment payment = MaintenancePayment.builder()
                    .bill(bill)
                    .unit(unit)
                    .amount(bp.amount())
                    .paymentDate(bp.paymentDate())
                    .paymentMode(bp.paymentMode())
                    .status(bp.status())
                    .payerType("OWNER")
                    .payerName("Payer" + index)
                    .receiptNumber(receiptNumber)
                    .transactionId(transactionId)
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

    private record PaymentBlueprint(String receiptToken, String transactionToken, BigDecimal amount,
                                    LocalDate paymentDate, PaymentMode paymentMode,
                                    PaymentStatus status) {
    }

    /**
     * Generates a payment set (each payment carries an independently-drawn receipt token and
     * transaction token from the pool) plus a reference term of length 1-100. Terms are drawn so
     * they are frequently re-cased substrings of a pool token (exercising case-insensitivity and
     * frequently matching some but not all payments), and occasionally arbitrary text that usually
     * matches nothing. Drawing receipt and transaction tokens independently means many trials
     * produce matches through only one of the two branches.
     */
    private Arbitrary<Scenario> scenarios() {
        Arbitrary<List<PaymentBlueprint>> payments =
                paymentBlueprint().list().ofMinSize(1).ofMaxSize(12);
        return Combinators.combine(payments, term()).as(Scenario::new);
    }

    private Arbitrary<PaymentBlueprint> paymentBlueprint() {
        Arbitrary<String> receiptTokens = Arbitraries.of(TOKENS);
        Arbitrary<String> transactionTokens = Arbitraries.of(TOKENS);
        Arbitrary<BigDecimal> amounts = Arbitraries.longs().between(0L, 500_000L)
                .map(cents -> BigDecimal.valueOf(cents, 2));
        Arbitrary<PaymentMode> modes = Arbitraries.of(PaymentMode.class);
        Arbitrary<PaymentStatus> statuses = Arbitraries.of(PaymentStatus.class);
        return Combinators.combine(receiptTokens, transactionTokens, amounts, paymentDate(), modes,
                        statuses)
                .as(PaymentBlueprint::new);
    }

    /**
     * A reference term of length 1-100. Most terms are re-cased substrings of a pool token so they
     * meaningfully match (and exercise case-insensitivity); a smaller fraction are arbitrary short
     * strings that usually match nothing (empty-result case).
     */
    private Arbitrary<String> term() {
        Arbitrary<String> substringTerms = substringOfPoolToken();
        Arbitrary<String> arbitraryTerms = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1).ofMaxLength(6)
                // Prefix with a character absent from the pool tokens so these usually match nothing.
                .map(s -> "q" + s);
        return Arbitraries.frequencyOf(
                Tuple.of(4, substringTerms),
                Tuple.of(1, arbitraryTerms));
    }

    /**
     * Draws a non-empty substring of one of the pool tokens, then randomly re-cases each letter.
     * The result is short (pool tokens are short, well within the 1-100 range) and, when matched
     * case-insensitively, will hit the payments bearing that substring regardless of stored casing.
     */
    private Arbitrary<String> substringOfPoolToken() {
        Arbitrary<String> base = Arbitraries.of(TOKENS);
        return base.flatMap(token -> {
            int len = token.length();
            Arbitrary<Integer> start = Arbitraries.integers().between(0, len - 1);
            return start.flatMap(s -> {
                Arbitrary<Integer> end = Arbitraries.integers().between(s + 1, len);
                return end.flatMap(e -> {
                    String sub = token.substring(s, e);
                    // Re-case each letter independently to vary the term's casing vs. stored values.
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
