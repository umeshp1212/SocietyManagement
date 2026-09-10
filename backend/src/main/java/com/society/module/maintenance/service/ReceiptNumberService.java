package com.society.module.maintenance.service;

import com.society.module.maintenance.entity.ReceiptSequence;
import com.society.module.maintenance.repository.MaintenancePaymentRepository;
import com.society.module.maintenance.repository.ReceiptSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
 * <p>Runs in the CALLER's transaction ({@link Propagation#REQUIRED}). This is deliberate and
 * important: a single business transaction can allocate several receipts (e.g. the Cashfree/
 * Razorpay two-pass loop that pays principal then interest across bills, or overpayment
 * re-application). The {@code existsByReceiptNumber} uniqueness probe below must be able to see
 * the payments this same transaction has already inserted but not yet committed. Hibernate
 * auto-flushes those pending inserts before running the query, so the probe sees them and the
 * counter advances past them.
 *
 * <p>A previous attempt used {@link Propagation#REQUIRES_NEW} to commit each number
 * independently. That was WRONG: it ran the probe on a separate transaction/connection under
 * READ_COMMITTED, so it could not see the outer transaction's uncommitted payment inserts. Two
 * allocations in the same business transaction then both derived the same number, the second
 * INSERT hit {@code uk_payment_receipt_number}, and the whole outer transaction rolled back —
 * leaving {@code receipt_sequences.last_number} ahead of zero committed payments (exactly the
 * observed {@code Duplicate entry 'RCP-YYYYMM-00001'} failure).
 *
 * <p>Cross-transaction concurrency is handled by the pessimistic write lock on the single
 * per-period {@code receipt_sequences} row ({@code SELECT ... FOR UPDATE}), which serialises
 * concurrent allocators. If the caller rolls back, the counter increment rolls back with it, so
 * no number is wasted and none is reused. The DB unique constraint remains the final backstop.
 */
@Service
@RequiredArgsConstructor
public class ReceiptNumberService {

    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    private final ReceiptSequenceRepository sequenceRepository;
    private final MaintenancePaymentRepository paymentRepository;

    /** Safety cap so a corrupt sequence can never spin forever. */
    private static final int MAX_PROBE = 100_000;

    /**
     * Reserve and return the next receipt number for the current month.
     * MUST be called within an active transaction (it is, from all payment paths).
     *
     * <p>Self-healing: the per-period counter can drift from the actual receipts already in
     * {@code maintenance_payments} (e.g. receipts created by other generators, imported/seed
     * data, or pre-existing rows). To avoid a duplicate-key failure we advance the counter past
     * any number that is already taken, then persist the counter at the value we hand out.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public String next() {
        String period = LocalDate.now().format(PERIOD_FMT);

        // Pessimistic row lock serialises concurrent allocators for this period.
        ReceiptSequence sequence = sequenceRepository.findByPeriodForUpdate(period)
                .orElseGet(() -> sequenceRepository.saveAndFlush(
                        ReceiptSequence.builder().period(period).lastNumber(0).build()));

        int candidate = sequence.getLastNumber();
        String receiptNumber;
        int probes = 0;
        do {
            candidate++;
            receiptNumber = String.format("RCP-%s-%05d", period, candidate);
            if (++probes > MAX_PROBE) {
                throw new IllegalStateException(
                        "Unable to allocate a unique receipt number for period " + period);
            }
            // existsByReceiptNumber triggers a flush of the caller's pending payment inserts
            // first, so a number already handed out earlier in THIS transaction is seen as taken
            // and we advance past it. Running in the caller's transaction (REQUIRED) is what
            // makes those uncommitted rows visible to this query.
        } while (paymentRepository.existsByReceiptNumber(receiptNumber));

        // Advance the counter in the caller's transaction. saveAndFlush pushes the UPDATE (while
        // still holding the row lock) so a subsequent allocation in the same transaction reads
        // the new value and never re-derives it. If the caller rolls back, this rolls back too.
        sequence.setLastNumber(candidate);
        sequenceRepository.saveAndFlush(sequence);

        return receiptNumber;
    }
}
