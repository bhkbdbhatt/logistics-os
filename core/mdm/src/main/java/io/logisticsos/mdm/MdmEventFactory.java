package io.logisticsos.mdm;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.outbox.OutboxRecord;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.avro.generic.GenericRecord;

/**
 * Maps an {@link MdmPublisher} envelope to an {@link OutboxRecord} and back, so the transactional
 * outbox (ADR-0007/0021) can store an event whose bytes are reconstructed byte-for-byte by the relay.
 * Every envelope field that is not derivable from the outbox row's own columns is stored as a header.
 */
public final class MdmEventFactory {

    static final String H_EVENT_ID = "envelope.eventId";
    static final String H_EVENT_TYPE = "envelope.eventType";
    static final String H_EVENT_VERSION = "envelope.eventVersion";
    static final String H_TENANT_ID = "envelope.tenantId";
    static final String H_OCCURRED_AT = "envelope.occurredAt";
    static final String H_RECORDED_AT = "envelope.recordedAt";
    static final String H_PRODUCER = "envelope.producer";
    static final String H_CORRELATION_ID = "envelope.correlationId";
    static final String H_CAUSATION_ID = "envelope.causationId";
    static final String H_TRACEPARENT = "envelope.traceparent";
    static final String H_IDEMPOTENCY_KEY = "envelope.idempotencyKey";
    static final String H_SCHEMA_SUBJECT = "envelope.schemaSubject";
    static final String H_SOURCE_SYSTEM = "envelope.sourceSystem";
    static final String H_PARTITION_SEQUENCE = "envelope.partitionSequence";

    private final MdmPublisher publisher;

    public MdmEventFactory(MdmPublisher publisher) {
        this.publisher = publisher;
    }

    public OutboxRecord toOutbox(MdmEntities entity, MdmRegistry.Entry entry, GenericRecord record,
            boolean active, String sourceSystem) {
        EventEnvelope envelope = publisher.buildEnvelope(entity, entry, record, active, sourceSystem);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(H_EVENT_ID, envelope.eventId());
        headers.put(H_EVENT_TYPE, envelope.eventType());
        headers.put(H_EVENT_VERSION, Integer.toString(envelope.eventVersion()));
        headers.put(H_TENANT_ID, envelope.tenantId());
        headers.put(H_OCCURRED_AT, Long.toString(envelope.occurredAt()));
        headers.put(H_RECORDED_AT, Long.toString(envelope.recordedAt()));
        headers.put(H_PRODUCER, envelope.producer());
        putIfPresent(headers, H_CORRELATION_ID, envelope.correlationId());
        putIfPresent(headers, H_CAUSATION_ID, envelope.causationId());
        putIfPresent(headers, H_TRACEPARENT, envelope.traceparent());
        headers.put(H_IDEMPOTENCY_KEY, envelope.idempotencyKey());
        headers.put(H_SCHEMA_SUBJECT, envelope.schemaSubject());
        putIfPresent(headers, H_SOURCE_SYSTEM, envelope.sourceSystem());
        headers.put(H_PARTITION_SEQUENCE, Long.toString(envelope.partitionSequence()));
        return OutboxRecord.builder()
                .id(envelope.eventId())
                .dedupeKey(envelope.idempotencyKey())
                .aggregateType(envelope.aggregateType())
                .aggregateId(envelope.aggregateId())
                .aggregateVersion(envelope.aggregateVersion())
                .topic(entity.topic())
                .partitionKey(envelope.aggregateId())
                .headers(headers)
                .payload(envelope.payload())
                .createdAtMs(envelope.recordedAt())
                .build();
    }

    private static void putIfPresent(Map<String, String> headers, String key, String value) {
        if (value != null) {
            headers.put(key, value);
        }
    }
}
