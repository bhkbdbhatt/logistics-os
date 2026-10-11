package io.logisticsos.outbox;

/**
 * Publishes one outbox row (Kafka producer, HTTP partner push, ...). Throwing signals retryable failure;
 * dispatching must be idempotent because a crash after publish but before commit leads to a replay.
 */
@FunctionalInterface
public interface OutboxDispatcher {

    void dispatch(OutboxRecord record);
}