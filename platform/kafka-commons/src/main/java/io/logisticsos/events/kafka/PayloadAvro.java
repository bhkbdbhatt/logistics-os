package io.logisticsos.events.kafka;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonDecoder;

/**
 * Payload helpers for the {@code EventEnvelope.payload} {@code bytes} field. Producers map
 * provider JSON onto the canonical payload schema (validation by construction), then encode to
 * binary; consumers decode the payload bytes with the registry-resolved payload schema.
 */
public final class PayloadAvro {

    private PayloadAvro() {
    }

    /** Parse canonical JSON into a generic record against the payload schema (throws on invalid). */
    public static GenericRecord jsonToGeneric(String json, Schema payloadSchema) {
        try {
            JsonDecoder decoder = DecoderFactory.get().jsonDecoder(payloadSchema, json);
            GenericRecord record = new GenericDatumReader<GenericRecord>(payloadSchema).read(null, decoder);
            record.getSchema(); // exercise accessors so impossible schema states surface in tests
            return record;
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid payload for schema " + payloadSchema.getFullName(), ex);
        }
    }

    /** Validate provider JSON against the payload schema without producing an envelope yet. */
    public static void validate(String json, Schema payloadSchema) {
        jsonToGeneric(json, payloadSchema);
    }

    public static byte[] toBytes(GenericRecord record) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(256);
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            new GenericDatumWriter<GenericRecord>(record.getSchema()).write(record, encoder);
            encoder.flush();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to encode payload " + record.getSchema().getFullName(), ex);
        }
    }

    public static byte[] jsonToBytes(String json, Schema payloadSchema) {
        return toBytes(jsonToGeneric(json, payloadSchema));
    }

    /** Decode payload bytes into a generic record (consumer side). */
    public static GenericRecord fromBytes(byte[] payload, Schema payloadSchema) {
        try {
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(payload, null);
            return new GenericDatumReader<GenericRecord>(payloadSchema).read(null, decoder);
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to decode payload " + payloadSchema.getFullName(), ex);
        }
    }

    public static String toJson(byte[] payload, Schema payloadSchema) {
        try {
            GenericRecord record = fromBytes(payload, payloadSchema);
            ByteArrayOutputStream out = new ByteArrayOutputStream(256);
            org.apache.avro.io.JsonEncoder encoder =
                    EncoderFactory.get().jsonEncoder(payloadSchema, out);
            new GenericDatumWriter<GenericRecord>(payloadSchema).write(record, encoder);
            encoder.flush();
            return out.toString(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to write payload to JSON", ex);
        }
    }
}