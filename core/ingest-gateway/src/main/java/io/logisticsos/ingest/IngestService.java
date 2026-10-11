package io.logisticsos.ingest;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.EventEnvelopeBuilder;
import io.logisticsos.events.kafka.PayloadAvro;
import io.logisticsos.events.kafka.SchemaResolver;
import io.logisticsos.idempotency.GuardedProcessor;
import io.logisticsos.idempotency.IdempotencyKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;

/**
 * Validates an inbound observation against the canonical payload schema, dedupes it, and publishes
 * a canonical envelope to the obs topic. Owns no data (ADR-0007 discipline: idempotent, exactly-once
 * effect semantics at the consumer of the downstream processor).
 */
public class IngestService {

    private final SchemaResolver schemas;
    private final IngestPublisher publisher;
    private final GuardedProcessor dedupe;

    public IngestService(SchemaResolver schemas, IngestPublisher publisher, GuardedProcessor dedupe) {
        this.schemas = schemas;
        this.publisher = publisher;
        this.dedupe = dedupe;
    }

    public Result ingest(IngestKind kind, String tenantId, String source, String idempotencyKey, String body) {
        var payloadSchema = schemas.resolve(kind.schemaSubject());
        PayloadAvro.validate(body, payloadSchema);
        GenericRecord record = PayloadAvro.jsonToGeneric(body, payloadSchema);

        String bodyHash = sha256Hex(body);
        String aggregateId = naturalKey(record, kind, bodyHash);
        String key = idempotencyKey == null || idempotencyKey.isBlank()
                ? source + ":" + bodyHash
                : idempotencyKey;

        GuardedProcessor.Outcome<EventEnvelope> outcome = dedupe.process(
                new IdempotencyKey(key),
                () -> {
                    long now = System.currentTimeMillis();
                    return new EventEnvelopeBuilder(now)
                            .eventType(kind.payloadType())
                            .tenantId(tenantId)
                            .aggregateType(kind.aggregateType())
                            .aggregateId(aggregateId)
                            .eventVersion(1)
                            .aggregateVersion(1)
                            .producer("ingest-gateway@0.1.0")
                            .sourceSystem(source)
                            .idempotencyKey(key)
                            .schemaSubject(kind.schemaSubject())
                            .payload(PayloadAvro.toBytes(record))
                            .build();
                },
                EventEnvelope::eventId);

        if (outcome.duplicate()) {
            return Result.duplicate(outcome.resultRef().orElse(key));
        }
        publisher.publish(kind.topic(), outcome.value().orElseThrow());
        return Result.accepted(outcome.value().orElseThrow().eventId());
    }

    private static String naturalKey(GenericRecord record, IngestKind kind, String fallback) {
        Object value = record.get(kind.naturalKeyField());
        if (value instanceof Utf8 u) {
            String s = u.toString();
            return s == null || s.isBlank() ? fallback : s;
        }
        return fallback;
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public record Result(String status, String eventId, boolean duplicate) {

        public static Result accepted(String eventId) {
            return new Result("ACCEPTED", eventId, false);
        }

        public static Result duplicate(String eventId) {
            return new Result("DUPLICATE", eventId, true);
        }
    }
}