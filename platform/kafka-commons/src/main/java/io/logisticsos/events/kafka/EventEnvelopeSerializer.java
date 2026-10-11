package io.logisticsos.events.kafka;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.EventEnvelopeCodec;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.kafka.common.serialization.Serializer;

/**
 * Avro-binary serializer for {@link EventEnvelope}. The string {@code eventType} is opaque to
 * serialization; the envelope schema is resolved from {@link EventEnvelopeSerde#ENVELOPE_SUBJECT_CONFIG}
 * (default {@code io.logisticsos.events.EventEnvelope-value}).
 */
public final class EventEnvelopeSerializer implements Serializer<EventEnvelope> {

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
    public byte[] serialize(String topic, EventEnvelope envelope) {
        if (envelope == null) {
            return null;
        }
        Schema schema = resolver.resolve(envelopeSubject);
        return EventEnvelopeCodec.encode(envelope, schema);
    }

    @Override
    public void close() {
        // nothing to release
    }
}