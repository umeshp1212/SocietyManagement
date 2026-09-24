package com.society.module.tds.service;

import com.society.common.PagedResponse;
import com.society.enums.TdsRemittanceStatus;
import com.society.exception.BusinessException;
import com.society.module.tds.dto.TdsFilterRequest;
import com.society.module.tds.dto.TdsLineDTO;
import com.society.module.tds.dto.TdsSummaryDTO;
import com.society.module.tds.entity.TdsLineView;
import com.society.module.tds.mapper.TdsMapper;
import com.society.module.tds.repository.TdsLineViewRepository;
import com.society.module.tds.specification.TdsSpecificationBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Default {@link TdsService} implementation.
 *
 * <p>Validates the incoming filter and pagination parameters before any query runs, builds the
 * dynamic query via {@link TdsSpecificationBuilder}, pages the {@code tds_line_view} read model
 * through {@link TdsLineViewRepository}, and maps each row to a {@link TdsLineDTO} inside the
 * shared {@link PagedResponse} envelope. Mirrors the read-only orchestration style of
 * {@code TransactionService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TdsServiceImpl implements TdsService {

    /** Default page number when none is supplied (Req 3.2). */
    private static final int DEFAULT_PAGE_NUMBER = 0;

    /** Default page size when none is supplied (Req 3.3). */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** Lower bound (inclusive) for an accepted page size (Req 3.4). */
    private static final int MIN_PAGE_SIZE = 1;

    /** Upper bound (inclusive) for an accepted page size (Req 3.4). */
    private static final int MAX_PAGE_SIZE = 200;

    /** Maximum allowed vendor-search term length (Req 2.8, 2.15). */
    private static final int MAX_VENDOR_SEARCH_LENGTH = 100;

    /** Scale applied to every monetary total in the summary (Req 4.8). */
    private static final int MONEY_SCALE = 2;

    private final TdsSpecificationBuilder specificationBuilder;
    private final TdsLineViewRepository tdsLineViewRepository;

    /**
     * {@inheritDoc}
     *
     * <p>Order of operations (all guards run before any query is built, so a rejected request
     * leaves stored data untouched):
     * <ol>
     *   <li>validate the filter (malformed values, inverted date ranges) — Req 2.15, 2.16;</li>
     *   <li>resolve/validate pagination (defaults page 0 / size 20; reject negative page or size
     *       outside 1–200) — Req 3.2, 3.3, 3.4, 3.5;</li>
     *   <li>build the specification, page the read model ordered by deduction date descending,
     *       and map to {@link TdsLineDTO} — Req 1.1, 2.1, 3.1, 3.6.</li>
     * </ol>
     */
    @Override
    public PagedResponse<TdsLineDTO> listTdsLines(TdsFilterRequest filter, Pageable pageable) {
        TdsFilterRequest effectiveFilter = filter != null ? filter : new TdsFilterRequest();

        validateFilter(effectiveFilter);

        PageRequest pageRequest = resolvePageRequest(pageable);

        Specification<TdsLineView> specification = specificationBuilder.build(effectiveFilter);

        Page<TdsLineView> resultPage = tdsLineViewRepository.findAll(specification, pageRequest);

        List<TdsLineDTO> content = resultPage.getContent().stream()
                .map(TdsMapper::toLineDTO)
                .collect(Collectors.toList());

        return PagedResponse.<TdsLineDTO>builder()
                .content(content)
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .last(resultPage.isLast())
                .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Aggregates over the SAME specification {@link #listTdsLines} pages, so the totals always
     * reflect exactly the rows the list returns for a given filter (Req 4.1, 4.7):
     * <ol>
     *   <li>validate the filter with the same guards as the list (inverted date ranges,
     *       vendor-search length) so a rejected request runs no query — Req 2.15, 2.16;</li>
     *   <li>build the specification via {@link TdsSpecificationBuilder} and fetch the full
     *       filtered set from the read model (no pagination);</li>
     *   <li>fold the {@code tdsAmount} of each line into the totals, classifying by
     *       {@link TdsRemittanceStatus} — Req 4.2, 4.3, 4.4, 4.5, 4.6.</li>
     * </ol>
     *
     * <p>Every monetary total is normalized to scale 2 with {@link RoundingMode#HALF_UP}
     * (Req 4.8). An empty filtered set yields {@code 0.00} totals and a line count of {@code 0}
     * (Req 4.9). Because {@code totalPaidToItDept <= totalDeducted}, {@code totalPending} is
     * always {@code >= 0.00} (Req 4.10).
     */
    @Override
    public TdsSummaryDTO summarize(TdsFilterRequest filter) {
        TdsFilterRequest effectiveFilter = filter != null ? filter : new TdsFilterRequest();

        validateFilter(effectiveFilter);

        Specification<TdsLineView> specification = specificationBuilder.build(effectiveFilter);

        List<TdsLineView> lines = tdsLineViewRepository.findAll(specification);

        BigDecimal totalDeducted = BigDecimal.ZERO;
        BigDecimal totalPaidToAccountant = BigDecimal.ZERO;
        BigDecimal totalPaidToItDept = BigDecimal.ZERO;

        for (TdsLineView line : lines) {
            BigDecimal amount = line.getTdsAmount() != null ? line.getTdsAmount() : BigDecimal.ZERO;
            TdsRemittanceStatus status = line.getRemittanceStatus();

            totalDeducted = totalDeducted.add(amount);

            // Paid-to-accountant is inclusive of the later PAID_TO_IT_DEPARTMENT stage (Req 4.3).
            if (status == TdsRemittanceStatus.PAID_TO_ACCOUNTANT
                    || status == TdsRemittanceStatus.PAID_TO_IT_DEPARTMENT) {
                totalPaidToAccountant = totalPaidToAccountant.add(amount);
            }
            if (status == TdsRemittanceStatus.PAID_TO_IT_DEPARTMENT) {
                totalPaidToItDept = totalPaidToItDept.add(amount);
            }
        }

        // Pending derives from deducted minus remitted-to-IT; both are non-negative sums and
        // paidToItDept can never exceed deducted, so pending stays >= 0.00 (Req 4.5, 4.10).
        BigDecimal totalPending = totalDeducted.subtract(totalPaidToItDept);

        return TdsSummaryDTO.builder()
                .totalDeducted(scale(totalDeducted))
                .totalPaidToAccountant(scale(totalPaidToAccountant))
                .totalPaidToItDept(scale(totalPaidToItDept))
                .totalPending(scale(totalPending))
                .lineCount(lines.size())
                .build();
    }

    /**
     * Normalizes a monetary total to scale 2 using {@link RoundingMode#HALF_UP} (Req 4.8).
     * On an empty filtered set every total is {@code BigDecimal.ZERO}, which renders as
     * {@code 0.00} at this scale (Req 4.9).
     */
    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Validates the filter before any query is built. On the first invalid value a
     * {@link BusinessException} is thrown that identifies the offending field, so no query runs
     * (Req 2.15, 2.16).
     *
     * <p>Strongly-typed fields on {@link TdsFilterRequest} — {@code LocalDate} dates and the
     * {@link TdsRemittanceStatus} enum list — reject unparseable dates and unrecognized status
     * names at the controller bind boundary. Here we validate the vendor-search length and the
     * two cross-field date-range orderings.
     */
    private void validateFilter(TdsFilterRequest f) {
        // Vendor search 1-100 chars (Req 2.8). Blank contributes no predicate, so only guard length.
        if (StringUtils.hasText(f.getVendorSearch())
                && f.getVendorSearch().length() > MAX_VENDOR_SEARCH_LENGTH) {
            throw new BusinessException("Invalid vendorSearch: term exceeds maximum length of "
                    + MAX_VENDOR_SEARCH_LENGTH + " characters");
        }

        // Deduction date range ordering (Req 2.16). Unparseable dates are rejected at bind time.
        if (f.getDeductionStartDate() != null && f.getDeductionEndDate() != null
                && f.getDeductionStartDate().isAfter(f.getDeductionEndDate())) {
            throw new BusinessException("Inverted deduction date range: start date "
                    + f.getDeductionStartDate() + " is after end date " + f.getDeductionEndDate());
        }

        // Remittance date range ordering (Req 2.16).
        if (f.getRemittanceStartDate() != null && f.getRemittanceEndDate() != null
                && f.getRemittanceStartDate().isAfter(f.getRemittanceEndDate())) {
            throw new BusinessException("Inverted remittance date range: start date "
                    + f.getRemittanceStartDate() + " is after end date " + f.getRemittanceEndDate());
        }
    }

    /**
     * Resolves the effective page request, applying defaults (page 0 / size 20) when no pageable
     * is supplied and rejecting a negative page number or a page size outside 1–200 with a
     * {@link BusinessException} that names the invalid parameter (Req 3.2, 3.3, 3.4, 3.5).
     * Results are always ordered by deduction date descending, with voucher id descending as a
     * stable tie-breaker (Req 2.1).
     */
    private PageRequest resolvePageRequest(Pageable pageable) {
        int page = pageable != null ? pageable.getPageNumber() : DEFAULT_PAGE_NUMBER;
        int size = pageable != null && pageable.isPaged() ? pageable.getPageSize() : DEFAULT_PAGE_SIZE;

        if (page < 0) {
            throw new BusinessException("Invalid pagination parameter 'page': "
                    + page + " (must be zero or greater)");
        }
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new BusinessException("Invalid pagination parameter 'size': "
                    + size + " (must be between " + MIN_PAGE_SIZE + " and " + MAX_PAGE_SIZE + ")");
        }

        return PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("deductionDate"), Sort.Order.desc("voucherId")));
    }
}
