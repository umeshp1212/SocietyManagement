package com.society.module.transaction.service;

import com.society.exception.BusinessException;
import com.society.exception.ResourceNotFoundException;
import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentMode;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentStatus;
import com.society.module.maintenance.repository.MaintenancePaymentRepository;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.repository.UnitRepository;
import com.society.module.transaction.dto.TransactionDetailDTO;
import com.society.module.transaction.dto.TransactionFilterRequest;
import com.society.module.transaction.specification.TransactionSpecificationBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Example/unit tests for the Transaction read model's filter validation and
 * detail retrieval error reporting ({@link TransactionService}, Subtask 7.8).
 *
 * <p>These are targeted, example-based tests (JUnit 5 + Mockito) — a companion
 * to the property tests in this package. They assert that each invalid filter
 * value raises a {@link BusinessException} whose message <em>names the offending
 * value</em> (so the frontend can echo it back), that no query is built or run
 * when validation fails, and that requesting a non-existent transaction detail
 * raises a not-found error (mapped to HTTP 404 by the global handler).</p>
 *
 * <p>Cases covered:
 * <ul>
 *   <li>Invalid payer type (not OWNER/TENANT) — Req 6.6</li>
 *   <li>Unit-search term over the 50-char bound — Req 6.5</li>
 *   <li>Reference term over the 100-char bound — Req 9.3</li>
 *   <li>Unknown (non-existent) unit id — Req 6.2</li>
 *   <li>Invalid date range (start &gt; end) and pre-query rejection — Req 3.4, 3.5, 7.6</li>
 *   <li>Detail request for a non-existent id — Req 8.6</li>
 *   <li>Recognized payment mode / status accepted — Req 4.3, 5.4 (see note below)</li>
 * </ul>
 *
 * <p><b>Note on payment mode / status (Req 4.3, 5.4).</b> On
 * {@link TransactionFilterRequest} these are strongly typed enum fields
 * ({@link PaymentMode} and {@code List<PaymentStatus>}), so an unrecognized name
 * is rejected — and named — at the controller bind boundary (Subtask 8.3), not
 * inside the service. At the service layer the corresponding guarantee is that a
 * <em>recognized</em> mode/status is accepted and flows through to the query; the
 * {@code recognizedPaymentModeAndStatuses_areAccepted} test asserts that positive
 * path so the validation ordering never spuriously rejects a legitimate value.</p>
 *
 * <p>Validates: Requirements 3.4, 3.5, 4.3, 5.4, 6.2, 6.5, 6.6, 7.6, 8.6, 9.3</p>
 */
class TransactionFilterValidationTest {

    /**
     * Settable-scope stub of {@link AccessScopeResolver}: {@link #resolve} is
     * overridden to return a chosen scope so the real service validation branch
     * runs without seeding users/owners/tenants.
     */
    private static final class SettableScopeResolver extends AccessScopeResolver {
        private AccessScope scope = AccessScope.societyWideScope();

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

    private SettableScopeResolver scopeResolver;
    private TransactionSpecificationBuilder specificationBuilder;
    private MaintenancePaymentRepository paymentRepository;
    private UnitRepository unitRepository;
    private TransactionService service;

    private final Authentication auth = new UsernamePasswordAuthenticationToken(
            "caller", "n/a", List.of());

    @BeforeEach
    void setUp() {
        scopeResolver = new SettableScopeResolver();
        specificationBuilder = mock(TransactionSpecificationBuilder.class);
        paymentRepository = mock(MaintenancePaymentRepository.class);
        unitRepository = mock(UnitRepository.class);
        service = new TransactionService(
                scopeResolver, specificationBuilder, paymentRepository, unitRepository);
    }

    private TransactionFilterRequest emptyFilter() {
        return new TransactionFilterRequest();
    }

    /** Assert the invalid filter is rejected, its message names {@code value}, and no query ran. */
    private void assertRejectedNamingValue(TransactionFilterRequest filter, String value) {
        assertThatThrownBy(() -> service.listTransactions(filter, 0, null, auth))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(value);
        // Validation runs before any query is built or executed (Req 7.6): no result set produced.
        verifyNoInteractions(specificationBuilder, paymentRepository);
    }

    @SuppressWarnings("unchecked")
    private void stubEmptyQuery() {
        when(specificationBuilder.build(any(), any()))
                .thenReturn((Specification<MaintenancePayment>) mock(Specification.class));
        when(paymentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());
    }

    // ---- invalid payer type (Req 6.6) --------------------------------------

    @Test
    void invalidPayerType_isRejectedAndNamesTheValue() {
        TransactionFilterRequest filter = emptyFilter();
        filter.setPayerType("BUILDER");

        assertRejectedNamingValue(filter, "BUILDER");
    }

    @Test
    void invalidPayerType_messageIdentifiesItAsAPayerType() {
        TransactionFilterRequest filter = emptyFilter();
        filter.setPayerType("landlord");

        assertThatThrownBy(() -> service.listTransactions(filter, 0, null, auth))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("payer type")
                .hasMessageContaining("landlord");
    }

    // ---- unit-search length (Req 6.5) --------------------------------------

    @Test
    void unitSearchOverMax_isRejectedAndNamesTheValue() {
        String tooLong = "A".repeat(51); // bound is 1-50
        TransactionFilterRequest filter = emptyFilter();
        filter.setUnitSearch(tooLong);

        assertRejectedNamingValue(filter, tooLong);
    }

    @Test
    void unitSearchAtMax_isAccepted() {
        String atBound = "A".repeat(50);
        TransactionFilterRequest filter = emptyFilter();
        filter.setUnitSearch(atBound);
        stubEmptyQuery();

        assertThat(service.listTransactions(filter, 0, null, auth).getContent()).isEmpty();
        verify(specificationBuilder).build(any(), any());
    }

    // ---- reference length (Req 9.3) ----------------------------------------

    @Test
    void referenceOverMax_isRejectedAndNamesTheValue() {
        String tooLong = "R".repeat(101); // bound is <= 100
        TransactionFilterRequest filter = emptyFilter();
        filter.setReference(tooLong);

        assertRejectedNamingValue(filter, tooLong);
    }

    @Test
    void referenceAtMax_isAccepted() {
        String atBound = "R".repeat(100);
        TransactionFilterRequest filter = emptyFilter();
        filter.setReference(atBound);
        stubEmptyQuery();

        assertThat(service.listTransactions(filter, 0, null, auth).getContent()).isEmpty();
        verify(specificationBuilder).build(any(), any());
    }

    // ---- unknown unit id (Req 6.2) -----------------------------------------

    @Test
    void nonExistentUnitId_isRejectedAndNamesTheValue() {
        Long unknownId = 4242L;
        when(unitRepository.existsById(unknownId)).thenReturn(false);
        TransactionFilterRequest filter = emptyFilter();
        filter.setUnitId(unknownId);

        assertRejectedNamingValue(filter, unknownId.toString());
    }

    @Test
    void existingUnitId_isAccepted() {
        Long existingId = 7L;
        when(unitRepository.existsById(existingId)).thenReturn(true);
        TransactionFilterRequest filter = emptyFilter();
        filter.setUnitId(existingId);
        stubEmptyQuery();

        assertThat(service.listTransactions(filter, 0, null, auth).getContent()).isEmpty();
        verify(specificationBuilder).build(any(), any());
    }

    // ---- invalid date range (Req 3.4, 3.5, 7.6) ----------------------------

    @Test
    void startAfterEnd_isRejectedAndNamesBothDates() {
        LocalDate start = LocalDate.of(2024, 6, 30);
        LocalDate end = LocalDate.of(2024, 6, 1);
        TransactionFilterRequest filter = emptyFilter();
        filter.setStartDate(start);
        filter.setEndDate(end);

        assertThatThrownBy(() -> service.listTransactions(filter, 0, null, auth))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(start.toString())
                .hasMessageContaining(end.toString());
        verifyNoInteractions(specificationBuilder, paymentRepository);
    }

    // ---- valid enum filters accepted (Req 4.3, 5.4 service-side positive path)

    @Test
    void recognizedPaymentModeAndStatuses_areAccepted() {
        TransactionFilterRequest filter = emptyFilter();
        filter.setPaymentMode(PaymentMode.UPI);
        filter.setStatuses(List.of(PaymentStatus.SUCCESS, PaymentStatus.VERIFIED));
        stubEmptyQuery();

        assertThat(service.listTransactions(filter, 0, null, auth).getContent()).isEmpty();
        verify(specificationBuilder).build(any(), any());
        verify(paymentRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    // ---- detail retrieval for a non-existent id (Req 8.6) ------------------

    @Test
    void nonExistentDetailId_throwsResourceNotFoundNamingTheId() {
        Long missingId = 9999L;
        when(paymentRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTransaction(missingId, auth))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(missingId.toString());
    }

    @Test
    void existingDetailId_returnsDetail() {
        Long id = 5L;
        Unit unit = new Unit();
        unit.setUnitId(500L);
        unit.setUnitNumber("A-101");
        MaintenancePayment payment = MaintenancePayment.builder()
                .paymentId(id)
                .unit(unit)
                .payerType("OWNER")
                .status(PaymentStatus.SUCCESS)
                .paymentMode(PaymentMode.UPI)
                .paymentDate(LocalDate.of(2024, 1, 15))
                .build();
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment));

        TransactionDetailDTO detail = service.getTransaction(id, auth);

        assertThat(detail).isNotNull();
        assertThat(detail.getPaymentId()).isEqualTo(id);
        assertThat(detail.getUnitNumber()).isEqualTo("A-101");
        verify(paymentRepository).findById(id);
    }

    // ---- the list query path must not touch the detail lookup --------------

    @Test
    void listQueryPath_neverCallsFindById() {
        TransactionFilterRequest filter = emptyFilter();
        stubEmptyQuery();

        service.listTransactions(filter, 0, null, auth);

        verify(paymentRepository, never()).findById(any());
    }
}
