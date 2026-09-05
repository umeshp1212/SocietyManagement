package com.society.module.transaction.controller;

import com.society.enums.OccupancyStatus;
import com.society.enums.OwnerStatus;
import com.society.enums.UnitType;
import com.society.module.auth.entity.Permission;
import com.society.module.auth.entity.Role;
import com.society.module.auth.entity.User;
import com.society.module.auth.repository.PermissionRepository;
import com.society.module.auth.repository.RoleRepository;
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
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc integration tests for the Transaction feature access control and scope
 * (Subtask 9.1).
 *
 * <p>Exercises the full request pipeline ({@code @SpringBootTest} + {@link MockMvc})
 * against a seeded in-memory H2 database, so the security method guard
 * ({@code @PreAuthorize}), the access-scope resolver, the specification query, and
 * the shared {@code ApiResponse}/{@code PagedResponse} envelopes are all validated
 * end-to-end. The controller is mounted under the {@code /api} context path.
 *
 * <p>Cases covered:
 * <ul>
 *   <li>unauthenticated request &rarr; 401 (Req 10.1)</li>
 *   <li>authenticated without {@code TRANSACTION_VIEW}/{@code SUPER_ADMIN} &rarr; 403 (Req 10.5)</li>
 *   <li>member vs admin scope end-to-end against seeded data: a member sees only their
 *       own units' transactions, an administrator sees every transaction (Req 10.2, 10.4)</li>
 *   <li>member out-of-scope detail request &rarr; 403 (Req 2.3, 8.5, 10.3)</li>
 *   <li>non-existent detail id &rarr; 404 (Req 8.6)</li>
 * </ul>
 *
 * <p>Authentication is simulated with Spring Security test's {@code @WithMockUser}
 * (username matches a seeded {@link User} so the scope resolver can map the caller
 * to its linked units; authorities drive the method-security guard and the
 * society-wide classification). Data is seeded through the real repositories so the
 * query runs against a real persistence layer.
 *
 * <p>Validates: Requirements 2.3, 8.5, 8.6, 10.1, 10.3, 10.4, 10.5
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        // Isolated in-memory H2 (MySQL-compatible) instead of the configured MySQL
        // datasource; Hibernate creates the schema from the entity mappings so the full
        // application context and the real query pipeline load without an external DB.
        "spring.datasource.url=jdbc:h2:mem:txn_integration_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        // The production db/data.sql is MySQL-specific; skip SQL script init for this test.
        "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF"
})
class TransactionControllerIntegrationTest {

    private static final String ADMIN_USERNAME = "admin_it";
    private static final String MEMBER_USERNAME = "member_owner_it";
    private static final String NO_ACCESS_USERNAME = "no_access_it";

    /**
     * Controller paths as seen by {@link MockMvc}. The production
     * {@code server.servlet.context-path=/api} is a servlet-container setting that
     * MockMvc does not apply, so requests target the controller mapping directly.
     */
    private static final String LIST_PATH = "/transactions";
    private static final String DETAIL_PATH = "/transactions/{id}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PermissionRepository permissionRepository;
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

    /** Unit the member owns (in scope). */
    private Unit memberUnit;
    /** Unit the member does not own (out of scope). */
    private Unit otherUnit;

    /** Payment ids on the member's own unit (in scope). */
    private Long memberPaymentId;
    /** Payment id on a unit the member does not own (out of scope). */
    private Long otherPaymentId;

    @BeforeEach
    void seed() {
        // Clean slate (each test seeds fresh so ordering/assertions are deterministic).
        paymentRepository.deleteAll();
        billRepository.deleteAll();
        unitOwnerRepository.deleteAll();
        userRepository.deleteAll();
        unitRepository.deleteAll();
        ownerRepository.deleteAll();

        // --- Roles / permission ---
        Permission transactionView = permissionRepository.findByPermissionName("TRANSACTION_VIEW")
                .orElseGet(() -> permissionRepository.save(Permission.builder()
                        .permissionName("TRANSACTION_VIEW")
                        .module("transaction")
                        .description("View maintenance transactions")
                        .build()));

        Role superAdminRole = roleRepository.findByRoleName("SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .roleName("SUPER_ADMIN")
                        .displayName("Super Admin")
                        .description("Full access")
                        .build()));

        Role ownerRole = roleRepository.findByRoleName("OWNER")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .roleName("OWNER")
                        .displayName("Owner")
                        .description("Member owner")
                        .build()));
        if (ownerRole.getPermissions().stream()
                .noneMatch(p -> "TRANSACTION_VIEW".equals(p.getPermissionName()))) {
            ownerRole.getPermissions().add(transactionView);
            ownerRole = roleRepository.save(ownerRole);
        }

        // --- Units ---
        memberUnit = unitRepository.save(unit("A-101"));
        otherUnit = unitRepository.save(unit("B-202"));

        // --- Owner linked to the member's unit ---
        Owner owner = ownerRepository.save(Owner.builder()
                .fullName("Resident One")
                .contactNumber("9000000001")
                .status(OwnerStatus.ACTIVE)
                .build());

        unitOwnerRepository.save(UnitOwner.builder()
                .unit(memberUnit)
                .owner(owner)
                .isPrimary(true)
                .ownershipPercentage(new BigDecimal("100.00"))
                .build());

        // --- Users ---
        // Admin: society-wide via SUPER_ADMIN role (-> ROLE_SUPER_ADMIN authority).
        userRepository.save(User.builder()
                .username(ADMIN_USERNAME)
                .password("x")
                .fullName("Admin IT")
                .isActive(true)
                .roles(new HashSet<>(Set.of(superAdminRole)))
                .build());

        // Member owner: TRANSACTION_VIEW authority via OWNER role; ownerId links to the unit.
        userRepository.save(User.builder()
                .username(MEMBER_USERNAME)
                .password("x")
                .fullName("Member Owner IT")
                .isActive(true)
                .ownerId(owner.getOwnerId())
                .roles(new HashSet<>(Set.of(ownerRole)))
                .build());

        // A user with no transaction-access authority at all.
        Role plainRole = roleRepository.findByRoleName("PLAIN_IT")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .roleName("PLAIN_IT")
                        .displayName("Plain")
                        .description("No transaction access")
                        .build()));
        userRepository.save(User.builder()
                .username(NO_ACCESS_USERNAME)
                .password("x")
                .fullName("No Access IT")
                .isActive(true)
                .roles(new HashSet<>(Set.of(plainRole)))
                .build());

        // --- Bills + payments ---
        MaintenanceBill memberBill = billRepository.save(bill(memberUnit));
        MaintenanceBill otherBill = billRepository.save(bill(otherUnit));

        memberPaymentId = paymentRepository.save(payment(memberUnit, memberBill,
                LocalDate.of(2024, 5, 10), "RCPT-MEM-1")).getPaymentId();
        // A second in-scope payment to confirm the member sees all of their own.
        paymentRepository.save(payment(memberUnit, memberBill,
                LocalDate.of(2024, 5, 11), "RCPT-MEM-2"));

        otherPaymentId = paymentRepository.save(payment(otherUnit, otherBill,
                LocalDate.of(2024, 5, 12), "RCPT-OTH-1")).getPaymentId();
    }

    // ------------------------------------------------------------------
    // 10.1 Unauthenticated -> 401
    // ------------------------------------------------------------------

    @Test
    @WithAnonymousUser
    void listWithoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get(LIST_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    void detailWithoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get(DETAIL_PATH, memberPaymentId))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // 10.5 Authenticated but lacks TRANSACTION_VIEW/SUPER_ADMIN -> 403
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = NO_ACCESS_USERNAME, authorities = {"ROLE_PLAIN_IT"})
    void listWithoutTransactionAuthority_returns403() throws Exception {
        mockMvc.perform(get(LIST_PATH))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = NO_ACCESS_USERNAME, authorities = {"ROLE_PLAIN_IT"})
    void detailWithoutTransactionAuthority_returns403() throws Exception {
        mockMvc.perform(get(DETAIL_PATH, memberPaymentId))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // 10.4 Administrator sees all transactions
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = ADMIN_USERNAME, authorities = {"ROLE_SUPER_ADMIN"})
    void adminList_seesAllTransactionsAcrossUnits() throws Exception {
        mockMvc.perform(get(LIST_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalElements", is(3)))
                .andExpect(jsonPath("$.data.content", hasSize(3)))
                .andExpect(jsonPath("$.data.content[*].unitNumber",
                        containsInAnyOrder("A-101", "A-101", "B-202")));
    }

    // ------------------------------------------------------------------
    // 10.2 Member sees only their own units' transactions
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = MEMBER_USERNAME, authorities = {"TRANSACTION_VIEW"})
    void memberList_seesOnlyOwnUnitsTransactions() throws Exception {
        mockMvc.perform(get(LIST_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalElements", is(2)))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[*].unitNumber",
                        containsInAnyOrder("A-101", "A-101")));
    }

    // ------------------------------------------------------------------
    // Member detail access: in-scope OK, out-of-scope -> 403 (2.3, 8.5, 10.3)
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = MEMBER_USERNAME, authorities = {"TRANSACTION_VIEW"})
    void memberDetail_inScope_returnsTransaction() throws Exception {
        mockMvc.perform(get(DETAIL_PATH, memberPaymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.paymentId", is(memberPaymentId.intValue())))
                .andExpect(jsonPath("$.data.unitNumber", is("A-101")));
    }

    @Test
    @WithMockUser(username = MEMBER_USERNAME, authorities = {"TRANSACTION_VIEW"})
    void memberDetail_outOfScope_returns403() throws Exception {
        mockMvc.perform(get(DETAIL_PATH, otherPaymentId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    // Administrators may read any existing transaction (10.4 / 8.5 society-wide side).
    @Test
    @WithMockUser(username = ADMIN_USERNAME, authorities = {"ROLE_SUPER_ADMIN"})
    void adminDetail_anyUnit_returnsTransaction() throws Exception {
        mockMvc.perform(get(DETAIL_PATH, otherPaymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unitNumber", is("B-202")));
    }

    // ------------------------------------------------------------------
    // 8.6 Non-existent detail id -> 404
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = ADMIN_USERNAME, authorities = {"ROLE_SUPER_ADMIN"})
    void detailForNonExistentId_returns404() throws Exception {
        long missingId = 999_999L;
        mockMvc.perform(get(DETAIL_PATH, missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    // ------------------------------------------------------------------
    // Seeding helpers
    // ------------------------------------------------------------------

    private Unit unit(String unitNumber) {
        return Unit.builder()
                .unitNumber(unitNumber)
                .unitType(UnitType.FLAT)
                .occupancyStatus(OccupancyStatus.SELF_OCCUPIED)
                .monthlyMaintenanceAmount(BigDecimal.ZERO)
                .status("ACTIVE")
                .build();
    }

    private MaintenanceBill bill(Unit unit) {
        return MaintenanceBill.builder()
                .unit(unit)
                .billMonth(5)
                .billYear(2024)
                .billDate(LocalDate.of(2024, 5, 1))
                .dueDate(LocalDate.of(2024, 5, 10))
                .amount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .build();
    }

    private MaintenancePayment payment(Unit unit, MaintenanceBill bill,
                                       LocalDate paymentDate, String receiptNumber) {
        return MaintenancePayment.builder()
                .bill(bill)
                .unit(unit)
                .amount(new BigDecimal("1000.00"))
                .paymentDate(paymentDate)
                .paymentMode(PaymentMode.UPI)
                .status(PaymentStatus.SUCCESS)
                .payerName("Payer")
                .payerType("OWNER")
                .receiptNumber(receiptNumber)
                .transactionId("TXN-" + receiptNumber)
                .build();
    }
}
