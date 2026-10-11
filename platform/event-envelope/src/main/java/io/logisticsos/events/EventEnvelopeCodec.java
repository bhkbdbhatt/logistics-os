package io.logisticsos.events;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;

/**
 * Avro binary codec for {@link EventEnvelope} against a supplied schema (the single source of truth is
 * contracts/avro/io/logisticsos/events/EventEnvelope.avsc). Payload bytes pass through untouched.
 */
public final class EventEnvelopeCodec {

    private EventEnvelopeCodec() {
    }

    public static byte[] encode(EventEnvelope envelope, Schema schema) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(512);
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            new GenericDatumWriter<GenericRecord>(schema).write(toGeneric(envelope, schema), encoder);
            encoder.flush();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to encode EventEnvelope", ex);
        }
    }

    public static EventEnvelope decode(byte[] bytes, Schema schema) {
        try {
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(bytes, null);
            GenericRecord record = new GenericDatumReader<GenericRecord>(schema).read(null, decoder);
            return fromGeneric(record);
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to decode EventEnvelope", ex);
        }
    }

    private static GenericRecord toGeneric(EventEnvelope e, Schema schema) {
        GenericRecord record = new GenericData.Record(schema);
        put(record, "eventId", e.eventId());
        put(record, "eventType", e.eventType());
        put(record, "eventVersion", e.eventVersion());
        put(record, "tenantId", e.tenantId());
        put(record, "aggregateType", e.aggregateType());
        put(record, "aggregateId", e.aggregateId());
        put(record, "aggregateVersion", e.aggregateVersion());
        put(record, "partitionSequence", e.partitionSequence());
        put(record, "occurredAt", e.occurredAt());
        put(record, "recordedAt", e.recordedAt());
        put(record, "producer", e.producer());
        put(record, "correlationId", e.correlationId());
        put(record, "causationId", e.causationId());
        put(record, "traceparent", e.traceparent());
        put(record, "idempotencyKey", e.idempotencyKey());
        put(record, "schemaSubject", e.schemaSubject());
        put(record, "sourceSystem", e.sourceSystem());
        put(record, "payload", e.payload() == null ? ByteBuffer.allocate(0) : ByteBuffer.wrap(e.payload()));
        return record;
    }

    private static EventEnvelope fromGeneric(GenericRecord r) {
        byte[] payload = value(r, "payload") instanceof ByteBuffer buf
                ? toBytes(buf)
                : new byte[0];
        return new EventEnvelope(
                string(r, "eventId"), string(r, "eventType"), intVal(r, "eventVersion"),
                string(r, "tenantId"), string(r, "aggregateType"), string(r, "aggregateId"),
                longVal(r, "aggregateVersion"), longVal(r, "partitionSequence"),
                longVal(r, "occurredAt"), longVal(r, "recordedAt"),
                string(r, "producer"), string(r, "correlationId"), string(r, "causationId"),
                string(r, "traceparent"), string(r, "idempotencyKey"), string(r, "schemaSubject"),
                string(r, "sourceSystem"), payload);
    }

    private static void put(GenericRecord record, String name, Object value) {
        record.put(name, value == null
                && record.getSchema().getField(name) != null
                && record.getSchema().getField(name).schema().getType() == Schema.Type.UNION
                ? null : value);
    }

    private static String string(GenericRecord r, String name) {
        Object v = value(r, name);
        return v == null ? null : v.toString();
    }

    private static int intVal(GenericRecord r, String name) {
        return ((Number) value(r, name)).intValue();
    }

    private static long longVal(GenericRecord r, String name) {
        return ((Number) value(r, name)).longValue();
    }

    private static Object value(GenericRecord r, String name) {
        return r.get(name);
    }

    private static byte[] toBytes(ByteBuffer buffer) {
        byte[] out = new byte[buffer.remaining()];
        buffer.duplicate().get(out);
        return out;
    }
}