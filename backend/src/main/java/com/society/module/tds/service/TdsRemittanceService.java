package com.society.module.tds.service;

import com.society.common.PagedResponse;
import com.society.module.tds.dto.CreateRemittanceRequest;
import com.society.module.tds.dto.RecordItRemittanceRequest;
import com.society.module.tds.dto.TdsFilterRequest;
import com.society.module.tds.dto.TdsRemittanceDTO;
import org.springframework.data.domain.Pageable;

/**
 * Write-model orchestration for the TDS remittance lifecycle. A batch groups one or more
 * deducted-TDS voucher lines and advances forward-only through the two remittance stages:
 * created directly in {@code PAID_TO_ACCOUNTANT} (stage 1), then advanced to
 * {@code PAID_TO_IT_DEPARTMENT} (stage 2).
 */
public interface TdsRemittanceService {

    /**
     * Stage 1: group the selected deducted-TDS vouchers into a new batch and record the
     * society-to-accountant payment, creating the batch in status {@code PAID_TO_ACCOUNTANT}
     * (Requirement 5).
     *
     * <p>The request is fully validated before anything is persisted; any validation failure
     * raises a {@link com.society.exception.BusinessException} and no batch or line is created.
     *
     * @param request the vouchers to group plus the recorded accountant payment details
     * @return the created batch together with its lines
     */
    TdsRemittanceDTO createBatch(CreateRemittanceRequest request);

    /**
     * Stage 2: advance an existing {@code PAID_TO_ACCOUNTANT} batch to
     * {@code PAID_TO_IT_DEPARTMENT}, recording the IT Department challan (Requirement 6).
     * Implemented by task 8.2.
     */
    TdsRemittanceDTO recordItRemittance(Long remittanceId, RecordItRemittanceRequest request);

    /**
     * Returns a single batch together with its lines, or raises a
     * {@link com.society.exception.ResourceNotFoundException} when no batch matches
     * (Requirement 8.1-8.4). Implemented by task 8.3.
     */
    TdsRemittanceDTO getBatch(Long remittanceId);

    /**
     * Returns a filtered, paginated list of remittance batches (Requirement 8.5-8.7).
     * Implemented by task 8.3.
     */
    PagedResponse<TdsRemittanceDTO> listBatches(TdsFilterRequest filter, Pageable pageable);
}
