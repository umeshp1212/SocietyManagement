package com.society.module.transaction.controller;

import com.society.enums.OccupancyStatus;
import com.society.enums.OwnerStatus;
import com.society.enums.UnitType;
import com.society.module.auth.entity.User;
import com.society.module.auth.repository.UserRepository;
import com.society.module.maintenance.entity.MaintenanceBill;
import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentMode;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentStatus;
import com.society.module.maintenance.repository.MaintenanceBillRepository;
import com.society.module.maintenance.repository.MaintenancePaymentRepository;
import com.society.module.owner.entity.Owner;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.entity.UnitOwner;
import com.society.module.owner.repository.OwnerRepository;
import com.society.module.owner.repository.UnitOwnerRepository;
import com.society.module.owner.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Performance smoke integration tests for the Transaction Page endpoints
 * (Subtask 9.2).
 *
 * <p>These tests assert that the read paths stay well within their stated
 * latency budgets on a representative dataset, driving the same
 * controller -> service -> specification -> repository stack the application
 * uses at runtime:
 * <ul>
 *   <li>member list request -> within 3 seconds (Req 2.2)</li>
 *   <li>admin unit filter request -> within 3 seconds (Req 6.1)</li>
 *   <li>detail request -> within 3 seconds (Req 8.2)</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 2.2, 6.1, 8.2</b>
 *
 * <p><b>Setup.</b> The full application context is booted with method security
 * active, but the datasource is redirected to an isolated in-memory H2 database
 * (MySQL-compatible) and the MySQL-specific {@code db/data.sql} init script is
 * skipped, mirroring the module's {@code @DataJpaTest} property tests. A
 * representative dataset of {@link MaintenancePayment} rows is seeded across
 * several units, with one unit linked to a member user via an {@link Owner} /
 * {@link UnitOwner} chain so the member scope resolves to real, non-empty data.
 *
 * <p><b>Note on flakiness.</b> Performance assertions can be sensitive to CI
 * load. The dataset is kept modest and the budget is the full 3-second
 * requirement (not a tighter internal target), so the assertion only trips on a
 * genuine regression rather than incidental jitter. Each measurement excludes
 * the first warm-up request to avoid JIT/first-query skew.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // Isolated in-memory H2 (MySQL mode) instead of the configured MySQL datasource;
        // Hibernate creates the schema from the entity mappings.
        "spring.datasource.url=jdbc:h2:mem:txn_perf_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        // The production db/data.sql is MySQL-specific; skip SQL script init for this test.
        "spring.sql.init.mode=never",
        // Keep test output readable.
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.com.society=WARN"
})
@Transactional
class TransactionPerformanceSmokeIntegrationTest {

    /** Latency budget shared by all three requirements (Req 2.2, 6.1, 8.2). */
    private static final long BUDGET_MILLIS = 3_000L;

    /** A representative number of payments per unit across several units. */
    private static final int UNITS = 6;
    private static final int PAYMENTS_PER_UNIT = 50; // 300 rows total

    private static final String MEMBER_USERNAME = "perf_member";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OwnerRepository ownerRepository;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private UnitOwnerRepository unitOwnerRepository;

    @Autowired
    private MaintenanceBillRepository billRepository;

    @Autowired
    private MaintenancePaymentRepository paymentRepository;

    /** The member's unit (linked via owner) and a representative seeded payment id. */
    private Long memberUnitId;
    private Long sampleUnitId;
    private Long samplePaymentId;

    @BeforeEach
    void seedRepresentativeDataset() {
        List<Unit> units = new ArrayList<>();
        for (int u = 0; u < UNITS; u++) {
            Unit unit = unitRepository.save(Unit.builder()
                    .unitNumber("P-" + (100 + u))
                    .unitType(UnitType.FLAT)
                    .occupancyStatus(OccupancyStatus.SELF_OCCUPIED)
                    .monthlyMaintenanceAmount(BigDecimal.ZERO)
                    .status("ACTIVE")
                    .build());
            units.add(unit);
        }

        // Link the first unit to a member user through an owner so the member
        // access scope resolves to a real, non-empty set of units.
        Unit memberUnit = units.get(0);
        memberUnitId = memberUnit.getUnitId();
        sampleUnitId = units.get(1).getUnitId();

        Owner owner = ownerRepository.save(Owner.builder()
                .fullName("Perf Member Owner")
                .status(OwnerStatus.ACTIVE)
                .build());
        unitOwnerRepository.save(UnitOwner.builder()
                .unit(memberUnit)
                .owner(owner)
                .isPrimary(true)
                .build());
        userRepository.save(User.builder()
                .username(MEMBER_USERNAME)
                .password("n/a")
                .fullName("Perf Member")
                .isActive(true)
                .ownerId(owner.getOwnerId())
                .build());

        PaymentMode[] modes = PaymentMode.values();
        PaymentStatus[] statuses = PaymentStatus.values();
        int index = 0;
        for (Unit unit : units) {
            MaintenanceBill bill = billRepository.save(MaintenanceBill.builder()
                    .unit(unit)
                    .billMonth(1)
                    .billYear(2024)
                    .billDate(LocalDate.of(2024, 1, 1))
                    .dueDate(LocalDate.of(2024, 1, 10))
                    .amount(BigDecimal.ZERO)
                    .totalAmount(BigDecimal.ZERO)
                    .build());

            for (int p = 0; p < PAYMENTS_PER_UNIT; p++) {
                MaintenancePayment payment = paymentRepository.save(MaintenancePayment.builder()
                        .bill(bill)
                        .unit(unit)
                        .amount(BigDecimal.valueOf(1000 + index))
                        .paymentDate(LocalDate.of(2024, 1, 1).plusDays(p % 28))
                        .paymentMode(modes[index % modes.length])
                        .status(statuses[index % statuses.length])
                        .payerType(index % 2 == 0 ? "OWNER" : "TENANT")
                        .payerName("Payer " + index)
                        .transactionId("TXN-" + index)
                        .receiptNumber("RCPT-" + index)
                        .build());
                if (samplePaymentId == null) {
                    samplePaymentId = payment.getPaymentId();
                }
                index++;
            }
        }
        paymentRepository.flush();
    }

    @Test
    @WithMockUser(username = MEMBER_USERNAME, authorities = {"TRANSACTION_VIEW"})
    void memberListRequestCompletesWithinBudget() throws Exception {
        // Warm up (JIT / first-query costs) without asserting the budget.
        mockMvc.perform(get("/transactions").param("page", "0"))
                .andExpect(status().isOk());

        long elapsed = timed(() -> mockMvc.perform(get("/transactions").param("page", "0"))
                .andExpect(status().isOk()));

        assertThat(elapsed)
                .as("member list request must return within %d ms (Req 2.2) but took %d ms",
                        BUDGET_MILLIS, elapsed)
                .isLessThan(BUDGET_MILLIS);
    }

    @Test
    @WithMockUser(username = "perf_admin", roles = {"SUPER_ADMIN"})
    void adminUnitFilterRequestCompletesWithinBudget() throws Exception {
        mockMvc.perform(get("/transactions")
                        .param("unitId", String.valueOf(sampleUnitId))
                        .param("page", "0"))
                .andExpect(status().isOk());

        long elapsed = timed(() -> mockMvc.perform(get("/transactions")
                        .param("unitId", String.valueOf(sampleUnitId))
                        .param("page", "0"))
                .andExpect(status().isOk()));

        assertThat(elapsed)
                .as("admin unit-filter request must return within %d ms (Req 6.1) but took %d ms",
                        BUDGET_MILLIS, elapsed)
                .isLessThan(BUDGET_MILLIS);
    }

    @Test
    @WithMockUser(username = "perf_admin", roles = {"SUPER_ADMIN"})
    void detailRequestCompletesWithinBudget() throws Exception {
        mockMvc.perform(get("/transactions/{paymentId}", samplePaymentId))
                .andExpect(status().isOk());

        long elapsed = timed(() -> mockMvc.perform(
                        get("/transactions/{paymentId}", samplePaymentId))
                .andExpect(status().isOk()));

        assertThat(elapsed)
                .as("detail request must return within %d ms (Req 8.2) but took %d ms",
                        BUDGET_MILLIS, elapsed)
                .isLessThan(BUDGET_MILLIS);
    }

    /** Runs the supplied request action and returns its wall-clock duration in milliseconds. */
    private long timed(RequestAction action) throws Exception {
        long start = System.nanoTime();
        action.run();
        return (System.nanoTime() - start) / 1_000_000L;
    }

    @FunctionalInterface
    private interface RequestAction {
        ResultActions run() throws Exception;
    }
}
