package com.society.module.maintenance.service;

import com.society.enums.OccupancyStatus;
import com.society.enums.UnitType;
import com.society.module.maintenance.dto.PaymentDTO;
import com.society.module.maintenance.dto.RecordOfflinePaymentRequest;
import com.society.module.maintenance.entity.MaintenanceBill;
import com.society.module.maintenance.entity.MaintenanceBill.BillStatus;
import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentStatus;
import com.society.module.maintenance.entity.ReceiptSequence;
import com.society.module.maintenance.entity.UnitAdvanceCredit;
import com.society.module.maintenance.repository.MaintenanceBillRepository;
import com.society.module.maintenance.repository.MaintenanceLedgerRepository;
import com.society.module.maintenance.repository.MaintenancePaymentRepository;
import com.society.module.maintenance.repository.OpeningBalanceRepository;
import com.society.module.maintenance.repository.ReceiptSequenceRepository;
import com.society.module.maintenance.repository.UnitAdvanceCreditRepository;
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
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the offline maintenance-payment flow, focused on the
 * receipt-number generator and the overpayment (advance-credit) path.
 *
 * <p>These cover the regressions we chased down:
 * <ul>
 *   <li>a single offline payment must produce exactly ONE {@code maintenance_payments}
 *       row and never trip the {@code uk_payment_receipt_number} unique constraint,
 *       even when it is an overpayment that also writes a {@code unit_advance_credits}
 *       row (the old code re-saved the payment and issued a duplicate INSERT);</li>
 *   <li>receipt numbers are strictly sequential ({@code RCP-YYYYMM-00001},
 *       {@code -00002}, ...) and the {@code receipt_sequences} counter tracks them;</li>
 *   <li>reversal restores the bill and voids any linked advance credit;</li>
 *   <li>a reversal followed by a fresh payment allocates the next number without a clash.</li>
 * </ul>
 *
 * <p>Runs the full Spring context against an isolated in-memory H2 (MySQL mode) so the
 * real service + repository + transaction pipeline executes exactly as in production.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:maint_payment_it;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF"
})
class MaintenancePaymentIntegrationTest {

    /** Receipt period key for "now", e.g. 202609. Tests assert against the current month. */
    private static final String PERIOD = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));

    @Autowired
    private MaintenanceBillService billService;
    @Autowired
    private MaintenanceBillRepository billRepository;
    @Autowired
    private MaintenancePaymentRepository paymentRepository;
    @Autowired
    private UnitRepository unitRepository;
    @Autowired
    private UnitAdvanceCreditRepository advanceCreditRepository;
    @Autowired
    private ReceiptSequenceRepository sequenceRepository;
    @Autowired
    private MaintenanceLedgerRepository ledgerRepository;
    @Autowired
    private OpeningBalanceRepository openingBalanceRepository;

    private Unit unit;

    @BeforeEach
    void cleanSlate() {
        // Order matters for FK integrity: children before parents.
        ledgerRepository.deleteAll();
        paymentRepository.deleteAll();
        advanceCreditRepository.deleteAll();
        sequenceRepository.deleteAll();
        billRepository.deleteAll();
        openingBalanceRepository.deleteAll();
        unitRepository.deleteAll();

        unit = unitRepository.save(Unit.builder()
                .unitNumber("A-101")
                .unitType(UnitType.FLAT)
                .occupancyStatus(OccupancyStatus.SELF_OCCUPIED)
                .monthlyMaintenanceAmount(new BigDecimal("5000.00"))
                .status("ACTIVE")
                .build());
    }

    // ------------------------------------------------------------------
    // Scenario 1: exact payment (amount == bill balance)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Exact payment clears the bill, one receipt, no advance credit")
    void exactPayment_clearsBill() {
        MaintenanceBill bill = persistBill(new BigDecimal("5000.00"));

        PaymentDTO dto = billService.recordOfflinePayment(request(bill, new BigDecimal("5000.00")));

        assertThat(dto.getReceiptNumber()).isEqualTo("RCP-" + PERIOD + "-00001");
        assertThat(dto.getPaymentId()).isNotNull();
        assertThat(dto.getStatus()).isEqualTo(PaymentStatus.VERIFIED.name());
        assertThat(paymentRepository.count()).isEqualTo(1);

        MaintenanceBill reloaded = billRepository.findById(bill.getBillId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BillStatus.PAID);
        assertThat(reloaded.getBalanceAmount()).isEqualByComparingTo("0.00");
        assertThat(advanceCreditRepository.count()).isZero();
    }

    // ------------------------------------------------------------------
    // Scenario 2: partial payment (amount < bill balance)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Partial payment leaves the bill partially paid, no advance credit")
    void partialPayment_leavesBalance() {
        MaintenanceBill bill = persistBill(new BigDecimal("5000.00"));

        PaymentDTO dto = billService.recordOfflinePayment(request(bill, new BigDecimal("2000.00")));

        assertThat(dto.getReceiptNumber()).isEqualTo("RCP-" + PERIOD + "-00001");
        MaintenanceBill reloaded = billRepository.findById(bill.getBillId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BillStatus.PARTIALLY_PAID);
        assertThat(reloaded.getBalanceAmount()).isEqualByComparingTo("3000.00");
        assertThat(advanceCreditRepository.count()).isZero();
    }

    // ------------------------------------------------------------------
    // Scenario 3: overpayment (amount > bill balance) -- the regression case
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Overpayment records ONE payment + one advance credit, no duplicate-key error")
    void overpayment_recordsSinglePaymentAndAdvanceCredit() {
        MaintenanceBill bill = persistBill(new BigDecimal("5000.00"));

        PaymentDTO dto = billService.recordOfflinePayment(request(bill, new BigDecimal("20000.00")));

        // Exactly one payment row (the old bug inserted a second row -> duplicate receipt).
        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(dto.getReceiptNumber()).isEqualTo("RCP-" + PERIOD + "-00001");
        assertThat(dto.getAmount()).isEqualByComparingTo("20000.00");

        // Bill fully paid, surplus parked as advance credit.
        MaintenanceBill reloaded = billRepository.findById(bill.getBillId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BillStatus.PAID);
        assertThat(reloaded.getBalanceAmount()).isEqualByComparingTo("0.00");

        List<UnitAdvanceCredit> credits =
                advanceCreditRepository.findByUnit_UnitIdOrderByReceivedDateDescAdvanceCreditIdDesc(unit.getUnitId());
        assertThat(credits).hasSize(1);
        UnitAdvanceCredit credit = credits.get(0);
        assertThat(credit.getStatus()).isEqualTo(UnitAdvanceCredit.CreditStatus.AVAILABLE);
        assertThat(credit.getBalanceAmount()).isEqualByComparingTo("15000.00");

        // The payment stores the FULL amount, applied only up to the bill, and links the credit.
        MaintenancePayment payment = paymentRepository.findByTransactionId(
                "TXN-" + bill.getBillId() + "-20000.00").orElseThrow();
        assertThat(payment.getAmount()).isEqualByComparingTo("20000.00");
        assertThat(payment.getAppliedAmount()).isEqualByComparingTo("5000.00");
        assertThat(payment.getSurplusCreditId()).isEqualTo(credit.getAdvanceCreditId());
    }

    // ------------------------------------------------------------------
    // Scenario 4: sequential receipts across multiple payments
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Consecutive payments get strictly increasing receipt numbers")
    void sequentialPayments_incrementReceiptNumbers() {
        // Distinct months so the (unit, month, year) unique constraint on bills is satisfied.
        MaintenanceBill billA = persistBillForMonth(new BigDecimal("1000.00"), 1);
        MaintenanceBill billB = persistBillForMonth(new BigDecimal("1000.00"), 2);
        MaintenanceBill billC = persistBillForMonth(new BigDecimal("1000.00"), 3);

        String r1 = billService.recordOfflinePayment(request(billA, new BigDecimal("1000.00"))).getReceiptNumber();
        String r2 = billService.recordOfflinePayment(request(billB, new BigDecimal("1000.00"))).getReceiptNumber();
        String r3 = billService.recordOfflinePayment(request(billC, new BigDecimal("1000.00"))).getReceiptNumber();

        assertThat(r1).isEqualTo("RCP-" + PERIOD + "-00001");
        assertThat(r2).isEqualTo("RCP-" + PERIOD + "-00002");
        assertThat(r3).isEqualTo("RCP-" + PERIOD + "-00003");

        ReceiptSequence seq = sequenceRepository.findByPeriod(PERIOD).orElseThrow();
        assertThat(seq.getLastNumber()).isEqualTo(3);
        assertThat(paymentRepository.count()).isEqualTo(3);
    }

    // ------------------------------------------------------------------
    // Scenario 5: reverse a payment (with surplus) restores bill + voids credit
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Reversing an overpayment restores the bill and voids the advance credit")
    void reverseOverpayment_restoresBillAndVoidsCredit() {
        MaintenanceBill bill = persistBill(new BigDecimal("5000.00"));
        billService.recordOfflinePayment(request(bill, new BigDecimal("20000.00")));
        Long paymentId = paymentRepository.findByTransactionId("TXN-" + bill.getBillId() + "-20000.00")
                .orElseThrow().getPaymentId();

        PaymentDTO reversed = billService.reversePayment(paymentId, "Test reversal");

        assertThat(reversed.getStatus()).isEqualTo(PaymentStatus.REVERSED.name());

        MaintenanceBill reloaded = billRepository.findById(bill.getBillId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BillStatus.UNPAID);
        assertThat(reloaded.getBalanceAmount()).isEqualByComparingTo("5000.00");

        List<UnitAdvanceCredit> credits =
                advanceCreditRepository.findByUnit_UnitIdOrderByReceivedDateDescAdvanceCreditIdDesc(unit.getUnitId());
        assertThat(credits).hasSize(1);
        assertThat(credits.get(0).getStatus()).isEqualTo(UnitAdvanceCredit.CreditStatus.REVERSED);
        assertThat(credits.get(0).getBalanceAmount()).isEqualByComparingTo("0.00");
    }

    // ------------------------------------------------------------------
    // Scenario 6: reverse then re-record allocates the next receipt, no clash
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A payment recorded after a reversal gets the next receipt number")
    void reverseThenReRecord_usesNextReceiptNumber() {
        MaintenanceBill bill = persistBill(new BigDecimal("5000.00"));

        PaymentDTO first = billService.recordOfflinePayment(request(bill, new BigDecimal("5000.00")));
        assertThat(first.getReceiptNumber()).isEqualTo("RCP-" + PERIOD + "-00001");
        Long firstId = paymentRepository.findByTransactionId("TXN-" + bill.getBillId() + "-5000.00")
                .orElseThrow().getPaymentId();

        billService.reversePayment(firstId, "Wrong entry");

        PaymentDTO second = billService.recordOfflinePayment(request(bill, new BigDecimal("5000.00")));
        assertThat(second.getReceiptNumber()).isEqualTo("RCP-" + PERIOD + "-00002");

        // Both rows persist (reversed one kept for audit); no duplicate receipt numbers.
        List<MaintenancePayment> all = paymentRepository.findAll();
        assertThat(all).hasSize(2);
        assertThat(all).extracting(MaintenancePayment::getReceiptNumber).doesNotHaveDuplicates();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private MaintenanceBill persistBill(BigDecimal total) {
        return persistBillForMonth(total, LocalDate.now().getMonthValue());
    }

    private MaintenanceBill persistBillForMonth(BigDecimal total, int month) {
        return billRepository.save(MaintenanceBill.builder()
                .unit(unit)
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

    private RecordOfflinePaymentRequest request(MaintenanceBill bill, BigDecimal amount) {
        return RecordOfflinePaymentRequest.builder()
                .billId(bill.getBillId())
                .amount(amount)
                .paymentDate(LocalDate.now())
                .paymentMode("BANK_TRANSFER")
                .transactionId("TXN-" + bill.getBillId() + "-" + amount)
                .payerName("Umesh Patil")
                .remarks("Bank Transfer")
                .build();
    }
}
