package io.logisticsos.acl.tms;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.EventEnvelopeBuilder;
import io.logisticsos.events.kafka.EnvelopeHeaders;
import io.logisticsos.events.kafka.EnvelopeRouting;
import io.logisticsos.events.kafka.PayloadAvro;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publishes a translated canonical shipment event to the tms shipment events topic. */
@Component
public class TmsEventProducer {

    private final KafkaTemplate<String, EventEnvelope> template;

    public TmsEventProducer(KafkaTemplate<String, EventEnvelope> template) {
        this.template = template;
    }

    public void publish(LegacyTmsMapper.ParsedEvent event, String tenantId, String sourceSystem) {
        String topic = EnvelopeRouting.topic(EnvelopeRouting.Layer.TMS, "shipment", "events", 1);
        EventEnvelope envelope = new EventEnvelopeBuilder()
                .eventType(event.payloadType())
                .tenantId(tenantId)
                .aggregateType("shipment")
                .aggregateId(event.aggregateId())
                .eventVersion(1)
                .aggregateVersion(1)
                .producer("acl-tms@0.1.0")
                .sourceSystem(sourceSystem)
                .schemaSubject(event.schemaSubject())
                .payload(PayloadAvro.toBytes(event.record()))
                .build();

        ProducerRecord<String, EventEnvelope> record = new ProducerRecord<>(topic, event.aggregateId(), envelope);
        record.headers().add(EnvelopeHeaders.TENANT_ID, tenantId.getBytes(StandardCharsets.UTF_8));
        record.headers().add(EnvelopeHeaders.CORRELATION_ID,
                envelope.correlationId().getBytes(StandardCharsets.UTF_8));
        record.headers().add(EnvelopeHeaders.EVENT_ID, envelope.eventId().getBytes(StandardCharsets.UTF_8));
        template.send(record);
    }
}