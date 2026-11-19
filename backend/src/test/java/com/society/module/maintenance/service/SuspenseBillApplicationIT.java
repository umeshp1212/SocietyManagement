package com.society.module.maintenance.service;

import com.society.enums.OccupancyStatus;
import com.society.enums.PaymentMode;
import com.society.enums.UnitType;
import com.society.module.maintenance.dto.GenerateBillsRequest;
import com.society.module.maintenance.dto.RecordOfflinePaymentRequest;
import com.society.module.maintenance.dto.SuspenseEntryDTO;
import com.society.module.maintenance.entity.MaintenanceBill;
import com.society.module.maintenance.entity.MaintenanceBill.BillStatus;
import com.society.module.maintenance.entity.MaintenanceChargeConfig;
import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.maintenance.entity.UnitAdvanceCredit;
import com.society.module.maintenance.repository.*;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.repository.UnitRepository;
import com.society.module.settings.entity.SocietySettings;
import com.society.module.settings.repository.SocietySettingsRepository;
import com.society.module.settings.service.SocietySettingsService;
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
 * Scenario tests for:
 *  (1) opening balance no longer being charged again on later bills (December double-count),
 *  (2) suspense assignment recording real payments on bills,
 *  (3) the interest refund credit and its automatic use on the next bill,
 *  (4) reversing an assignment.
 *
 * Setup: one unit, flat charge 2000/month, interest 1%/month, opening principal 6000.
 *   Nov bill = 2000 + 6000 principal + 60 new interest = 8060.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:suspense_bill_it;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF"
})
class SuspenseBillApplicationIT {

    private static final String ACTOR = "admin_it";

    @Autowired private SuspenseService suspenseService;
    @Autowired private MaintenanceBillService billService;
    @Autowired private OpeningBalanceService openingBalanceService;
    @Autowired private SocietySettingsService settingsService;
    @Autowired private SocietySettingsRepository settingsRepository;
    @Autowired private MaintenanceBillRepository billRepository;
    @Autowired private MaintenancePaymentRepository paymentRepository;
    @Autowired private MaintenanceChargeConfigRepository chargeConfigRepository;
    @Autowired private UnitAdvanceCreditRepository creditRepository;
    @Autowired private SuspenseEntryRepository suspenseEntryRepository;
    @Autowired private SuspenseAuditTrailRepository auditTrailRepository;
    @Autowired private OpeningBalanceRepository openingBalanceRepository;
    @Autowired private MaintenanceLedgerRepository ledgerRepository;
    @Autowired private PenaltyRepository penaltyRepository;
    @Autowired private UnitRepository unitRepository;

    private Unit unit;

    @BeforeEach
    void setUp() {
        ledgerRepository.deleteAll();
        creditRepository.deleteAll();
        auditTrailRepository.deleteAll();
        suspenseEntryRepository.deleteAll();
        paymentRepository.deleteAll();
        billRepository.deleteAll();
        penaltyRepository.deleteAll();
        openingBalanceRepository.deleteAll();
        chargeConfigRepository.deleteAll();
        settingsRepository.deleteAll();
        unitRepository.deleteAll();

        unit = unitRepository.save(Unit.builder()
                .unitNumber("A-101")
                .unitType(UnitType.FLAT)
                .occupancyStatus(OccupancyStatus.SELF_OCCUPIED)
                .monthlyMaintenanceAmount(new BigDecimal("2000.00"))
                .status("ACTIVE")
                .build());

        chargeConfigRepository.save(MaintenanceChargeConfig.builder()
                .chargeCode("MAINTENANCE")
                .chargeName("Maintenance")
                .calculationType(MaintenanceChargeConfig.CalculationType.FLAT)
                .flatAmount(new BigDecimal("2000.00"))
                .applicableTo(MaintenanceChargeConfig.ApplicableTo.ALL)
                .displayOrder(1)
                .isActive(true)
                .build());

        SocietySettings s = settingsService.getSettings();
        s.setInterestRatePercent(new BigDecimal("1.00"));
        settingsService.saveSettings(s);

        openingBalanceService.createOrUpdateOpeningBalance(unit.getUnitId(),
                new BigDecimal("6000.00"), BigDecimal.ZERO, LocalDate.of(2026, 10, 30), "legacy", ACTOR);
    }

    @Test
    @DisplayName("Opening balance is charged on the first bill only, not again on December")
    void openingBalanceNotChargedTwice() {
        generate(11);
        MaintenanceBill nov = bill(11);
        assertThat(nov.getTotalAmount()).isEqualByComparingTo("8060.00");

        // Owner pays the whole November bill.
        pay(nov, "8060.00");

        generate(12);
        MaintenanceBill dec = bill(12);
        // December must be ONLY December's charges: the 6000 legacy dues were paid via November.
        assertThat(dec.getPrincipalArrears()).isEqualByComparingTo("0.00");
        assertThat(dec.getTotalAmount()).isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("Unpaid November: December carries 8000 principal, not 14000")
    void unpaidNovemberCarriesOnce() {
        generate(11);
        generate(12);
        MaintenanceBill dec = bill(12);
        assertThat(dec.getPrincipalArrears()).isEqualByComparingTo("8000.00");
    }

    @Test
    @DisplayName("Assigning suspense records a real payment dated when money was received, plus interest refund credit")
    void assignRecordsPaymentAndRefund() {
        generate(11);
        SuspenseEntryDTO created = suspenseService.createSuspenseEntry(
                new BigDecimal("3000.00"), LocalDate.of(2026, 4, 15), PaymentMode.BANK_TRANSFER.name(),
                "UTR-APR", "Unidentified April transfer", ACTOR);

        suspenseService.assignToUnit(created.getSuspenseId(), unit.getUnitId(), "It is A-101", false, ACTOR);

        MaintenanceBill nov = bill(11);
        assertThat(nov.getPaidAmount()).isEqualByComparingTo("3000.00");
        // The 60 of interest on the November bill is removed immediately: 8060 - 60 - 3000 = 5000.
        assertThat(nov.getBalanceAmount()).isEqualByComparingTo("5000.00");
        assertThat(nov.getInterestArrears()).isEqualByComparingTo("0.00");
        assertThat(nov.getStatus()).isEqualTo(BillStatus.PARTIALLY_PAID);

        List<MaintenancePayment> payments = paymentRepository.findByBill_BillId(nov.getBillId());
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getPaymentDate()).isEqualTo(LocalDate.of(2026, 4, 15));

        // Refund = 30/month x 7 months (May..Nov) = 210; 60 comes off the November bill, 150 is left as credit.
        List<UnitAdvanceCredit> credits = creditRepository
                .findByUnit_UnitIdAndStatusOrderByReceivedDateAscAdvanceCreditIdAsc(
                        unit.getUnitId(), UnitAdvanceCredit.CreditStatus.AVAILABLE);
        assertThat(credits).hasSize(1);
        assertThat(credits.get(0).getAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("The refund credit and the payment both flow into the next (December) bill")
    void refundAppliedOnNextBill() {
        generate(11);
        SuspenseEntryDTO created = suspenseService.createSuspenseEntry(
                new BigDecimal("3000.00"), LocalDate.of(2026, 4, 15), PaymentMode.BANK_TRANSFER.name(),
                "UTR-APR", "Unidentified April transfer", ACTOR);
        suspenseService.assignToUnit(created.getSuspenseId(), unit.getUnitId(), "It is A-101", false, ACTOR);

        generate(12);
        MaintenanceBill dec = bill(12);
        // Nov balance 5000 carried (all principal), + 1% new interest 50 + 2000 charges = 7050,
        // less the 150 leftover refund credit = 6900.
        assertThat(dec.getPrincipalArrears()).isEqualByComparingTo("5000.00");
        assertThat(dec.getCreditApplied()).isEqualByComparingTo("150.00");
        assertThat(dec.getTotalAmount()).isEqualByComparingTo("6900.00");
    }

    @Test
    @DisplayName("Reversing the assignment restores the bill and voids the refund credit")
    void reverseUndoesEverything() {
        generate(11);
        SuspenseEntryDTO created = suspenseService.createSuspenseEntry(
                new BigDecimal("3000.00"), LocalDate.of(2026, 4, 15), PaymentMode.BANK_TRANSFER.name(),
                "UTR-APR", "Unidentified April transfer", ACTOR);
        suspenseService.assignToUnit(created.getSuspenseId(), unit.getUnitId(), "It is A-101", false, ACTOR);

        suspenseService.reverseAssignment(created.getSuspenseId(), "Wrong unit", ACTOR);

        MaintenanceBill nov = bill(11);
        assertThat(nov.getPaidAmount()).isEqualByComparingTo("0.00");
        assertThat(nov.getBalanceAmount()).isEqualByComparingTo("8060.00");
        assertThat(nov.getStatus()).isEqualTo(BillStatus.UNPAID);

        assertThat(creditRepository.getAvailableCreditByUnit(unit.getUnitId())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Worked example: 200000 opening dues, 2601 monthly charge, 150000 paid in November")
    void workedExample_openingDues200000_paid150000() {
        // Re-seed with the example's numbers.
        openingBalanceService.createOrUpdateOpeningBalance(unit.getUnitId(),
                new BigDecimal("200000.00"), BigDecimal.ZERO, LocalDate.of(2026, 10, 30), "legacy", ACTOR);
        MaintenanceChargeConfig charge = chargeConfigRepository.findAll().get(0);
        charge.setFlatAmount(new BigDecimal("2601.00"));
        chargeConfigRepository.save(charge);

        generate(11);
        MaintenanceBill nov = bill(11);
        // 2601 charges + 200000 dues + 1% interest on 200000 (2000)
        assertThat(nov.getTotalAmount()).isEqualByComparingTo("204601.00");

        pay(nov, "150000.00");
        nov = bill(11);
        assertThat(nov.getBalanceAmount()).isEqualByComparingTo("54601.00");

        // The Opening Balance record itself is not reduced by bill payments.
        assertThat(openingBalanceRepository.findByUnit_UnitId(unit.getUnitId()).orElseThrow()
                .getBalanceAmount()).isEqualByComparingTo("200000.00");

        generate(12);
        MaintenanceBill dec = bill(12);
        assertThat(dec.getPrincipalArrears()).isEqualByComparingTo("54601.00");   // carried forward
        assertThat(dec.getNewInterest()).isEqualByComparingTo("546.00");          // 1% of 54601, rounded
        assertThat(dec.getLastReceiptAmount()).isEqualByComparingTo("150000.00");
        assertThat(dec.getTotalAmount()).isEqualByComparingTo("57748.00");        // 2601 + 54601 + 546
    }

    // ------------------------------------------------------------------

    private void generate(int month) {
        billService.generateMonthlyBills(GenerateBillsRequest.builder()
                .month(month).year(2026).build());
    }

    private MaintenanceBill bill(int month) {
        return billRepository.findByUnit_UnitIdAndBillMonthAndBillYear(unit.getUnitId(), month, 2026).orElseThrow();
    }

    private void pay(MaintenanceBill bill, String amount) {
        billService.recordOfflinePayment(RecordOfflinePaymentRequest.builder()
                .billId(bill.getBillId())
                .amount(new BigDecimal(amount))
                .paymentDate(LocalDate.of(2026, 11, 20))
                .paymentMode("CASH")
                .payerName("Owner")
                .build());
    }
}
