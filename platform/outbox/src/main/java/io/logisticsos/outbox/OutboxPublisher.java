package io.logisticsos.outbox;

import java.util.List;

/**
 * Relay: polls the outbox table and hands rows to a dispatcher, applying the state transitions.
 * Safe under replay because the dispatcher is idempotent and the store dedupes on save.
 */
public final class OutboxPublisher {

    private final OutboxRepository repository;

    public OutboxPublisher(OutboxRepository repository) {
        this.repository = repository;
    }

    public PublishSummary dispatchBatch(int batchSize, OutboxDispatcher dispatcher) {
        List<OutboxRecord> batch = repository.pollPending(batchSize);
        int dispatched = 0;
        int failed = 0;
        for (OutboxRecord record : batch) {
            try {
                dispatcher.dispatch(record);
                repository.markDispatched(record.id(), true);
                dispatched++;
            } catch (RuntimeException ex) {
                repository.markDispatched(record.id(), false);
                failed++;
            }
        }
        return new PublishSummary(batch.size(), dispatched, failed, repository.countPending());
    }

    public record PublishSummary(int polled, int dispatched, int failed, long remainingPending) {

        public boolean allFailed() {
            return dispatched == 0 && failed > 0;
        }
    }
}