package io.logisticsos.idempotency;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Thread-safe in-memory {@link DedupeStore} with TTL expiry per key. Suitable for dev, tests and
 * single-instance services; distributed deployments use Redis or a DB-backed store.
 */
public final class InMemoryDedupeStore implements DedupeStore {

    private static final class Entry {
        boolean processing = true;
        String resultRef;
        long expiresAtMillis;
    }

    private final Map<String, Entry> entries = new HashMap<>();
    private final long ttlMillis;

    public InMemoryDedupeStore(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        this.ttlMillis = ttl.toMillis();
    }

    @Override
    public synchronized boolean claim(IdempotencyKey key) {
        long now = System.currentTimeMillis();
        Entry existing = entries.get(key.value());
        if (existing == null || existing.expiresAtMillis < now) {
            Entry fresh = new Entry();
            fresh.expiresAtMillis = now + ttlMillis;
            entries.put(key.value(), fresh);
            return true;
        }
        return false;
    }

    @Override
    public synchronized void recordOutcome(IdempotencyKey key, String resultRef) {
        long now = System.currentTimeMillis();
        Entry entry = entries.get(key.value());
        if (entry == null) {
            entry = new Entry();
            entries.put(key.value(), entry);
        }
        entry.processing = false;
        entry.resultRef = resultRef;
        entry.expiresAtMillis = now + ttlMillis;
    }

    @Override
    public synchronized void release(IdempotencyKey key) {
        Entry entry = entries.get(key.value());
        if (entry != null && entry.processing) {
            entries.remove(key.value());
        }
    }

    @Override
    public synchronized boolean isProcessed(IdempotencyKey key) {
        Entry entry = entries.get(key.value());
        return entry != null && !entry.processing && entry.expiresAtMillis >= System.currentTimeMillis();
    }

    @Override
    public synchronized Optional<String> resultRef(IdempotencyKey key) {
        Entry entry = entries.get(key.value());
        if (entry == null || entry.processing || entry.expiresAtMillis < System.currentTimeMillis()) {
            return Optional.empty();
        }
        return Optional.ofNullable(entry.resultRef);
    }
}