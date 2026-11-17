package com.society.module.transaction.service;

import com.society.common.PagedResponse;
import com.society.exception.BusinessException;
import com.society.exception.ResourceNotFoundException;
import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.maintenance.repository.MaintenancePaymentRepository;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.repository.UnitRepository;
import com.society.module.transaction.dto.TransactionDetailDTO;
import com.society.module.transaction.dto.TransactionFilterRequest;
import com.society.module.transaction.dto.TransactionSummaryDTO;
import com.society.module.transaction.mapper.TransactionMapper;
import com.society.module.transaction.specification.TransactionSpecificationBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-only orchestration for the Transaction Page.
 *
 * <p>Resolves the caller's data access scope, validates the incoming filter
 * values before any query runs, resolves and clamps the effective page size,
 * then delegates the dynamic query to {@link TransactionSpecificationBuilder}
 * and maps the result into the shared {@link PagedResponse} envelope. Detail
 * retrieval enforces the same access scope so members cannot read transactions
 * outside their linked units.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TransactionService {

    /** Recognized payer types (Req 6.6). */
    private static final Set<String> VALID_PAYER_TYPES = Set.of("OWNER", "TENANT");

    /** Default page size for society-wide (administrator) callers (Req 1.4). */
    private static final int ADMIN_DEFAULT_PAGE_SIZE = 25;

    /** Default page size for member callers (Req 2.1). */
    private static final int MEMBER_DEFAULT_PAGE_SIZE = 50;

    /** Lower bound for the effective page size (Req 1.4). */
    private static final int MIN_PAGE_SIZE = 1;

    /** Upper bound for the effective page size (Req 1.4). */
    private static final int MAX_PAGE_SIZE = 100;

    /** Maximum allowed unit-search term length (Req 6.5). */
    private static final int MAX_UNIT_SEARCH_LENGTH = 50;

    /** Maximum allowed reference search term length (Req 9.3). */
    private static final int MAX_REFERENCE_LENGTH = 100;

    private final AccessScopeResolver accessScopeResolver;
    private final TransactionSpecificationBuilder specificationBuilder;
    private final MaintenancePaymentRepository paymentRepository;
    private final UnitRepository unitRepository;

    /**
     * Returns a paginated, filtered, access-scoped list of transactions
     * (Subtask 7.1).
     *
     * <p>Filters are validated before any query is built; an invalid value
     * raises a {@link BusinessException} (naming the offending value) and no
     * query runs. Results are ordered by payment date descending and, within a
     * date, by payment identifier descending.
     *
     * @param filter the requested filters (absent/blank values are ignored)
     * @param page   the zero-based page number
     * @param size   the requested page size, or {@code null} for the role default
     * @param auth   the authenticated caller
     * @return the requested page wrapped in a {@link PagedResponse}
     */
    public PagedResponse<TransactionSummaryDTO> listTransactions(
            TransactionFilterRequest filter, int page, Integer size, Authentication auth) {

        AccessScope scope = accessScopeResolver.resolve(auth);

        validateFilter(filter);

        int effectiveSize = resolveEffectivePageSize(size, scope.societyWide());

        Specification<MaintenancePayment> specification = specificationBuilder.build(scope, filter);

        PageRequest pageRequest = PageRequest.of(page, effectiveSize,
                Sort.by(Sort.Order.desc("paymentDate"), Sort.Order.desc("paymentId")));

        Page<MaintenancePayment> resultPage = paymentRepository.findAll(specification, pageRequest);

        List<TransactionSummaryDTO> content = resultPage.getContent().stream()
                .map(TransactionMapper::toSummary)
                .collect(Collectors.toList());

        return PagedResponse.<TransactionSummaryDTO>builder()
                .content(content)
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .last(resultPage.isLast())
                .build();
    }

    /**
     * Returns the full detail of a single transaction, enforcing access scope
     * (Subtask 7.2).
     *
     * <p>Administrators (society-wide) may read any existing transaction. A
     * member caller is denied when the transaction's unit is outside their
     * scope.
     *
     * @param paymentId the transaction (payment) identifier
     * @param auth      the authenticated caller
     * @return the transaction detail
     * @throws ResourceNotFoundException when no transaction exists for the id (Req 8.6)
     * @throws AccessDeniedException     when a member requests an out-of-scope transaction (Req 8.5, 10.3)
     */
    public TransactionDetailDTO getTransaction(Long paymentId, Authentication auth) {
        MaintenancePayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction", "paymentId", paymentId));

        AccessScope scope = accessScopeResolver.resolve(auth);

        if (!scope.societyWide() && !isWithinScope(payment, scope)) {
            throw new AccessDeniedException("Transaction is not accessible");
        }

        return TransactionMapper.toDetail(payment);
    }

    /**
     * Validates every filter value before any query is built. On the first
     * invalid value a {@link BusinessException} is thrown that names the
     * offending value, so no query runs and stored data is unchanged.
     *
     * <p>Date and enum fields ({@code startDate}/{@code endDate},
     * {@code paymentMode}, {@code statuses}) are strongly typed on
     * {@link TransactionFilterRequest} and therefore reject unparsable dates and
     * unrecognized enum names at the controller bind boundary; here we validate
     * the cross-field date-range ordering plus the string/lookup-based filters.
     */
    private void validateFilter(TransactionFilterRequest filter) {
        // Date range ordering (Req 3.4). Unparsable dates are rejected at bind time (Req 3.5).
        if (filter.getStartDate() != null && filter.getEndDate() != null
                && filter.getStartDate().isAfter(filter.getEndDate())) {
            throw new BusinessException("Invalid date range: start date "
                    + filter.getStartDate() + " is after end date " + filter.getEndDate());
        }

        // Payer type must be OWNER or TENANT (Req 6.6).
        if (filter.getPayerType() != null) {
            String payerType = filter.getPayerType().trim();
            if (!VALID_PAYER_TYPES.contains(payerType.toUpperCase())) {
                throw new BusinessException("Invalid payer type: '" + filter.getPayerType()
                        + "'. Expected one of " + VALID_PAYER_TYPES);
            }
        }

        // Unit id must exist (Req 6.2).
        if (filter.getUnitId() != null && !unitRepository.existsById(filter.getUnitId())) {
            throw new BusinessException("Unit not found: '" + filter.getUnitId() + "'");
        }

        // Unit search length 1-50 (Req 6.5). Blank contributes no filter, so only guard length.
        if (StringUtils.hasText(filter.getUnitSearch())
                && filter.getUnitSearch().length() > MAX_UNIT_SEARCH_LENGTH) {
            throw new BusinessException("Unit search term exceeds maximum length of "
                    + MAX_UNIT_SEARCH_LENGTH + " characters: '" + filter.getUnitSearch() + "'");
        }

        // Reference: trimmed; blank ignored (Req 9.2); length > 100 rejected (Req 9.3).
        if (filter.getReference() != null) {
            String trimmed = filter.getReference().trim();
            if (!trimmed.isEmpty() && trimmed.length() > MAX_REFERENCE_LENGTH) {
                throw new BusinessException("Reference search term exceeds maximum length of "
                        + MAX_REFERENCE_LENGTH + " characters: '" + filter.getReference() + "'");
            }
        }
    }

    /**
     * Resolves the effective page size: the role default (25 admin / 50 member)
     * when no valid size is supplied, then clamped to [1, 100] (Req 1.4, 2.1,
     * Property 13).
     */
    private int resolveEffectivePageSize(Integer requestedSize, boolean societyWide) {
        int roleDefault = societyWide ? ADMIN_DEFAULT_PAGE_SIZE : MEMBER_DEFAULT_PAGE_SIZE;
        int size = requestedSize != null ? requestedSize : roleDefault;
        return Math.max(MIN_PAGE_SIZE, Math.min(MAX_PAGE_SIZE, size));
    }

    /**
     * Whether the payment's unit belongs to the caller's member scope.
     */
    private boolean isWithinScope(MaintenancePayment payment, AccessScope scope) {
        Unit unit = payment.getUnit();
        return unit != null && unit.getUnitId() != null && scope.unitIds().contains(unit.getUnitId());
    }
}
