package com.society.module.maintenance.service;

import com.society.module.maintenance.entity.ReceiptSequence;
import com.society.module.maintenance.repository.ReceiptSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Generates unique, gap-free maintenance receipt numbers of the form
 * {@code RCP-YYYYMM-NNNNN} (e.g. RCP-202609-00001).
 *
 * <p>Concurrency safety comes from a pessimistic write lock on the single per-period row
 * in {@code receipt_sequences} (same approach as voucher numbering). This replaces the old
 * {@code RCP-yyyyMMdd-<random 4 digits>} scheme, which could collide and had no uniqueness
 * guarantee.
 *
 * <p>Runs in the CALLER's transaction (default propagation). The row lock is held only for
 * the brief window until the caller commits; if the caller rolls back (e.g. an
 * optimistic-lock retry), the increment rolls back too, so no number is wasted and none is
 * ever reused.
 */
@Service
@RequiredArgsConstructor
public class ReceiptNumberService {

    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    private final ReceiptSequenceRepository sequenceRepository;

    /**
     * Reserve and return the next receipt number for the current month.
     * MUST be called within an active transaction (it is, from all payment paths).
     */
    @Transactional
    public String next() {
        String period = LocalDate.now().format(PERIOD_FMT);

        ReceiptSequence sequence = sequenceRepository.findByPeriodForUpdate(period)
                .orElseGet(() -> sequenceRepository.save(
                        ReceiptSequence.builder().period(period).lastNumber(0).build()));

        int nextNumber = sequence.getLastNumber() + 1;
        sequence.setLastNumber(nextNumber);
        sequenceRepository.save(sequence);

        return String.format("RCP-%s-%05d", period, nextNumber);
    }
}
