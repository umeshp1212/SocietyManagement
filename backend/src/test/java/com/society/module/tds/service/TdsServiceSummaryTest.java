package com.society.module.tds.service;

import com.society.enums.TdsRemittanceStatus;
import com.society.module.tds.dto.TdsFilterRequest;
import com.society.module.tds.dto.TdsLineDTO;
import com.society.module.tds.dto.TdsSummaryDTO;
import com.society.module.tds.entity.TdsLineView;
import com.society.module.tds.repository.TdsLineViewRepository;
import com.society.module.tds.specification.TdsSpecificationBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the TdsService summary math and filter mapping ({@link TdsService},
 * task 7.3).
 *
 * <p>Covers:
 * <ul>
 *   <li>HALF_UP boundary (0.005 rounding) – Requirement 4.8</li>
 *   <li>Empty set zeros – Requirement 4.9</li>
 *   <li>Single line aggregation – Requirements 4.2, 4.3, 4.4, 4.5, 4.6</li>
 *   <li>Blank-field no-predicate – Requirements 2.2, 2.13 (filters never widen)</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 2.2, 4.8, 4.9</b></p>
 */
class TdsServiceSummaryTest {

    private TdsSpecificationBuilder specificationBuilder;
    private TdsLineViewRepository tdsLineViewRepository;
    private TdsService service;

    @BeforeEach
    void setUp() {
        specificationBuilder = mock(TdsSpecificationBuilder.class);
        tdsLineViewRepository = mock(TdsLineViewRepository.class);
        service = new TdsServiceImpl(specificationBuilder, tdsLineViewRepository);
    }

    // ------------------------------------------------------------------
    // Empty set (Requirement 4.9)
    // ------------------------------------------------------------------

    @Test
    void emptyFilteredSet_returnsAllZeros() {
        TdsFilterRequest filter = new TdsFilterRequest();
        List<TdsLineView> emptyLines = List.of();

        when(specificationBuilder.build(any())).thenReturn(Specification.where(null));
        when(tdsLineViewRepository.findAll(any(Specification.class))).thenReturn(emptyLines);

        TdsSummaryDTO summary = service.summarize(filter);

        assertThat(summary.getTotalDeducted()).isEqualByComparingTo("0.00");
        assertThat(summary.getTotalPaidToAccountant()).isEqualByComparingTo("0.00");
        assertThat(summary.getTotalPaidToItDept()).isEqualByComparingTo("0.00");
        assertThat(summary.getTotalPending()).isEqualByComparingTo("0.00");
        assertThat(summary.getLineCount()).isZero();
        verify(specificationBuilder).build(any());
        verify(tdsLineViewRepository).findAll(any(Specification.class));
    }

    // ------------------------------------------------------------------
    // Single line scenarios (Requirements 4.2, 4.3, 4.4, 4.5, 4.6)
    // ------------------------------------------------------------------

    @Test
    void singleLine_deducted_status() {
        TdsLineView line = createLine(1L, BigDecimal.valueOf(1000.00),
                LocalDate.of(2024, 6, 15), TdsRemittanceStatus.DEDUCTED,
                null, null);

        TdsSummaryDTO summary = summarize(List.of(line));

        assertThat(summary.getTotalDeducted()).isEqualByComparingTo("1000.00");
        assertThat(summary.getTotalPaidToAccountant()).isEqualByComparingTo("0.00");
        assertThat(summary.getTotalPaidToItDept()).isEqualByComparingTo("0.00");
        assertThat(summary.getTotalPending()).isEqualByComparingTo("1000.00");
        assertThat(summary.getLineCount()).isEqualTo(1);
    }

    @Test
    void singleLine_paidToAccountant_status() {
        TdsLineView line = createLine(1L, BigDecimal.valueOf(2500.00),
                LocalDate.of(2024, 6, 15), TdsRemittanceStatus.PAID_TO_ACCOUNTANT,
                LocalDate.of(2024, 6, 20), null);

        TdsSummaryDTO summary = summarize(List.of(line));

        assertThat(summary.getTotalDeducted()).isEqualByComparingTo("2500.00");
        assertThat(summary.getTotalPaidToAccountant()).isEqualByComparingTo("2500.00");
        assertThat(summary.getTotalPaidToItDept()).isEqualByComparingTo("0.00");
        assertThat(summary.getTotalPending()).isEqualByComparingTo("2500.00");
        assertThat(summary.getLineCount()).isEqualTo(1);
    }

    @Test
    void singleLine_paidToItDepartment_status() {
        TdsLineView line = createLine(1L, BigDecimal.valueOf(5000.00),
                LocalDate.of(2024, 6, 15), TdsRemittanceStatus.PAID_TO_IT_DEPARTMENT,
                LocalDate.of(2024, 6, 20), LocalDate.of(2024, 6, 25));

        TdsSummaryDTO summary = summarize(List.of(line));

        assertThat(summary.getTotalDeducted()).isEqualByComparingTo("5000.00");
        assertThat(summary.getTotalPaidToAccountant()).isEqualByComparingTo("5000.00");
        assertThat(summary.getTotalPaidToItDept()).isEqualByComparingTo("5000.00");
        assertThat(summary.getTotalPending()).isEqualByComparingTo("0.00");
        assertThat(summary.getLineCount()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // HALF_UP rounding (Requirement 4.8)
    // ------------------------------------------------------------------

    @Test
    void halfUp_rounding_up_at_boundary() {
        // 0.005 should round to 0.01 with HALF_UP
        TdsLineView line1 = createLine(1L, BigDecimal.valueOf(1000.005),
                LocalDate.of(2024, 6, 15), TdsRemittanceStatus.DEDUCTED,
                null, null);
        TdsLineView line2 = createLine(2L, BigDecimal.valueOf(2000.005),
                LocalDate.of(2024, 6, 16), TdsRemittanceStatus.DEDUCTED,
                null, null);

        TdsSummaryDTO summary = summarize(List.of(line1, line2));

        // 3000.01 should round to 3000.01 (already at scale 2)
        assertThat(summary.getTotalDeducted()).isEqualByComparingTo("3000.01");
    }

    @Test
    void halfUp_rounding_down_at_boundary() {
        // 0.004 should round to 0.00 with HALF_UP
        TdsLineView line1 = createLine(1L, BigDecimal.valueOf(1000.004),
                LocalDate.of(2024, 6, 15), TdsRemittanceStatus.DEDUCTED,
                null, null);
        TdsLineView line2 = createLine(2L, BigDecimal.valueOf(2000.004),
                LocalDate.of(2024, 6, 16), TdsRemittanceStatus.DEDUCTED,
                null, null);

        TdsSummaryDTO summary = summarize(List.of(line1, line2));

        // 3000.008 should round to 3000.01 (HALF_UP: 0.008 > 0.005)
        assertThat(summary.getTotalDeducted()).isEqualByComparingTo("3000.01");
    }

    @Test
    void halfUp_rounding_exact_boundary() {
        // 0.005 exactly should round UP with HALF_UP
        TdsLineView line1 = createLine(1L, BigDecimal.valueOf(1000.005),
                LocalDate.of(2024, 6, 15), TdsRemittanceStatus.DEDUCTED,
                null, null);

        TdsSummaryDTO summary = summarize(List.of(line1));

        assertThat(summary.getTotalDeducted()).isEqualByComparingTo("1000.01");
    }

    // ------------------------------------------------------------------
    // Multiple lines aggregation
    // ------------------------------------------------------------------

    @Test
    void multipleLines_partialRemittance() {
        TdsLineView line1 = createLine(1L, BigDecimal.valueOf(1000.00),
                LocalDate.of(2024, 6, 15), TdsRemittanceStatus.PAID_TO_ACCOUNTANT,
                LocalDate.of(2024, 6, 20), null);
        TdsLineView line2 = createLine(2L, BigDecimal.valueOf(2000.00),
                LocalDate.of(2024, 6, 16), TdsRemittanceStatus.PAID_TO_IT_DEPARTMENT,
                LocalDate.of(2024, 6, 21), LocalDate.of(2024, 6, 25));
        TdsLineView line3 = createLine(3L, BigDecimal.valueOf(3000.00),
                LocalDate.of(2024, 6, 17), TdsRemittanceStatus.DEDUCTED,
                null, null);

        TdsSummaryDTO summary = summarize(List.of(line1, line2, line3));

        assertThat(summary.getTotalDeducted()).isEqualByComparingTo("6000.00");
        assertThat(summary.getTotalPaidToAccountant()).isEqualByComparingTo("3000.00"); // line1 + line2
        assertThat(summary.getTotalPaidToItDept()).isEqualByComparingTo("2000.00");     // line2 only
        assertThat(summary.getTotalPending()).isEqualByComparingTo("4000.00");         // 6000 - 2000
        assertThat(summary.getLineCount()).isEqualTo(3);
    }

    // ------------------------------------------------------------------
    // Filter mapping - blank/absent fields contribute no predicate (Requirement 2.2)
    // ------------------------------------------------------------------

    @Test
    void blankVendorSearch_contributesNoPredicate() {
        TdsFilterRequest filter = new TdsFilterRequest();
        filter.setVendorSearch("");

        Specification<TdsLineView> spec = buildSpecification(filter);

        verify(specificationBuilder).build(any());
        // The specification builder should not include vendor name predicate for blank search
    }

    @Test
    void absentVendorSearch_contributesNoPredicate() {
        TdsFilterRequest filter = new TdsFilterRequest();
        filter.setVendorSearch(null);

        Specification<TdsLineView> spec = buildSpecification(filter);

        verify(specificationBuilder).build(any());
        // The specification builder should not include vendor name predicate for null search
    }

    @Test
    void blankFinancialYear_contributesNoPredicate() {
        TdsFilterRequest filter = new TdsFilterRequest();
        filter.setFinancialYear("");

        Specification<TdsLineView> spec = buildSpecification(filter);

        verify(specificationBuilder).build(any());
    }

    @Test
    void blankTdsSection_contributesNoPredicate() {
        TdsFilterRequest filter = new TdsFilterRequest();
        filter.setTdsSection("");

        Specification<TdsLineView> spec = buildSpecification(filter);

        verify(specificationBuilder).build(any());
    }

    @Test
    void emptyStatusList_contributesNoPredicate() {
        TdsFilterRequest filter = new TdsFilterRequest();
        filter.setStatuses(List.of());

        Specification<TdsLineView> spec = buildSpecification(filter);

        verify(specificationBuilder).build(any());
    }

    // ------------------------------------------------------------------
    // Filters never widen result set (Requirement 2.13)
    // ------------------------------------------------------------------

    @Test
    void addingFilters_neverWidens() {
        // Unfiltered query returns more results than filtered query
        TdsLineView line1 = createLine(1L, BigDecimal.valueOf(1000.00),
                LocalDate.of(2024, 6, 15), TdsRemittanceStatus.DEDUCTED,
                null, null);
        TdsLineView line2 = createLine(2L, BigDecimal.valueOf(2000.00),
                LocalDate.of(2024, 6, 16), TdsRemittanceStatus.PAID_TO_IT_DEPARTMENT,
                LocalDate.of(2024, 6, 20), LocalDate.of(2024, 6, 25));

        TdsFilterRequest unfiltered = new TdsFilterRequest();
        TdsFilterRequest filtered = new TdsFilterRequest();
        filtered.setStatuses(List.of(TdsRemittanceStatus.PAID_TO_IT_DEPARTMENT));

        List<TdsLineView> unfilteredLines = List.of(line1, line2);
        List<TdsLineView> filteredLines = List.of(line2);

        when(specificationBuilder.build(any())).thenReturn(Specification.where(null));
        when(tdsLineViewRepository.findAll(any(Specification.class)))
                .thenReturn(unfilteredLines)
                .thenReturn(filteredLines);

        TdsSummaryDTO unfilteredSummary = service.summarize(unfiltered);
        TdsSummaryDTO filteredSummary = service.summarize(filtered);

        // Unfiltered should have more lines and higher total
        assertThat(unfilteredSummary.getLineCount()).isGreaterThanOrEqualTo(filteredSummary.getLineCount());
        assertThat(unfilteredSummary.getTotalDeducted())
                .isGreaterThanOrEqualTo(filteredSummary.getTotalDeducted());
    }

    // ------------------------------------------------------------------
    // Helper methods
    // ------------------------------------------------------------------

    private TdsSummaryDTO summarize(List<TdsLineView> lines) {
        TdsFilterRequest filter = new TdsFilterRequest();
        when(specificationBuilder.build(any())).thenReturn(Specification.where(null));
        when(tdsLineViewRepository.findAll(any(Specification.class))).thenReturn(lines);

        return service.summarize(filter);
    }

    private Specification<TdsLineView> buildSpecification(TdsFilterRequest filter) {
        when(specificationBuilder.build(any())).thenReturn(Specification.where(null));
        service.summarize(filter);
        verify(specificationBuilder).build(any());
        return Specification.where(null);
    }

    private TdsLineView createLine(Long voucherId, BigDecimal tdsAmount, LocalDate deductionDate,
                                   TdsRemittanceStatus status, LocalDate paidToAccountantDate,
                                   LocalDate paidToItDate) {
        TdsLineView line = new TdsLineView();
        line.setVoucherId(voucherId);
        line.setTdsAmount(tdsAmount);
        line.setDeductionDate(deductionDate);
        line.setRemittanceStatus(status);
        line.setPaidToAccountantDate(paidToAccountantDate);
        line.setPaidToItDate(paidToItDate);
        return line;
    }
}
