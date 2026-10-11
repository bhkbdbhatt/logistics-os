package io.logisticsos.events;

/**
 * Fluent builder for {@link EventEnvelope}. Defaults follow docs/schemas/common-types.md:
 * <ul>
 *   <li>{@code eventId} -> new ULID</li>
 *   <li>{@code idempotencyKey} -> {@code eventType:aggregateId:aggregateVersion}</li>
 *   <li>{@code recordedAt} -> now (inject a Clock to make deterministic)</li>
 *   <li>{@code correlationId} -> {@code aggregateType:aggregateId} unless set explicitly</li>
 * </ul>
 */
public final class EventEnvelopeBuilder {

    private final long clockMillis;

    private String eventId;
    private String eventType;
    private int eventVersion = 1;
    private String tenantId;
    private String aggregateType;
    private String aggregateId;
    private long aggregateVersion = 1;
    private long partitionSequence = -1;
    private long occurredAt;
    private long recordedAt;
    private String producer;
    private String correlationId;
    private String causationId;
    private String traceparent;
    private String idempotencyKey;
    private String schemaSubject;
    private String sourceSystem;
    private byte[] payload;

    public EventEnvelopeBuilder() {
        this(System.currentTimeMillis());
    }

    /** Deterministic builder for tests. {@code now} seeds occurredAt/recordedAt/eventId defaults. */
    public EventEnvelopeBuilder(long now) {
        this.clockMillis = now;
        this.occurredAt = now;
        this.recordedAt = now;
        this.eventId = Ulid.generate(now);
    }

    public EventEnvelopeBuilder eventId(String eventId) {
        this.eventId = eventId;
        return this;
    }

    public EventEnvelopeBuilder eventType(String eventType) {
        this.eventType = eventType;
        return this;
    }

    public EventEnvelopeBuilder eventVersion(int eventVersion) {
        this.eventVersion = eventVersion;
        return this;
    }

    public EventEnvelopeBuilder tenantId(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    public EventEnvelopeBuilder aggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
        return this;
    }

    public EventEnvelopeBuilder aggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
        return this;
    }

    public EventEnvelopeBuilder aggregateVersion(long aggregateVersion) {
        this.aggregateVersion = aggregateVersion;
        return this;
    }

    public EventEnvelopeBuilder partitionSequence(long partitionSequence) {
        this.partitionSequence = partitionSequence;
        return this;
    }

    public EventEnvelopeBuilder occurredAt(long occurredAt) {
        this.occurredAt = occurredAt;
        return this;
    }

    public EventEnvelopeBuilder recordedAt(long recordedAt) {
        this.recordedAt = recordedAt;
        return this;
    }

    public EventEnvelopeBuilder producer(String producer) {
        this.producer = producer;
        return this;
    }

    public EventEnvelopeBuilder correlationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }

    public EventEnvelopeBuilder causationId(String causationId) {
        this.causationId = causationId;
        return this;
    }

    public EventEnvelopeBuilder traceparent(String traceparent) {
        this.traceparent = traceparent;
        return this;
    }

    public EventEnvelopeBuilder idempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
        return this;
    }

    public EventEnvelopeBuilder schemaSubject(String schemaSubject) {
        this.schemaSubject = schemaSubject;
        return this;
    }

    public EventEnvelopeBuilder sourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
        return this;
    }

    public EventEnvelopeBuilder payload(byte[] payload) {
        this.payload = payload;
        return this;
    }

    public EventEnvelope build() {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalStateException("eventType is required");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("tenantId is required");
        }
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new IllegalStateException("aggregateType is required");
        }
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalStateException("aggregateId is required");
        }
        if (correlationId == null) {
            correlationId = aggregateType + ":" + aggregateId;
        }
        if (idempotencyKey == null) {
            idempotencyKey = EventEnvelope.idempotencyKey(eventType, aggregateId, aggregateVersion);
        }
        if (schemaSubject == null) {
            schemaSubject = eventType + "-value";
        }
        if (producer == null) {
            producer = "unknown@0.0.0";
        }
        return new EventEnvelope(eventId, eventType, eventVersion, tenantId, aggregateType, aggregateId,
                aggregateVersion, partitionSequence, occurredAt, recordedAt, producer, correlationId, causationId,
                traceparent, idempotencyKey, schemaSubject, sourceSystem, payload);
    }
}