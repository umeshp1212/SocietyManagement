package com.society.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Runs a unit of work and retries it if it fails with an optimistic-locking conflict.
 *
 * <p>Used to wrap money-mutating operations (applying payments) that do a read-modify-write
 * on a {@code @Version}-guarded entity. When two transactions update the same bill
 * concurrently, the second commit fails with {@link ObjectOptimisticLockingFailureException};
 * retrying re-reads the row at its new version and re-applies the change, so no update is lost
 * and no payment is double-credited.
 *
 * <p>IMPORTANT: the supplied operation MUST open its own transaction (i.e. be a call to a
 * {@code @Transactional} method on a Spring proxy). The retry loop lives OUTSIDE the
 * transaction so each attempt runs in a fresh transaction with a fresh read of the entity.
 * Do not call this from inside an existing transaction.
 */
@Component
@Slf4j
public class OptimisticRetry {

    /** Total attempts (1 initial try + up to maxAttempts-1 retries). */
    private static final int MAX_ATTEMPTS = 4;

    /** Base backoff in millis; grows linearly per attempt to spread out contenders. */
    private static final long BASE_BACKOFF_MS = 25L;

    /**
     * Execute {@code operation}, retrying on optimistic-lock conflicts.
     *
     * @param description short description for logging (e.g. "record offline payment")
     * @param operation   the transactional work to run; must be idempotent-safe to re-run
     * @return the operation's result
     */
    public <T> T execute(String description, Supplier<T> operation) {
        ObjectOptimisticLockingFailureException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return operation.get();
            } catch (ObjectOptimisticLockingFailureException ex) {
                last = ex;
                log.warn("Optimistic lock conflict on '{}' (attempt {}/{}); retrying",
                        description, attempt, MAX_ATTEMPTS);
                if (attempt < MAX_ATTEMPTS) {
                    sleep(BASE_BACKOFF_MS * attempt);
                }
            }
        }
        log.error("Giving up on '{}' after {} attempts due to repeated optimistic-lock conflicts",
                description, MAX_ATTEMPTS);
        throw last;
    }

    /** Convenience overload for operations that return nothing. */
    public void executeVoid(String description, Runnable operation) {
        execute(description, () -> {
            operation.run();
            return null;
        });
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying after optimistic-lock conflict", ie);
        }
    }
}
