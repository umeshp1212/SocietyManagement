package com.society.module.transaction.service;

import com.society.enums.OccupancyStatus;
import com.society.enums.UnitType;
import com.society.exception.ResourceNotFoundException;
import com.society.module.maintenance.entity.MaintenanceBill;
import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentMode;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentStatus;
import com.society.module.maintenance.repository.MaintenancePaymentRepository;
import com.society.module.owner.entity.Unit;
import com.society.module.transaction.dto.TransactionDetailDTO;
import com.society.module.transaction.specification.TransactionSpecificationBuilder;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property test for the access-scope enforcement of the Transaction detail lookup
 * ({@link TransactionService#getTransaction(Long, Authentication)}, Subtask 7.2).
 *
 * <p><b>Property 3: Detail access respects scope.</b>
 * For all members and any transaction, requesting the detail of a transaction whose unit
 * is outside the member's scope is denied and returns no transaction data; requesting an
 * in-scope (or, for an administrator, any existing) transaction returns that transaction's
 * detail.</p>
 *
 * <p><b>Validates: Requirements 2.3, 8.5, 10.3</b></p>
 *
 * <p><b>Approach.</b> The scope decision lives in the real service, which resolves the
 * caller's {@link AccessScope} and, for a member caller, denies the request when the
 * payment's unit is outside that scope. This test drives the real
 * {@link TransactionService#getTransaction} path against an in-memory H2 database seeded
 * with generated {@link MaintenancePayment} records spread across a small pool of units.
 * The only collaborator that is stubbed is {@link AccessScopeResolver} (which would
 * otherwise require seeding users/owners/tenants); it is replaced with an in-test override
 * that returns the scope selected for the trial, so the service's own scope-enforcement
 * branch — not a mocked service — is exercised. The remaining collaborators
 * ({@link MaintenancePaymentRepository}, {@link UnitRepository}, the real
 * {@link TransactionSpecificationBuilder}) are the production beans.</p>
 *
 * <p>For each trial a random persisted transaction is chosen and looked up under three
 * scope shapes:
 * <ul>
 *   <li><b>society-wide</b> (administrator): any existing transaction returns its detail;</li>
 *   <li><b>in-scope member</b> (the transaction's unit is in the member's unit-id set): the
 *       detail is returned and its {@code paymentId} matches the requested id;</li>
 *   <li><b>out-of-scope member</b> (the transaction's unit is excluded from the set): the
 *       lookup throws {@link AccessDeniedException} and yields no {@link TransactionDetailDTO}.</li>
 * </ul>
 * A non-existent id is also probed to confirm it surfaces as {@link ResourceNotFoundException}
 * regardless of scope (the id-not-found path; message-level 404 assertions belong to task 7.8).</p>
 *
 * <p>Because jqwik's own lifecycle does not share a single Spring context, the property is
 * driven inside one JUnit {@code @Test} method over a stream of generated (payment set)
 * samples (>= 100 tries), matching the project's other jqwik-based property tests while
 * keeping a single injected persistence context.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        // Use an isolated in-memory H2 database (MySQL-compatible) instead of the
        // configured MySQL datasource, and let Hibernate create the schema from the
        // entity mappings so the real repository lookup runs against a real query engine.
        "spring.datasource.url=jdbc:h2:mem:txn_detail_scope_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class TransactionDetailAccessScopePropertyTest {

    private static final int TRIES = 120;

    @Autowired
    private MaintenancePaymentRepository paymentRepository;

    @Autowired
    private com.society.module.owner.repository.UnitRepository unitRepository;

    @Autowired
    private TestEntityManager entityManager;

    /**
     * A settable-scope stub of {@link AccessScopeResolver}: the resolver's collaborators are
     * irrelevant here because {@link #resolve(Authentication)} is overridden to return the
     * scope selected for the current trial, letting the real service scope-enforcement branch
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
    void detailAccessRespectsScope() {
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

        // Coverage guards: ensure the generators actually exercised each branch of the property.
        boolean sawAdminSuccess = false;
        boolean sawInScopeMemberSuccess = false;
        boolean sawOutOfScopeMemberDenied = false;
        boolean sawMultiUnit = false;

        for (int i = 0; i < TRIES; i++) {
            List<PaymentBlueprint> blueprints = scenarios.next();

            // Fresh data per trial so results are independent.
            clearDatabase();
            List<MaintenancePayment> persisted = persist(blueprints);

            // Distinct unit ids actually present in this trial's data.
            List<Long> unitIds = persisted.stream()
                    .map(p -> p.getUnit().getUnitId())
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
            if (unitIds.size() >= 2) {
                sawMultiUnit = true;
            }

            // Pick a target transaction deterministically from the generated seed.
            MaintenancePayment target = persisted.get(i % persisted.size());
            Long targetId = target.getPaymentId();
            Long targetUnitId = target.getUnit().getUnitId();

            // --- Administrator (society-wide): any existing transaction returns its detail (Req 10.4/8.x) ---
            scopeResolver.setScope(AccessScope.societyWideScope());
            TransactionDetailDTO adminDetail = service.getTransaction(targetId, auth);
            assertThat(adminDetail)
                    .as("trial %d: administrator must receive detail for existing transaction id=%d", i, targetId)
                    .isNotNull();
            assertThat(adminDetail.getPaymentId())
                    .as("trial %d: administrator detail must be the requested transaction", i)
                    .isEqualTo(targetId);
            sawAdminSuccess = true;

            // --- In-scope member: the target's unit is within the member scope -> detail returned (Req 8.5) ---
            Set<Long> inScope = new LinkedHashSet<>();
            inScope.add(targetUnitId);
            scopeResolver.setScope(AccessScope.memberScoped(inScope));
            TransactionDetailDTO memberDetail = service.getTransaction(targetId, auth);
            assertThat(memberDetail)
                    .as("trial %d: in-scope member must receive detail for transaction id=%d (unit %d)",
                            i, targetId, targetUnitId)
                    .isNotNull();
            assertThat(memberDetail.getPaymentId())
                    .as("trial %d: in-scope member detail must be the requested transaction", i)
                    .isEqualTo(targetId);
            sawInScopeMemberSuccess = true;

            // --- Out-of-scope member: the target's unit is excluded -> denied, no data (Req 2.3/8.5/10.3) ---
            Set<Long> outOfScope = unitIds.stream()
                    .filter(id -> !id.equals(targetUnitId))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            // Also cover the empty-scope member (no linked units at all): still out of scope.
            for (Set<Long> deniedScope : List.of(outOfScope, Set.<Long>of())) {
                scopeResolver.setScope(AccessScope.memberScoped(deniedScope));
                assertThatThrownBy(() -> service.getTransaction(targetId, auth))
                        .as("trial %d: out-of-scope member (scope=%s) must be denied access to transaction id=%d",
                                i, deniedScope, targetId)
                        .isInstanceOf(AccessDeniedException.class);
                if (!deniedScope.contains(targetUnitId)) {
                    sawOutOfScopeMemberDenied = true;
                }
            }

            // --- Non-existent id: not-found regardless of scope (id path; message assertions -> task 7.8) ---
            long missingId = maxId(persisted) + 1000L;
            scopeResolver.setScope(AccessScope.societyWideScope());
            assertThatThrownBy(() -> service.getTransaction(missingId, auth))
                    .as("trial %d: a non-existent transaction id=%d must surface as not-found", i, missingId)
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        assertThat(sawAdminSuccess)
                .as("generator should exercise the administrator success path")
                .isTrue();
        assertThat(sawInScopeMemberSuccess)
                .as("generator should exercise the in-scope member success path")
                .isTrue();
        assertThat(sawOutOfScopeMemberDenied)
                .as("generator should exercise the out-of-scope member denial path")
                .isTrue();
        assertThat(sawMultiUnit)
                .as("generator should produce at least one multi-unit data set so out-of-scope denial is non-trivial")
                .isTrue();
    }

    private static long maxId(List<MaintenancePayment> payments) {
        return payments.stream().mapToLong(MaintenancePayment::getPaymentId).max().orElse(0L);
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
     * Generates a non-empty payment set spread across a small pool of units so that some target
     * transactions land on a unit that a member scope can plausibly include or exclude.
     */
    private Arbitrary<List<PaymentBlueprint>> paymentSets() {
        return paymentBlueprint().list().ofMinSize(1).ofMaxSize(12);
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
