package com.society.module.tds.service;

import com.society.common.PagedResponse;
import com.society.module.tds.dto.TdsFilterRequest;
import com.society.module.tds.dto.TdsLineDTO;
import com.society.module.tds.dto.TdsSummaryDTO;
import org.springframework.data.domain.Pageable;

/**
 * Read-model orchestration for the deducted-TDS list and its summary totals.
 *
 * <p>Both operations run over the same {@code tds_line_view} projection filtered by the same
 * {@link com.society.module.tds.specification.TdsSpecificationBuilder}, so the summary always
 * reflects exactly the rows the list returns for a given filter.
 */
public interface TdsService {

    /**
     * Returns a filtered, paginated list of deducted-TDS lines across all vendors, ordered by
     * deduction date descending (Requirements 1.1, 2.1, 3.1).
     *
     * <p>The filter is validated before any query is built; an invalid filter value or an
     * inverted date range raises a {@link com.society.exception.BusinessException} and no query
     * runs. Invalid pagination parameters (negative page, or page size outside 1–200) are
     * likewise rejected.
     *
     * @param filter   the requested filters (absent/blank values are ignored)
     * @param pageable the requested page; {@code null} means page 0 / size 20
     * @return the requested page wrapped in a {@link PagedResponse}
     */
    PagedResponse<TdsLineDTO> listTdsLines(TdsFilterRequest filter, Pageable pageable);

    /**
     * Aggregated totals over the same filtered set the list returns (Requirement 4).
     * Implemented by task 7.2.
     */
    TdsSummaryDTO summarize(TdsFilterRequest filter);
}
