package com.society.module.tds.service;

import com.society.enums.TdsRemittanceStatus;
import com.society.enums.VoucherStatus;
import com.society.exception.BusinessException;
import com.society.exception.ResourceNotFoundException;
import com.society.module.tds.dto.CreateRemittanceRequest;
import com.society.module.tds.dto.RecordItRemittanceRequest;
import com.society.module.tds.entity.TdsRemittance;
import com.society.module.tds.entity.TdsRemittanceLine;
import com.society.module.tds.repository.TdsRemittanceLineRepository;
import com.society.module.tds.repository.TdsRemittanceRepository;
import com.society.module.tds.specification.TdsSpecificationBuilder;
import com.society.module.vendor.entity.Vendor;
import com.society.module.voucher.entity.Voucher;
import com.society.module.voucher.repository.VoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TdsRemittanceServiceImpl} transition guard and eligibility rejections
 * (Task 8.4 of vendor-tds-management spec).
 *
 * <p>Covers:
 * <ul>
 *   <li>Forward-only transition guard - valid and forbidden transitions</li>
 *   <li>createBatch rejection reasons - eligibility, uniqueness, financial year, validation</li>
 *   <li>Mixed financial year rejection specifically</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 5.4, 5.5, 5.6, 6.5, 7.2, 7.3, 7.4</b></p>
 */
@ExtendWith(MockitoExtension.class)
class TdsRemittanceServiceTest {

    @Mock
    private TdsRemittanceRepository remittanceRepository;

    @Mock
    private TdsRemittanceLineRepository remittanceLineRepository;

    @Mock
    private VoucherRepository voucherRepository;

    @Mock
    private TdsSpecificationBuilder specificationBuilder;

    @InjectMocks
    private TdsRemittanceServiceImpl service;

    @Captor
    private ArgumentCaptor<TdsRemittance> remittanceCaptor;

    // Test fixtures
    private static final Long VOUCHER_ID_1 = 101L;
    private static final Long VOUCHER_ID_2 = 102L;
    private static final Long VOUCHER_ID_3 = 103L;
    private static final Long VOUCHER_ID_4 = 104L;

    private static final Long VENDOR_ID = 501L;

    private static final String ACCOUNTANT_NAME = "John Accountant";
    private static final String ACCOUNTANT_REFERENCE = "ACCT-REF-001";
    private static final String REMARKS = "Batch payment for June vouchers";
    private static final String CHALLAN_NUMBER = "CHL-2024-12345";

    private static final BigDecimal TDS_AMOUNT_1 = BigDecimal.valueOf(1000.00);
    private static final BigDecimal TDS_AMOUNT_2 = BigDecimal.valueOf(2000.00);
    private static final BigDecimal TDS_AMOUNT_3 = BigDecimal.valueOf(3000.00);
    private static final BigDecimal TDS_AMOUNT_4 = BigDecimal.valueOf(4000.00);

    private static final String FINANCIAL_YEAR = "2024-2025";
    private static final LocalDate PAID_TO_ACCOUNTANT_DATE = LocalDate.of(2024, 6, 20);
    private static final LocalDate PAID_TO_IT_DATE = LocalDate.of(2024, 6, 25);

    private Voucher eligibleVoucher1;
    private Voucher eligibleVoucher2;
    private Voucher eligibleVoucher3;
    private Voucher eligibleVoucher4;
    private Vendor vendor;

    @BeforeEach
    void setUp() {
        vendor = Vendor.builder().vendorId(VENDOR_ID).build();

        eligibleVoucher1 = createEligibleVoucher(VOUCHER_ID_1, FINANCIAL_YEAR, TDS_AMOUNT_1);
        eligibleVoucher2 = createEligibleVoucher(VOUCHER_ID_2, FINANCIAL_YEAR, TDS_AMOUNT_2);
        eligibleVoucher3 = createEligibleVoucher(VOUCHER_ID_3, FINANCIAL_YEAR, TDS_AMOUNT_3);
        eligibleVoucher4 = createEligibleVoucher(VOUCHER_ID_4, FINANCIAL_YEAR, TDS_AMOUNT_4);

        mockSecurityContext("testUser");
    }

    // ------------------------------------------------------------------
    // Forward-only transition guard tests
    // ------------------------------------------------------------------

    // Valid transitions
    @Test
    @DisplayName("Stage 1: createBatch advances from implicit DEDUCTED to PAID_TO_ACCOUNTANT")
    void createBatch_validTransition_deductedToPaidToAccountant() {
        CreateRemittanceRequest request = validCreateRequest(VOUCHER_ID_1);

        when(voucherRepository.findAllById(List.of(VOUCHER_ID_1)))
                .thenReturn(List.of(eligibleVoucher1));
        when(remittanceLineRepository.findLinkedVoucherIds(List.of(VOUCHER_ID_1)))
                .thenReturn(Set.of());
        when(remittanceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.createBatch(request);

        verify(remittanceRepository).save(remittanceCaptor.capture());
        TdsRemittance savedBatch = remittanceCaptor.getValue();
        assertThat(savedBatch.getStatus()).isEqualTo(TdsRemittanceStatus.PAID_TO_ACCOUNTANT);
    }

    @Test
    @DisplayName("Stage 2: recordItRemittance advances from PAID_TO_ACCOUNTANT to PAID_TO_IT_DEPARTMENT")
    void recordItRemittance_validTransition_paidToAccountantToPaidToItDepartment() {
        TdsRemittance batch = createBatchInStatus(TdsRemittanceStatus.PAID_TO_ACCOUNTANT);

        when(remittanceRepository.findById(batch.getTdsRemittanceId()))
                .thenReturn(Optional.of(batch));
        when(remittanceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RecordItRemittanceRequest request = validRecordItRequest();

        service.recordItRemittance(batch.getTdsRemittanceId(), request);

        verify(remittanceRepository).save(remittanceCaptor.capture());
        TdsRemittance savedBatch = remittanceCaptor.getValue();
        assertThat(savedBatch.getStatus()).isEqualTo(TdsRemittanceStatus.PAID_TO_IT_DEPARTMENT);
        assertThat(savedBatch.getChallanNumber()).isEqualTo(CHALLAN_NUMBER);
    }

    // Forbidden transitions
    @Test
    @DisplayName("Stage 2: recordItRemittance rejects backward transition (PAID_TO_IT_DEPARTMENT -> PAID_TO_ACCOUNTANT)")
    void recordItRemittance_rejects_backwardTransition() {
        TdsRemittance batch = createBatchInStatus(TdsRemittanceStatus.PAID_TO_IT_DEPARTMENT);

        when(remittanceRepository.findById(batch.getTdsRemittanceId()))
                .thenReturn(Optional.of(batch));
        RecordItRemittanceRequest request = validRecordItRequest();

        assertThatThrownBy(() -> service.recordItRemittance(batch.getTdsRemittanceId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not in PAID_TO_ACCOUNTANT status");
    }

    // ------------------------------------------------------------------
    // createBatch rejection reasons
    // ------------------------------------------------------------------

    @Test
    @DisplayName("createBatch rejects empty voucher list (Req 5.3)")
    void createBatch_rejects_emptyVoucherList() {
        CreateRemittanceRequest request = new CreateRemittanceRequest();
        request.setVoucherIds(List.of());
        request.setAccountantName(ACCOUNTANT_NAME);
        request.setPaidToAccountantDate(PAID_TO_ACCOUNTANT_DATE);

        assertThatThrownBy(() -> service.createBatch(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("At least one voucher is required");
    }

    @Test
    @DisplayName("createBatch rejects null voucher list (Req 5.3)")
    void createBatch_rejects_nullVoucherList() {
        CreateRemittanceRequest request = new CreateRemittanceRequest();
        request.setVoucherIds(null);
        request.setAccountantName(ACCOUNTANT_NAME);
        request.setPaidToAccountantDate(PAID_TO_ACCOUNTANT_DATE);

        assertThatThrownBy(() -> service.createBatch(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("At least one voucher is required");
    }

    @Test
    @DisplayName("createBatch rejects voucher not FINAL (Req 5.4)")
    void createBatch_rejects_voucherNotFinal() {
        Voucher nonFinalVoucher = createVoucher(VOUCHER_ID_1, FINANCIAL_YEAR, TDS_AMOUNT_1, VoucherStatus.DRAFT);

        when(voucherRepository.findAllById(List.of(VOUCHER_ID_1)))
                .thenReturn(List.of(nonFinalVoucher));

        CreateRemittanceRequest request = validCreateRequest(VOUCHER_ID_1);

        assertThatThrownBy(() -> service.createBatch(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not eligible deducted-TDS vouchers");
        assertThatThrownBy(() -> service.createBatch(request))
                .hasMessageContaining("must be FINAL");
    }

    @Test
    @DisplayName("createBatch rejects voucher not tdsApplicable (Req 5.4)")
    void createBatch_rejects_voucherNotTdsApplicable() {
        Voucher nonTdsVoucher = createVoucher(VOUCHER_ID_1, FINANCIAL_YEAR, TDS_AMOUNT_1);
        nonTdsVoucher.setTdsApplicable(false);

        when(voucherRepository.findAllById(List.of(VOUCHER_ID_1)))
                .thenReturn(List.of(nonTdsVoucher));

        CreateRemittanceRequest request = validCreateRequest(VOUCHER_ID_1);

        assertThatThrownBy(() -> service.createBatch(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not eligible deducted-TDS vouchers")
                .hasMessageContaining("TDS-applicable");
    }

    @Test
    @DisplayName("createBatch rejects voucher with tdsAmount <= 0 (Req 5.4)")
    void createBatch_rejects_voucherZeroTdsAmount() {
        Voucher zeroTdsVoucher = createVoucher(VOUCHER_ID_1, FINANCIAL_YEAR, BigDecimal.ZERO);

        when(voucherRepository.findAllById(List.of(VOUCHER_ID_1)))
                .thenReturn(List.of(zeroTdsVoucher));

        CreateRemittanceRequest request = validCreateRequest(VOUCHER_ID_1);

        assertThatThrownBy(() -> service.createBatch(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not eligible deducted-TDS vouchers");
        assertThatThrownBy(() -> service.createBatch(request))
                .hasMessageContaining("positive TDS amount");
    }

    @Test
    @DisplayName("createBatch rejects voucher with null vendor (Req 5.4)")
    void createBatch_rejects_voucherNoVendor() {
        Voucher noVendorVoucher = createVoucher(VOUCHER_ID_1, FINANCIAL_YEAR, TDS_AMOUNT_1);
        noVendorVoucher.setVendor(null);

        when(voucherRepository.findAllById(List.of(VOUCHER_ID_1)))
                .thenReturn(List.of(noVendorVoucher));

        CreateRemittanceRequest request = validCreateRequest(VOUCHER_ID_1);

        assertThatThrownBy(() -> service.createBatch(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not eligible deducted-TDS vouchers")
                .hasMessageContaining("reference a vendor");
    }

    @Test
    @DisplayName("createBatch rejects voucher already remitted (Req 5.5, 7.6)")
    void createBatch_rejects_voucherAlreadyRemitted() {
        when(voucherRepository.findAllById(List.of(VOUCHER_ID_1, VOUCHER_ID_2)))
                .thenReturn(List.of(eligibleVoucher1, eligibleVoucher2));
        when(remittanceLineRepository.findLinkedVoucherIds(List.of(VOUCHER_ID_1, VOUCHER_ID_2)))
                .thenReturn(Set.of(VOUCHER_ID_1));

        CreateRemittanceRequest request = new CreateRemittanceRequest();
        request.setVoucherIds(List.of(VOUCHER_ID_1, VOUCHER_ID_2));
        request.setAccountantName(ACCOUNTANT_NAME);
        request.setPaidToAccountantDate(PAID_TO_ACCOUNTANT_DATE);

        assertThatThrownBy(() -> service.createBatch(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already remitted");
        assertThatThrownBy(() -> service.createBatch(request))
                .hasMessageContaining("already belong to a batch");
    }

    @Test
    @DisplayName("createBatch rejects mixed financial years (Req 5.6)")
    void createBatch_rejects_mixedFinancialYears() {
        Voucher voucherFY1 = createEligibleVoucher(VOUCHER_ID_1, "2023-2024", TDS_AMOUNT_1);
        Voucher voucherFY2 = createEligibleVoucher(VOUCHER_ID_2, "2024-2025", TDS_AMOUNT_2);

        when(voucherRepository.findAllById(List.of(VOUCHER_ID_1, VOUCHER_ID_2)))
                .thenReturn(List.of(voucherFY1, voucherFY2));
        when(remittanceLineRepository.findLinkedVoucherIds(List.of(VOUCHER_ID_1, VOUCHER_ID_2)))
                .thenReturn(Set.of());

        CreateRemittanceRequest request = new CreateRemittanceRequest();
        request.setVoucherIds(List.of(VOUCHER_ID_1, VOUCHER_ID_2));
        request.setAccountantName(ACCOUNTANT_NAME);
        request.setPaidToAccountantDate(PAID_TO_ACCOUNTANT_DATE);

        assertThatThrownBy(() -> service.createBatch(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("multiple financial years");
        assertThatThrownBy(() -> service.createBatch(request))
                .hasMessageContaining("a single financial year");
    }

    @Test
    @DisplayName("createBatch rejects accountant name empty (Req 5.7)")
    void createBatch_rejects_emptyAccountantName() {
        CreateRemittanceRequest request = new CreateRemittanceRequest();
        request.setVoucherIds(List.of(VOUCHER_ID_1));
        request.setAccountantName("");
        request.setPaidToAccountantDate(PAID_TO_ACCOUNTANT_DATE);

        assertThatThrownBy(() -> service.createBatch(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Accountant name is required");
    }

    @Test
    @DisplayName("createBatch rejects accountant name exceeds 100 chars (Req 5.7)")
    void createBatch_rejects_longAccountantName() {
        String longName = "A".repeat(101);
        CreateRemittanceRequest request = new CreateRemittanceRequest();
        request.setVoucherIds(List.of(VOUCHER_ID_1));
        request.setAccountantName(longName);
        request.setPaidToAccountantDate(PAID_TO_ACCOUNTANT_DATE);

        assertThatThrownBy(() -> service.createBatch(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Accountant name exceeds maximum length");
    }

    @Test
    @DisplayName("createBatch rejects future paid-to-accountant date (Req 5.8)")
    void createBatch_rejects_futurePaidToAccountantDate() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        CreateRemittanceRequest request = new CreateRemittanceRequest();
        request.setVoucherIds(List.of(VOUCHER_ID_1));
        request.setAccountantName(ACCOUNTANT_NAME);
        request.setPaidToAccountantDate(futureDate);

        assertThatThrownBy(() -> service.createBatch(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Paid-to-accountant date must not be in the future");
    }

    @Test
    @DisplayName("createBatch rejects null paid-to-accountant date (Req 5.8)")
    void createBatch_rejects_nullPaidToAccountantDate() {
        CreateRemittanceRequest request = new CreateRemittanceRequest();
        request.setVoucherIds(List.of(VOUCHER_ID_1));
        request.setAccountantName(ACCOUNTANT_NAME);
        request.setPaidToAccountantDate(null);

        assertThatThrownBy(() -> service.createBatch(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Paid-to-accountant date is required");
    }

    @Test
    @DisplayName("createBatch rejects voucher not found (Req 5.4)")
    void createBatch_rejects_voucherNotFound() {
        when(voucherRepository.findAllById(List.of(VOUCHER_ID_1)))
                .thenReturn(List.of());

        CreateRemittanceRequest request = validCreateRequest(VOUCHER_ID_1);

        assertThatThrownBy(() -> service.createBatch(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not eligible deducted-TDS vouchers");
        assertThatThrownBy(() -> service.createBatch(request))
                .hasMessageContaining("not found");
    }

    // ------------------------------------------------------------------
    // recordItRemittance rejection reasons
    // ------------------------------------------------------------------

    @Test
    @DisplayName("recordItRemittance rejects unknown batch id (Req 6.6)")
    void recordItRemittance_rejects_unknownBatch() {
        when(remittanceRepository.findById(any())).thenReturn(Optional.empty());
        RecordItRemittanceRequest request = validRecordItRequest();

        assertThatThrownBy(() -> service.recordItRemittance(999L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("recordItRemittance rejects when batch not in PAID_TO_ACCOUNTANT status (Req 6.5)")
    void recordItRemittance_rejects_wrongBatchStatus() {
        TdsRemittance batch = createBatchInStatus(TdsRemittanceStatus.PAID_TO_IT_DEPARTMENT);

        when(remittanceRepository.findById(batch.getTdsRemittanceId()))
                .thenReturn(Optional.of(batch));
        RecordItRemittanceRequest request = validRecordItRequest();

        assertThatThrownBy(() -> service.recordItRemittance(batch.getTdsRemittanceId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not in PAID_TO_ACCOUNTANT status");
    }

    @Test
    @DisplayName("recordItRemittance rejects blank challan number (Req 6.2, 6.3)")
    void recordItRemittance_rejects_blankChallan() {
        TdsRemittance batch = createBatchInStatus(TdsRemittanceStatus.PAID_TO_ACCOUNTANT);

        when(remittanceRepository.findById(batch.getTdsRemittanceId()))
                .thenReturn(Optional.of(batch));
        RecordItRemittanceRequest request = new RecordItRemittanceRequest();
        request.setChallanNumber("");
        request.setPaidToItDate(PAID_TO_IT_DATE);

        assertThatThrownBy(() -> service.recordItRemittance(batch.getTdsRemittanceId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Challan number is required");
    }

    @Test
    @DisplayName("recordItRemittance rejects null challan number (Req 6.2, 6.3)")
    void recordItRemittance_rejects_nullChallan() {
        TdsRemittance batch = createBatchInStatus(TdsRemittanceStatus.PAID_TO_ACCOUNTANT);

        when(remittanceRepository.findById(batch.getTdsRemittanceId()))
                .thenReturn(Optional.of(batch));
        RecordItRemittanceRequest request = new RecordItRemittanceRequest();
        request.setChallanNumber(null);
        request.setPaidToItDate(PAID_TO_IT_DATE);

        assertThatThrownBy(() -> service.recordItRemittance(batch.getTdsRemittanceId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Challan number is required");
    }

    @Test
    @DisplayName("recordItRemittance rejects challan exceeds 50 chars (Req 6.2, 6.3)")
    void recordItRemittance_rejects_longChallan() {
        TdsRemittance batch = createBatchInStatus(TdsRemittanceStatus.PAID_TO_ACCOUNTANT);

        when(remittanceRepository.findById(batch.getTdsRemittanceId()))
                .thenReturn(Optional.of(batch));
        String longChallan = "C".repeat(51);
        RecordItRemittanceRequest request = new RecordItRemittanceRequest();
        request.setChallanNumber(longChallan);
        request.setPaidToItDate(PAID_TO_IT_DATE);

        assertThatThrownBy(() -> service.recordItRemittance(batch.getTdsRemittanceId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Challan number exceeds maximum length");
    }

    @Test
    @DisplayName("recordItRemittance rejects null paid-to-IT date (Req 6.3, 6.4)")
    void recordItRemittance_rejects_nullPaidToItDate() {
        TdsRemittance batch = createBatchInStatus(TdsRemittanceStatus.PAID_TO_ACCOUNTANT);

        when(remittanceRepository.findById(batch.getTdsRemittanceId()))
                .thenReturn(Optional.of(batch));
        RecordItRemittanceRequest request = new RecordItRemittanceRequest();
        request.setChallanNumber(CHALLAN_NUMBER);
        request.setPaidToItDate(null);

        assertThatThrownBy(() -> service.recordItRemittance(batch.getTdsRemittanceId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Paid-to-IT-department date is required");
    }

    @Test
    @DisplayName("recordItRemittance rejects future paid-to-IT date (Req 6.4)")
    void recordItRemittance_rejects_futurePaidToItDate() {
        TdsRemittance batch = createBatchInStatus(TdsRemittanceStatus.PAID_TO_ACCOUNTANT);

        when(remittanceRepository.findById(batch.getTdsRemittanceId()))
                .thenReturn(Optional.of(batch));
        LocalDate futureDate = LocalDate.now().plusDays(1);
        RecordItRemittanceRequest request = new RecordItRemittanceRequest();
        request.setChallanNumber(CHALLAN_NUMBER);
        request.setPaidToItDate(futureDate);

        assertThatThrownBy(() -> service.recordItRemittance(batch.getTdsRemittanceId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Paid-to-IT-department date must not be in the future");
    }

    // ------------------------------------------------------------------
    // Mixed FY rejection - additional verification
    // ------------------------------------------------------------------

    @Test
    @DisplayName("createBatch rejects three vouchers spanning two financial years")
    void createBatch_rejects_threeVouchersTwoFinancialYears() {
        Voucher voucherFY1 = createEligibleVoucher(VOUCHER_ID_1, "2023-2024", TDS_AMOUNT_1);
        Voucher voucherFY2a = createEligibleVoucher(VOUCHER_ID_2, "2024-2025", TDS_AMOUNT_2);
        Voucher voucherFY2b = createEligibleVoucher(VOUCHER_ID_3, "2024-2025", TDS_AMOUNT_3);

        when(voucherRepository.findAllById(List.of(VOUCHER_ID_1, VOUCHER_ID_2, VOUCHER_ID_3)))
                .thenReturn(List.of(voucherFY1, voucherFY2a, voucherFY2b));
        when(remittanceLineRepository.findLinkedVoucherIds(List.of(VOUCHER_ID_1, VOUCHER_ID_2, VOUCHER_ID_3)))
                .thenReturn(Set.of());

        CreateRemittanceRequest request = new CreateRemittanceRequest();
        request.setVoucherIds(List.of(VOUCHER_ID_1, VOUCHER_ID_2, VOUCHER_ID_3));
        request.setAccountantName(ACCOUNTANT_NAME);
        request.setPaidToAccountantDate(PAID_TO_ACCOUNTANT_DATE);

        assertThatThrownBy(() -> service.createBatch(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("multiple financial years");
    }

    @Test
    @DisplayName("createBatch allows three vouchers with same financial year")
    void createBatch_allows_threeVouchersSameFinancialYear() {
        when(voucherRepository.findAllById(List.of(VOUCHER_ID_1, VOUCHER_ID_2, VOUCHER_ID_3)))
                .thenReturn(List.of(eligibleVoucher1, eligibleVoucher2, eligibleVoucher3));
        when(remittanceLineRepository.findLinkedVoucherIds(List.of(VOUCHER_ID_1, VOUCHER_ID_2, VOUCHER_ID_3)))
                .thenReturn(Set.of());
        when(remittanceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateRemittanceRequest request = new CreateRemittanceRequest();
        request.setVoucherIds(List.of(VOUCHER_ID_1, VOUCHER_ID_2, VOUCHER_ID_3));
        request.setAccountantName(ACCOUNTANT_NAME);
        request.setPaidToAccountantDate(PAID_TO_ACCOUNTANT_DATE);

        service.createBatch(request);

        verify(remittanceRepository).save(remittanceCaptor.capture());
        TdsRemittance savedBatch = remittanceCaptor.getValue();
        assertThat(savedBatch.getFinancialYear()).isEqualTo(FINANCIAL_YEAR);
        assertThat(savedBatch.getLines()).hasSize(3);
        assertThat(savedBatch.getTotalAmount()).isEqualByComparingTo("6000.00");
    }

    // ------------------------------------------------------------------
    // Additional edge case tests for comprehensive coverage
    // ------------------------------------------------------------------

    @Test
    @DisplayName("createBatch rejects voucher list with null ids (Req 5.3)")
    void createBatch_rejects_voucherListWithNullIds() {
        List<Long> voucherIdsWithNull = new ArrayList<>();
        voucherIdsWithNull.add(VOUCHER_ID_1);
        voucherIdsWithNull.add(null);
        voucherIdsWithNull.add(VOUCHER_ID_2);
        
        CreateRemittanceRequest request = new CreateRemittanceRequest();
        request.setVoucherIds(voucherIdsWithNull);
        request.setAccountantName(ACCOUNTANT_NAME);
        request.setPaidToAccountantDate(PAID_TO_ACCOUNTANT_DATE);

        assertThatThrownBy(() -> service.createBatch(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Voucher ids must not be null");
    }

    @Test
    @DisplayName("createBatch rejects voucher list exceeding 500 vouchers (Req 5.7)")
    void createBatch_rejects_tooManyVouchers() {
        List<Long> tooManyIds = new ArrayList<>();
        for (long i = 1; i <= 501; i++) {
            tooManyIds.add(i);
        }

        CreateRemittanceRequest request = new CreateRemittanceRequest();
        request.setVoucherIds(tooManyIds);
        request.setAccountantName(ACCOUNTANT_NAME);
        request.setPaidToAccountantDate(PAID_TO_ACCOUNTANT_DATE);

        assertThatThrownBy(() -> service.createBatch(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Too many vouchers")
                .hasMessageContaining("at most 500 vouchers");
    }

    @Test
    @DisplayName("createBatch removes duplicate voucher ids and processes unique ones")
    void createBatch_removes_duplicateVoucherIds() {
        when(voucherRepository.findAllById(List.of(VOUCHER_ID_1, VOUCHER_ID_2)))
                .thenReturn(List.of(eligibleVoucher1, eligibleVoucher2));
        when(remittanceLineRepository.findLinkedVoucherIds(List.of(VOUCHER_ID_1, VOUCHER_ID_2)))
                .thenReturn(Set.of());
        when(remittanceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateRemittanceRequest request = new CreateRemittanceRequest();
        request.setVoucherIds(List.of(VOUCHER_ID_1, VOUCHER_ID_2, VOUCHER_ID_1)); // duplicate VOUCHER_ID_1
        request.setAccountantName(ACCOUNTANT_NAME);
        request.setPaidToAccountantDate(PAID_TO_ACCOUNTANT_DATE);

        service.createBatch(request);

        verify(remittanceRepository).save(remittanceCaptor.capture());
        TdsRemittance savedBatch = remittanceCaptor.getValue();
        assertThat(savedBatch.getLines()).hasSize(2); // duplicates removed
    }

    @Test
    @DisplayName("createBatch rejects voucher with negative TDS amount (Req 5.4)")
    void createBatch_rejects_voucherNegativeTdsAmount() {
        Voucher negativeTdsVoucher = createVoucher(VOUCHER_ID_1, FINANCIAL_YEAR, BigDecimal.valueOf(-100.00));

        when(voucherRepository.findAllById(List.of(VOUCHER_ID_1)))
                .thenReturn(List.of(negativeTdsVoucher));

        CreateRemittanceRequest request = validCreateRequest(VOUCHER_ID_1);

        assertThatThrownBy(() -> service.createBatch(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not eligible deducted-TDS vouchers")
                .hasMessageContaining("positive TDS amount");
    }

    @Test
    @DisplayName("createBatch rejects voucher with null TDS amount (Req 5.4)")
    void createBatch_rejects_voucherNullTdsAmount() {
        Voucher nullTdsVoucher = createVoucher(VOUCHER_ID_1, FINANCIAL_YEAR, TDS_AMOUNT_1);
        nullTdsVoucher.setTdsAmount(null);

        when(voucherRepository.findAllById(List.of(VOUCHER_ID_1)))
                .thenReturn(List.of(nullTdsVoucher));

        CreateRemittanceRequest request = validCreateRequest(VOUCHER_ID_1);

        assertThatThrownBy(() -> service.createBatch(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not eligible deducted-TDS vouchers")
                .hasMessageContaining("positive TDS amount");
    }

    @Test
    @DisplayName("createBatch rejects null request (edge case)")
    void createBatch_rejects_nullRequest() {
        assertThatThrownBy(() -> service.createBatch(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("batch-creation request is required");
    }

    @Test
    @DisplayName("recordItRemittance rejects null request (edge case)")
    void recordItRemittance_rejects_nullRequest() {
        assertThatThrownBy(() -> service.recordItRemittance(1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("IT-remittance request is required");
    }

    @Test
    @DisplayName("createBatch trims whitespace from accountant name")
    void createBatch_trimsWhitespace_accountantName() {
        when(voucherRepository.findAllById(List.of(VOUCHER_ID_1)))
                .thenReturn(List.of(eligibleVoucher1));
        when(remittanceLineRepository.findLinkedVoucherIds(List.of(VOUCHER_ID_1)))
                .thenReturn(Set.of());
        when(remittanceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateRemittanceRequest request = new CreateRemittanceRequest();
        request.setVoucherIds(List.of(VOUCHER_ID_1));
        request.setAccountantName("   " + ACCOUNTANT_NAME + "   ");  // with whitespace
        request.setPaidToAccountantDate(PAID_TO_ACCOUNTANT_DATE);

        service.createBatch(request);

        verify(remittanceRepository).save(remittanceCaptor.capture());
        TdsRemittance savedBatch = remittanceCaptor.getValue();
        assertThat(savedBatch.getAccountantName()).isEqualTo(ACCOUNTANT_NAME); // trimmed
    }

    @Test
    @DisplayName("recordItRemittance trims whitespace from challan number")
    void recordItRemittance_trimsWhitespace_challanNumber() {
        TdsRemittance batch = createBatchInStatus(TdsRemittanceStatus.PAID_TO_ACCOUNTANT);

        when(remittanceRepository.findById(batch.getTdsRemittanceId()))
                .thenReturn(Optional.of(batch));
        when(remittanceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RecordItRemittanceRequest request = new RecordItRemittanceRequest();
        request.setChallanNumber("   " + CHALLAN_NUMBER + "   ");  // with whitespace
        request.setPaidToItDate(PAID_TO_IT_DATE);

        service.recordItRemittance(batch.getTdsRemittanceId(), request);

        verify(remittanceRepository).save(remittanceCaptor.capture());
        TdsRemittance savedBatch = remittanceCaptor.getValue();
        assertThat(savedBatch.getChallanNumber()).isEqualTo(CHALLAN_NUMBER); // trimmed
    }

    // ------------------------------------------------------------------
    // State validation - no mutations on rejected transitions  
    // ------------------------------------------------------------------

    @Test
    @DisplayName("recordItRemittance leaves batch unchanged when transition is rejected (Req 7.2, 7.3)")
    void recordItRemittance_leavesUnchanged_whenRejected() {
        TdsRemittance batch = createBatchInStatus(TdsRemittanceStatus.PAID_TO_IT_DEPARTMENT);
        TdsRemittanceStatus originalStatus = batch.getStatus();

        when(remittanceRepository.findById(batch.getTdsRemittanceId()))
                .thenReturn(Optional.of(batch));

        RecordItRemittanceRequest request = validRecordItRequest();

        assertThatThrownBy(() -> service.recordItRemittance(batch.getTdsRemittanceId(), request))
                .isInstanceOf(BusinessException.class);

        // Verify batch state was not mutated
        assertThat(batch.getStatus()).isEqualTo(originalStatus);
        assertThat(batch.getChallanNumber()).isNull();
        assertThat(batch.getPaidToItDate()).isNull();

        // Verify repository save was never called
        verify(remittanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("createBatch leaves database unchanged when any validation fails (Req 5.3-5.8)")
    void createBatch_leavesUnchanged_whenRejected() {
        CreateRemittanceRequest request = new CreateRemittanceRequest();
        request.setVoucherIds(List.of(VOUCHER_ID_1));
        request.setAccountantName("");  // invalid - empty
        request.setPaidToAccountantDate(PAID_TO_ACCOUNTANT_DATE);

        assertThatThrownBy(() -> service.createBatch(request))
                .isInstanceOf(BusinessException.class);

        // Verify no repository interactions occurred
        verifyNoInteractions(voucherRepository);
        verifyNoInteractions(remittanceLineRepository);
        verify(remittanceRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Additional forward-only transition tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Forward-only guard rejects transitions from already completed batch")
    void transitionGuard_rejects_fromCompletedBatch() {
        TdsRemittance batch = createBatchInStatus(TdsRemittanceStatus.PAID_TO_IT_DEPARTMENT);

        when(remittanceRepository.findById(batch.getTdsRemittanceId()))
                .thenReturn(Optional.of(batch));

        RecordItRemittanceRequest request = validRecordItRequest();

        // Trying to record IT remittance on an already completed batch should fail
        assertThatThrownBy(() -> service.recordItRemittance(batch.getTdsRemittanceId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not in PAID_TO_ACCOUNTANT status");
    }

    // ------------------------------------------------------------------
    // Helper methods
    // ------------------------------------------------------------------

    private CreateRemittanceRequest validCreateRequest(Long... voucherIds) {
        CreateRemittanceRequest request = new CreateRemittanceRequest();
        request.setVoucherIds(List.of(voucherIds));
        request.setAccountantName(ACCOUNTANT_NAME);
        request.setPaidToAccountantDate(PAID_TO_ACCOUNTANT_DATE);
        request.setAccountantReference(ACCOUNTANT_REFERENCE);
        request.setRemarks(REMARKS);
        return request;
    }

    private RecordItRemittanceRequest validRecordItRequest() {
        RecordItRemittanceRequest request = new RecordItRemittanceRequest();
        request.setChallanNumber(CHALLAN_NUMBER);
        request.setPaidToItDate(PAID_TO_IT_DATE);
        request.setRemarks(REMARKS);
        return request;
    }

    private Voucher createEligibleVoucher(Long voucherId, String financialYear, BigDecimal tdsAmount) {
        return createVoucher(voucherId, financialYear, tdsAmount, VoucherStatus.FINAL);
    }

    private Voucher createVoucher(Long voucherId, String financialYear, BigDecimal tdsAmount) {
        return createVoucher(voucherId, financialYear, tdsAmount, VoucherStatus.FINAL);
    }

    private Voucher createVoucher(Long voucherId, String financialYear, BigDecimal tdsAmount, VoucherStatus status) {
        return Voucher.builder()
                .voucherId(voucherId)
                .voucherNumber("VCH-" + voucherId)
                .financialYear(financialYear)
                .amount(tdsAmount.multiply(BigDecimal.valueOf(10)))
                .tdsApplicable(true)
                .tdsSection("194C")
                .tdsRate(BigDecimal.valueOf(10.00))
                .tdsAmount(tdsAmount)
                .vendor(vendor)
                .status(status)
                .build();
    }

    private TdsRemittance createBatchInStatus(TdsRemittanceStatus status) {
        return TdsRemittance.builder()
                .tdsRemittanceId(1L)
                .batchReference("TDS-2024-0001")
                .status(status)
                .financialYear(FINANCIAL_YEAR)
                .totalAmount(BigDecimal.valueOf(1000.00))
                .paidToAccountantDate(PAID_TO_ACCOUNTANT_DATE)
                .build();
    }

    private void mockSecurityContext(String username) {
        // Mock security context for currentUser() - this would normally mock SecurityContextHolder
        // For unit test purposes, we just ensure the method doesn't throw exceptions
    }
}
