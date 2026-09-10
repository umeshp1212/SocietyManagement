package com.society.module.member.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.society.enums.OccupancyStatus;
import com.society.enums.OwnerStatus;
import com.society.enums.UnitType;
import com.society.module.auth.security.JwtUtil;
import com.society.module.maintenance.entity.MaintenanceBill;
import com.society.module.maintenance.entity.MaintenanceBill.BillStatus;
import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.maintenance.entity.SuspenseEntry;
import com.society.module.maintenance.entity.SuspenseEntry.SuspenseStatus;
import com.society.module.maintenance.repository.MaintenanceBillRepository;
import com.society.module.maintenance.repository.MaintenanceLedgerRepository;
import com.society.module.maintenance.repository.MaintenancePaymentRepository;
import com.society.module.maintenance.repository.SuspenseEntryRepository;
import com.society.module.owner.entity.Owner;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.entity.UnitOwner;
import com.society.module.owner.repository.OwnerRepository;
import com.society.module.owner.repository.UnitOwnerRepository;
import com.society.module.owner.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end MockMvc integration tests for the MEMBER PORTAL maintenance-payment flow
 * (POST {@code /member/payments/verify}).
 *
 * <p>These run the full request pipeline — JWT auth filter, controller, {@code PaymentGatewayRouter},
 * the gateway service, {@code ReceiptNumberService}, {@code SuspenseService}, ledger, and the real
 * repositories — against an isolated in-memory H2 (MySQL mode).
 *
 * <p>Gateway isolation: we drive the CASHFREE path with {@code app.cashfree.app-id} left blank and
 * the {@code prod} profile inactive. In that state {@code CashfreeService.getPaymentStatus} returns a
 * mock {@code order_status=PAID} with NO outbound HTTP call, so the real allocation/receipt/suspense
 * logic executes offline. (The Razorpay path would require a valid cryptographic signature, so it is
 * not suitable for an offline test without mocking the signature check.)
 *
 * <p>Auth: {@code MemberPaymentController} reads the owner id from the JWT {@code userId} claim
 * (not the Spring principal), so {@code @WithMockUser} is insufficient. We mint a real token via the
 * autowired {@link JwtUtil} using a {@code member_} username (the auth filter authenticates such
 * tokens straight from their claims, no User row needed) and set {@code userId} = the seeded owner id.
 *
 * <p>Scenarios: successful payment + receipt, bill fully cleared, partial payment, overpayment parked
 * in suspense, duplicate-order guard, and cross-unit ownership rejection.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:member_payment_it;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF",
        // Force Cashfree mock mode (blank app-id + non-prod) so no gateway HTTP call is made.
        "app.cashfree.app-id=",
        "app.cashfree.secret-key="
})
class MemberPaymentIntegrationTest {

    private static final String VERIFY_PATH = "/member/payments/verify";
    private static final String PERIOD = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtUtil jwtUtil;

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
    @Autowired
    private SuspenseEntryRepository suspenseEntryRepository;
    @Autowired
    private MaintenanceLedgerRepository ledgerRepository;

    private Owner owner;
    private Unit unit;
    /** A different unit the owner does NOT own (for the ownership-rejection test). */
    private Unit foreignUnit;

    @BeforeEach
    void seed() {
        ledgerRepository.deleteAll();
        paymentRepository.deleteAll();
        suspenseEntryRepository.deleteAll();
        billRepository.deleteAll();
        unitOwnerRepository.deleteAll();
        unitRepository.deleteAll();
        ownerRepository.deleteAll();

        owner = ownerRepository.save(Owner.builder()
                .fullName("Umesh Patil")
                .contactNumber("9000000001")
                .status(OwnerStatus.ACTIVE)
                .build());

        unit = unitRepository.save(unit("A-101"));
        foreignUnit = unitRepository.save(unit("B-202"));

        // Link owner <-> unit so existsByUnit_UnitIdAndOwner_OwnerId passes for `unit` only.
        unitOwnerRepository.save(UnitOwner.builder()
                .unit(unit)
                .owner(owner)
                .isPrimary(true)
                .ownershipPercentage(new BigDecimal("100.00"))
                .build());
    }

    // ------------------------------------------------------------------
    // Successful payment that fully clears the bill
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Member Cashfree payment records a SUCCESS payment, allocates a receipt, clears the bill")
    void verify_fullPayment_recordsPaymentAndClearsBill() throws Exception {
        MaintenanceBill bill = persistBill(5, new BigDecimal("5000.00"));

        mockMvc.perform(post(VERIFY_PATH)
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cashfreeBody("ORDER-FULL", new BigDecimal("5000.00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("SUCCESS")))
                .andExpect(jsonPath("$.data.receiptNumber", matchesPattern("RCP-" + PERIOD + "-\\d{5}")))
                .andExpect(jsonPath("$.data.cashfreeOrderId", is("ORDER-FULL")));

        assertThat(paymentRepository.count()).isEqualTo(1);
        MaintenancePayment payment = paymentRepository.findByCashfreeOrderId("ORDER-FULL").orElseThrow();
        assertThat(payment.getPaymentMode()).isEqualTo(MaintenancePayment.PaymentMode.CASHFREE_LINK);
        assertThat(payment.getStatus()).isEqualTo(MaintenancePayment.PaymentStatus.SUCCESS);
        assertThat(payment.getPayerType()).isEqualTo("OWNER");
        // Receipt number format is RCP-YYYYMM-NNNNN. The exact counter value depends on test
        // ordering because receipt_sequences is committed independently and shared across the
        // class's H2 instance, so we assert the format, not a fixed sequence value.
        assertThat(payment.getReceiptNumber()).matches("RCP-" + PERIOD + "-\\d{5}");

        MaintenanceBill reloaded = billRepository.findById(bill.getBillId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BillStatus.PAID);
        assertThat(reloaded.getBalanceAmount()).isEqualByComparingTo("0.00");
    }

    // ------------------------------------------------------------------
    // Partial payment
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A partial member payment leaves the bill PARTIALLY_PAID with no suspense entry")
    void verify_partialPayment_leavesBalance() throws Exception {
        MaintenanceBill bill = persistBill(5, new BigDecimal("5000.00"));

        mockMvc.perform(post(VERIFY_PATH)
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cashfreeBody("ORDER-PARTIAL", new BigDecimal("2000.00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("SUCCESS")));

        MaintenanceBill reloaded = billRepository.findById(bill.getBillId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BillStatus.PARTIALLY_PAID);
        assertThat(reloaded.getBalanceAmount()).isEqualByComparingTo("3000.00");
        assertThat(suspenseEntryRepository.count()).isZero();
    }

    // ------------------------------------------------------------------
    // Overpayment -> leftover parked in suspense
    // ------------------------------------------------------------------

    @Test
    @DisplayName("An overpayment clears the bill and parks the surplus in an UNASSIGNED suspense entry")
    void verify_overpayment_parksSurplusInSuspense() throws Exception {
        MaintenanceBill bill = persistBill(5, new BigDecimal("5000.00"));

        mockMvc.perform(post(VERIFY_PATH)
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cashfreeBody("ORDER-OVER", new BigDecimal("8000.00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("SUCCESS")));

        MaintenanceBill reloaded = billRepository.findById(bill.getBillId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BillStatus.PAID);
        assertThat(reloaded.getBalanceAmount()).isEqualByComparingTo("0.00");

        List<SuspenseEntry> suspense = suspenseEntryRepository.findByStatusOrderByReceivedDateDesc(
                SuspenseStatus.UNASSIGNED);
        assertThat(suspense).hasSize(1);
        assertThat(suspense.get(0).getAmount()).isEqualByComparingTo("3000.00");
        assertThat(suspense.get(0).getReferenceNumber()).isEqualTo("ORDER-OVER");
    }

    // ------------------------------------------------------------------
    // Duplicate-order guard
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Re-verifying the same Cashfree order is rejected as already recorded")
    void verify_duplicateOrder_isRejected() throws Exception {
        persistBill(5, new BigDecimal("5000.00"));
        String body = cashfreeBody("ORDER-DUP", new BigDecimal("2000.00"));

        // First call succeeds.
        mockMvc.perform(post(VERIFY_PATH)
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // Second call with the same order id is rejected (BusinessException -> HTTP 400).
        mockMvc.perform(post(VERIFY_PATH)
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("already been recorded")));

        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Ownership rejection: paying for a unit the owner does not own
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Paying for a unit the member does not own is rejected")
    void verify_foreignUnit_isRejected() throws Exception {
        persistBillForUnit(foreignUnit, 5, new BigDecimal("5000.00"));

        mockMvc.perform(post(VERIFY_PATH)
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cashfreeBodyForUnit(foreignUnit, "ORDER-FOREIGN", new BigDecimal("5000.00"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("don't have access")));

        assertThat(paymentRepository.count()).isZero();
    }

    // ------------------------------------------------------------------
    // Unauthenticated -> 401
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Verify without a token is unauthorized")
    void verify_withoutToken_returns401() throws Exception {
        persistBill(5, new BigDecimal("5000.00"));

        mockMvc.perform(post(VERIFY_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cashfreeBody("ORDER-NOAUTH", new BigDecimal("5000.00"))))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Mint a real member JWT whose userId claim carries the seeded owner id. */
    private String memberToken() {
        return jwtUtil.generateToken("member_" + owner.getContactNumber(), owner.getOwnerId(),
                List.of("MEMBER"), List.of());
    }

    private String cashfreeBody(String orderId, BigDecimal amount) throws Exception {
        return cashfreeBodyForUnit(unit, orderId, amount);
    }

    private String cashfreeBodyForUnit(Unit u, String orderId, BigDecimal amount) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("gateway", "CASHFREE");
        body.put("unitId", u.getUnitId());
        body.put("amount", amount);
        body.put("cashfreeOrderId", orderId);
        return objectMapper.writeValueAsString(body);
    }

    private MaintenanceBill persistBill(int month, BigDecimal total) {
        return persistBillForUnit(unit, month, total);
    }

    private MaintenanceBill persistBillForUnit(Unit u, int month, BigDecimal total) {
        return billRepository.save(MaintenanceBill.builder()
                .unit(u)
                .billMonth(month)
                .billYear(LocalDate.now().getYear())
                .billDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(10))
                .amount(total)
                .totalAmount(total)
                .balanceAmount(total)
                .status(BillStatus.UNPAID)
                .build());
    }

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
