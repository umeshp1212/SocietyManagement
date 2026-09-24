package com.society.module.tds.service;

import com.society.module.tds.dto.TdsFilterRequest;

import java.io.IOException;

/**
 * Renders the deducted-TDS report (summary totals plus the filtered line set) as a downloadable
 * PDF, mirroring the vendor ledger PDF export.
 *
 * <p>The report reflects exactly the rows and totals the {@code /tds/lines} and {@code /tds/summary}
 * endpoints return for the same {@link TdsFilterRequest}, ordered by deduction date descending.
 */
public interface TdsReportPdfService {

    /**
     * Generates the TDS deduction &amp; remittance report PDF for the given filter
     * (Requirements 9.1&ndash;9.5).
     *
     * <p>An empty result still yields a valid PDF showing zeroed totals and a "no records" notice
     * (Requirement 9.4). Any generation failure propagates as an {@link IOException}; no partial
     * bytes are returned (Requirement 9.5).
     *
     * @param filter the requested filters (absent/blank values are ignored)
     * @return the rendered PDF as a byte array
     * @throws IOException if the document cannot be generated
     */
    byte[] generateTdsReportPdf(TdsFilterRequest filter) throws IOException;
}
