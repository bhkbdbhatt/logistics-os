package io.logisticsos.readmodel.projection;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.kafka.PayloadAvro;
import io.logisticsos.events.kafka.SchemaResolver;
import org.apache.avro.generic.GenericRecord;

/** Decodes an envelope's payload bytes using the payload schema declared on the envelope. */
public final class EnvelopePayloadDecoder {

    private final SchemaResolver schemas;

    public EnvelopePayloadDecoder(SchemaResolver schemas) {
        this.schemas = schemas;
    }

    public GenericRecord decode(EventEnvelope envelope) {
        return PayloadAvro.fromBytes(envelope.payload(), schemas.resolve(envelope.schemaSubject()));
    }
}