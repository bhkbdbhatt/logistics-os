package io.logisticsos.outbox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Thread-safe in-memory {@link OutboxRepository} for tests, dev and single-node use. */
public final class InMemoryOutboxRepository implements OutboxRepository {

    private final Map<String, OutboxRecord> byId = new LinkedHashMap<>();
    private final Map<String, String> byDedupeKey = new LinkedHashMap<>();
    private final int maxAttempts;

    public InMemoryOutboxRepository() {
        this(3);
    }

    public InMemoryOutboxRepository(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.maxAttempts = maxAttempts;
    }

    @Override
    public synchronized boolean save(OutboxRecord record) {
        if (byDedupeKey.containsKey(record.dedupeKey())) {
            return false;
        }
        OutboxRecord stored = new OutboxRecord(record.id(), record.dedupeKey(), record.aggregateType(),
                record.aggregateId(), record.aggregateVersion(), record.topic(), record.partitionKey(),
                record.headers(), record.payload(), record.createdAtMs(), record.state(), record.attemptCount());
        byId.put(stored.id(), stored);
        byDedupeKey.put(stored.dedupeKey(), stored.id());
        return true;
    }

    @Override
    public synchronized List<OutboxRecord> pollPending(int batchSize) {
        List<OutboxRecord> result = new ArrayList<>();
        for (OutboxRecord record : byId.values()) {
            if (record.state() == OutboxState.PENDING) {
                result.add(record);
                if (result.size() >= batchSize) {
                    break;
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public synchronized void markDispatched(String recordId, boolean success) {
        OutboxRecord record = byId.get(recordId);
        if (record == null) {
            return;
        }
        int attempts = record.attemptCount() + (success ? 0 : 1);
        OutboxState newState;
        if (success) {
            newState = OutboxState.DISPATCHED;
        } else {
            newState = attempts >= maxAttempts ? OutboxState.FAILED : OutboxState.PENDING;
        }
        byId.put(recordId, new OutboxRecord(record.id(), record.dedupeKey(), record.aggregateType(),
                record.aggregateId(), record.aggregateVersion(), record.topic(), record.partitionKey(),
                record.headers(), record.payload(), record.createdAtMs(), newState, attempts));
    }

    @Override
    public synchronized Optional<OutboxRecord> findById(String recordId) {
        return Optional.ofNullable(byId.get(recordId));
    }

    @Override
    public synchronized long countPending() {
        return byId.values().stream().filter(r -> r.state() == OutboxState.PENDING).count();
    }
}