package io.logisticsos.outbox;

import java.util.List;
import java.util.Optional;

/**
 * Storage contract for the outbox table (ADR-0007). Real services implement this over JDBC in the same
 * local transaction as the state change; {@link InMemoryOutboxRepository} backs tests and dev.
 */
public interface OutboxRepository {

    /**
     * Insert the row. Honor uniqueness of {@code dedupeKey}: return false when a row with the same
     * dedupe key already exists (silent duplicate, not an error).
     */
    boolean save(OutboxRecord record);

    /** Pending rows in chronological creation order, up to {@code batchSize}. */
    List<OutboxRecord> pollPending(int batchSize);

    /**
     * Mark a row dispatched (success=true) or retryable (success=false: attempts++ and FAILED when
     * exhausted, otherwise back to PENDING).
     */
    void markDispatched(String recordId, boolean success);

    Optional<OutboxRecord> findById(String recordId);

    /** Poll diagnostics for lag/backlog SLOs. */
    long countPending();
}