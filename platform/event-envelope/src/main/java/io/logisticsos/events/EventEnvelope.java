package io.logisticsos.events;

import java.util.Arrays;
import java.util.Objects;

/**
 * Universal envelope for all canonical events (see contracts/avro/io/logisticsos/events/EventEnvelope.avsc).
 * Immutable. 'payload' mirrors the Avro {@code bytes} field as a defensive-copy byte[].
 */
public record EventEnvelope(
        String eventId,
        String eventType,
        int eventVersion,
        String tenantId,
        String aggregateType,
        String aggregateId,
        long aggregateVersion,
        long partitionSequence,
        long occurredAt,
        long recordedAt,
        String producer,
        String correlationId,
        String causationId,
        String traceparent,
        String idempotencyKey,
        String schemaSubject,
        String sourceSystem,
        byte[] payload) {

    /**
     * Canonical idempotency key (docs/schemas/common-types.md 4.1):
     * {@code eventType:aggregateId:aggregateVersion}, or the producer key when aggregateVersion is not tracked.
     */
    public static String idempotencyKey(String eventType, String aggregateId, long aggregateVersion) {
        return eventType + ":" + Objects.requireNonNull(aggregateId, "aggregateId") + ":" + aggregateVersion;
    }

    @Override
    public byte[] payload() {
        return payload == null ? null : Arrays.copyOf(payload, payload.length);
    }

    @Override
    public String toString() {
        return "EventEnvelope{eventId=" + eventId + ", eventType=" + eventType + ", tenantId=" + tenantId
                + ", aggregateId=" + aggregateId + ", idempotencyKey=" + idempotencyKey + "}";
    }
}