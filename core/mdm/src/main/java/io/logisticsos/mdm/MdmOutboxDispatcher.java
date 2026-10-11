package io.logisticsos.mdm;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.EventEnvelopeBuilder;
import io.logisticsos.outbox.OutboxDispatcher;
import io.logisticsos.outbox.OutboxRecord;
import java.util.Map;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Reconstructs the {@link EventEnvelope} stored by {@link MdmEventFactory} and sends it on the entity
 * topic keyed by aggregate id. Dispatch must be idempotent (a crash after send but before commit
 * replays), which holds because the outbox dedupes on save and the send is keyed.
 */
public final class MdmOutboxDispatcher implements OutboxDispatcher {

    private final KafkaTemplate<String, EventEnvelope> template;

    public MdmOutboxDispatcher(KafkaTemplate<String, EventEnvelope> template) {
        this.template = template;
    }

    @Override
    public void dispatch(OutboxRecord record) {
        Map<String, String> h = record.headers();
        EventEnvelope envelope = new EventEnvelopeBuilder(parseLong(h.get(MdmEventFactory.H_RECORDED_AT),
                record.createdAtMs()))
                .eventId(h.get(MdmEventFactory.H_EVENT_ID))
                .eventType(h.get(MdmEventFactory.H_EVENT_TYPE))
                .eventVersion(parseInt(h.get(MdmEventFactory.H_EVENT_VERSION), 1))
                .tenantId(h.get(MdmEventFactory.H_TENANT_ID))
                .aggregateType(record.aggregateType())
                .aggregateId(record.aggregateId())
                .aggregateVersion(record.aggregateVersion())
                .partitionSequence(parseLong(h.get(MdmEventFactory.H_PARTITION_SEQUENCE), -1))
                .occurredAt(parseLong(h.get(MdmEventFactory.H_OCCURRED_AT), record.createdAtMs()))
                .recordedAt(parseLong(h.get(MdmEventFactory.H_RECORDED_AT), record.createdAtMs()))
                .producer(h.get(MdmEventFactory.H_PRODUCER))
                .correlationId(h.get(MdmEventFactory.H_CORRELATION_ID))
                .causationId(h.get(MdmEventFactory.H_CAUSATION_ID))
                .traceparent(h.get(MdmEventFactory.H_TRACEPARENT))
                .idempotencyKey(h.get(MdmEventFactory.H_IDEMPOTENCY_KEY))
                .schemaSubject(h.get(MdmEventFactory.H_SCHEMA_SUBJECT))
                .sourceSystem(h.get(MdmEventFactory.H_SOURCE_SYSTEM))
                .payload(record.payload())
                .build();
        try {
            template.send(record.topic(), record.partitionKey(), envelope).get();
        } catch (Exception e) {
            throw new IllegalStateException("mdm outbox dispatch failed for " + record.id(), e);
        }
    }

    private static long parseLong(String value, long fallback) {
        return value == null ? fallback : Long.parseLong(value);
    }

    private static int parseInt(String value, int fallback) {
        return value == null ? fallback : Integer.parseInt(value);
    }
}
