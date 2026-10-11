package io.logisticsos.events.kafka;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.EventEnvelopeCodec;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.kafka.common.serialization.Deserializer;

/**
 * Avro-binary deserializer for {@link EventEnvelope}. Envelope schema is resolved from
 * {@link EventEnvelopeSerde#ENVELOPE_SUBJECT_CONFIG} (default {@code io.logisticsos.events.EventEnvelope-value}).
 */
public final class EventEnvelopeDeserializer implements Deserializer<EventEnvelope> {

    private SchemaResolver resolver;
    private String envelopeSubject;

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        this.resolver = SchemaResolvers.create(configs);
        this.envelopeSubject = configs.get(EventEnvelopeSerde.ENVELOPE_SUBJECT_CONFIG) == null
                ? EventEnvelopeSerde.DEFAULT_ENVELOPE_SUBJECT
                : String.valueOf(configs.get(EventEnvelopeSerde.ENVELOPE_SUBJECT_CONFIG));
    }

    @Override
    public EventEnvelope deserialize(String topic, byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        Schema schema = resolver.resolve(envelopeSubject);
        return EventEnvelopeCodec.decode(data, schema);
    }

    @Override
    public void close() {
        // nothing to release
    }
}