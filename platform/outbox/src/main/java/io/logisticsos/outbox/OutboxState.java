package io.logisticsos.outbox;

/** Outbox row lifecycle (ADR-0007). */
public enum OutboxState {
    PENDING,
    DISPATCHED,
    FAILED
}