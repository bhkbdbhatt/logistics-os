package io.logisticsos.ingest;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.kafka.EnvelopeHeaders;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

/** Publishes envelopes to the canonical obs topic for the kind, keyed by the aggregate natural key. */
public class KafkaIngestPublisher implements IngestPublisher {

    private final KafkaTemplate<String, EventEnvelope> template;

    public KafkaIngestPublisher(KafkaTemplate<String, EventEnvelope> template) {
        this.template = template;
    }

    @Override
    public void publish(String topic, EventEnvelope envelope) {
        ProducerRecord<String, EventEnvelope> record = new ProducerRecord<>(topic, envelope.aggregateId(), envelope);
        if (envelope.tenantId() != null) {
            record.headers().add(EnvelopeHeaders.TENANT_ID,
                    envelope.tenantId().getBytes(StandardCharsets.UTF_8));
        }
        record.headers().add(EnvelopeHeaders.CORRELATION_ID,
                envelope.correlationId().getBytes(StandardCharsets.UTF_8));
        record.headers().add(EnvelopeHeaders.EVENT_ID,
                envelope.eventId().getBytes(StandardCharsets.UTF_8));
        if (envelope.sourceSystem() != null) {
            record.headers().add(EnvelopeHeaders.SOURCE_SYSTEM,
                    envelope.sourceSystem().getBytes(StandardCharsets.UTF_8));
        }
        template.send(record);
    }
}