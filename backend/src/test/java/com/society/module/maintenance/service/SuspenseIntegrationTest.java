package com.society.module.maintenance.service;

import com.society.enums.OccupancyStatus;
import com.society.enums.PaymentMode;
import com.society.enums.UnitType;
import com.society.module.maintenance.dto.SuspenseEntryDTO;
import com.society.module.maintenance.entity.SuspenseAuditTrail;
import com.society.module.maintenance.entity.SuspenseEntry.SuspenseStatus;
import com.society.module.maintenance.repository.MaintenancePaymentRepository;
import com.society.module.maintenance.repository.OpeningBalanceRepository;
import com.society.module.maintenance.repository.SuspenseAuditTrailRepository;
import com.society.module.maintenance.repository.SuspenseEntryRepository;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the suspense (unidentified-money) flow: create, assign,
 * reverse, reassign, and assign-against-opening-balance.
 *
 * <p>A key invariant asserted here is that the suspense workflow NEVER creates a
 * {@code maintenance_payments} row and therefore never allocates a receipt number —
 * this was the source of confusion while chasing the duplicate-receipt bug, which
 * actually lived in the offline-payment path, not the suspense path.
 *
 * <p>Runs the full Spring context against an isolated in-memory H2 (MySQL mode).
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:suspense_it;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF"
})
class SuspenseIntegrationTest {

    private static final String ACTOR = "admin_it";

    @Autowired
    private SuspenseService suspenseService;
    @Autowired
    private OpeningBalanceService openingBalanceService;
    @Autowired
    private SuspenseEntryRepository suspenseEntryRepository;
    @Autowired
    private SuspenseAuditTrailRepository auditTrailRepository;
    @Autowired
    private UnitRepository unitRepository;
    @Autowired
    private MaintenancePaymentRepository paymentRepository;
    @Autowired
    private OpeningBalanceRepository openingBalanceRepository;

    private Unit unitA;
    private Unit unitB;

    @BeforeEach
    void cleanSlate() {
        auditTrailRepository.deleteAll();
        suspenseEntryRepository.deleteAll();
        paymentRepository.deleteAll();
        openingBalanceRepository.deleteAll();
        unitRepository.deleteAll();

        unitA = unitRepository.save(unit("A-101"));
        unitB = unitRepository.save(unit("B-202"));
    }

    // ------------------------------------------------------------------
    // Scenario 7: create + assign
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Assigning suspense to a unit sets ASSIGNED and creates NO payment")
    void assign_setsAssignedAndCreatesNoPayment() {
        SuspenseEntryDTO created = suspenseService.createSuspenseEntry(
                new BigDecimal("3000.00"), LocalDate.now(), PaymentMode.BANK_TRANSFER.name(),
                "REF-1", "Unidentified transfer", ACTOR);
        assertThat(created.getStatus()).isEqualTo(SuspenseStatus.UNASSIGNED.name());

        SuspenseEntryDTO assigned = suspenseService.assignToUnit(
                created.getSuspenseId(), unitA.getUnitId(), "Belongs to A-101", false, ACTOR);

        assertThat(assigned.getStatus()).isEqualTo(SuspenseStatus.ASSIGNED.name());
        assertThat(assigned.getAssignedToUnitId()).isEqualTo(unitA.getUnitId());

        // The invariant: suspense assignment does not touch maintenance_payments.
        assertThat(paymentRepository.count()).isZero();

        List<SuspenseAuditTrail> audits =
                auditTrailRepository.findBySuspenseEntry_SuspenseIdOrderByPerformedOnDesc(created.getSuspenseId());
        assertThat(audits).extracting(a -> a.getAction().name())
                .contains("CREATED", "ASSIGNED");
    }

    // ------------------------------------------------------------------
    // Scenario 8: reverse an assignment
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Reversing an assignment sets REVERSED and unlinks the unit")
    void reverse_setsReversedAndUnlinks() {
        SuspenseEntryDTO created = suspenseService.createSuspenseEntry(
                new BigDecimal("3000.00"), LocalDate.now(), PaymentMode.BANK_TRANSFER.name(),
                "REF-2", "Unidentified transfer", ACTOR);
        suspenseService.assignToUnit(created.getSuspenseId(), unitA.getUnitId(), "Assign", false, ACTOR);

        SuspenseEntryDTO reversed = suspenseService.reverseAssignment(
                created.getSuspenseId(), "Assigned to wrong unit", ACTOR);

        assertThat(reversed.getStatus()).isEqualTo(SuspenseStatus.REVERSED.name());
        assertThat(reversed.getAssignedToUnitId()).isNull();
        assertThat(paymentRepository.count()).isZero();

        List<SuspenseAuditTrail> audits =
                auditTrailRepository.findBySuspenseEntry_SuspenseIdOrderByPerformedOnDesc(created.getSuspenseId());
        assertThat(audits).extracting(a -> a.getAction().name()).contains("REVERSED");
    }

    // ------------------------------------------------------------------
    // Scenario 9: reassign to a different unit
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Reassigning moves the entry to a new unit and ends ASSIGNED")
    void reassign_movesToNewUnit() {
        SuspenseEntryDTO created = suspenseService.createSuspenseEntry(
                new BigDecimal("3000.00"), LocalDate.now(), PaymentMode.BANK_TRANSFER.name(),
                "REF-3", "Unidentified transfer", ACTOR);
        suspenseService.assignToUnit(created.getSuspenseId(), unitA.getUnitId(), "Assign to A", false, ACTOR);

        SuspenseEntryDTO reassigned = suspenseService.reassign(
                created.getSuspenseId(), unitB.getUnitId(), "Actually B-202", "Move it", false, ACTOR);

        assertThat(reassigned.getStatus()).isEqualTo(SuspenseStatus.ASSIGNED.name());
        assertThat(reassigned.getAssignedToUnitId()).isEqualTo(unitB.getUnitId());
        assertThat(paymentRepository.count()).isZero();
    }

    // ------------------------------------------------------------------
    // Scenario 10: assign against opening balance (and reverse restores it)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Assigning with applyToOpeningBalance reduces the unit's opening balance; reversal restores it")
    void assignAgainstOpeningBalance_reducesThenRestores() {
        openingBalanceService.createOrUpdateOpeningBalance(
                unitA.getUnitId(), new BigDecimal("10000.00"), LocalDate.now(), "Legacy arrears", ACTOR);

        SuspenseEntryDTO created = suspenseService.createSuspenseEntry(
                new BigDecimal("4000.00"), LocalDate.now(), PaymentMode.BANK_TRANSFER.name(),
                "REF-4", "Unidentified transfer", ACTOR);

        suspenseService.assignToUnit(created.getSuspenseId(), unitA.getUnitId(), "Apply to arrears", true, ACTOR);

        // Read scalar fields straight off the entity to avoid the lazy unit proxy (no OSIV here).
        var afterAssign = openingBalanceRepository.findByUnit_UnitId(unitA.getUnitId()).orElseThrow();
        assertThat(afterAssign.getPaidAmount()).isEqualByComparingTo("4000.00");
        assertThat(afterAssign.getBalanceAmount()).isEqualByComparingTo("6000.00");

        suspenseService.reverseAssignment(created.getSuspenseId(), "Undo", ACTOR);

        var afterReverse = openingBalanceRepository.findByUnit_UnitId(unitA.getUnitId()).orElseThrow();
        assertThat(afterReverse.getPaidAmount()).isEqualByComparingTo("0.00");
        assertThat(afterReverse.getBalanceAmount()).isEqualByComparingTo("10000.00");
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    private Unit unit(String unitNumber) {
        return Unit.builder()
                .unitNumber(unitNumber)
                .unitType(UnitType.FLAT)
                .occupancyStatus(OccupancyStatus.SELF_OCCUPIED)
                .monthlyMaintenanceAmount(new BigDecimal("5000.00"))
                .status("ACTIVE")
                .build();
    }
}
