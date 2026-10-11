package io.logisticsos.idempotency;

import java.util.Optional;

/**
 * Dedupe store backing consumer-side idempotency (docs/schemas/common-types.md 4.2.1).
 * Implementations: RocksDB state store, Redis, or a DB unique-index row — TTL >= topic retention.
 */
public interface DedupeStore {

    /**
     * Atomically claim a key. Claiming wins when the key is absent or its previous entry expired.
     * A duplicate claim means another attempt is processing or already succeeded for this key.
     *
     * @return true when this caller owns the key and must perform the work
     */
    boolean claim(IdempotencyKey key);

    /** Publish the outcome reference after successful processing (resets the TTL). */
    void recordOutcome(IdempotencyKey key, String resultRef);

    /** Abort a claim (rollback path): work failed, allow retry. No-op when the entry was already finalized. */
    void release(IdempotencyKey key);

    /** True when the key was processed successfully (outcome recorded). */
    boolean isProcessed(IdempotencyKey key);

    /** Previously recorded outcome reference, if any. */
    Optional<String> resultRef(IdempotencyKey key);
}