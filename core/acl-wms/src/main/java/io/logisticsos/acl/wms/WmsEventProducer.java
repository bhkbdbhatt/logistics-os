package io.logisticsos.acl.wms;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.EventEnvelopeBuilder;
import io.logisticsos.events.kafka.EnvelopeHeaders;
import io.logisticsos.events.kafka.PayloadAvro;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publishes a translated canonical WMS/obs event to its topic ({@code ParsedEvent.topic}). */
@Component
public class WmsEventProducer {

    private final KafkaTemplate<String, EventEnvelope> template;

    public WmsEventProducer(KafkaTemplate<String, EventEnvelope> template) {
        this.template = template;
    }

    public void publish(LegacyWmsMapper.ParsedEvent event, String tenantId, String sourceSystem) {
        EventEnvelope envelope = new EventEnvelopeBuilder()
                .eventType(event.payloadType())
                .tenantId(tenantId)
                .aggregateType(aggregateType(event.payloadType()))
                .aggregateId(event.aggregateId())
                .eventVersion(1)
                .aggregateVersion(1)
                .producer("acl-wms@0.1.0")
                .sourceSystem(sourceSystem)
                .schemaSubject(event.schemaSubject())
                .payload(PayloadAvro.toBytes(event.record()))
                .build();

        ProducerRecord<String, EventEnvelope> record = new ProducerRecord<>(event.topic(), event.aggregateId(), envelope);
        record.headers().add(EnvelopeHeaders.TENANT_ID, tenantId.getBytes(StandardCharsets.UTF_8));
        record.headers().add(EnvelopeHeaders.CORRELATION_ID,
                envelope.correlationId().getBytes(StandardCharsets.UTF_8));
        record.headers().add(EnvelopeHeaders.EVENT_ID, envelope.eventId().getBytes(StandardCharsets.UTF_8));
        template.send(record);
    }

    private static String aggregateType(String payloadType) {
        return switch (payloadType) {
            case "io.logisticsos.obs.v1.Scan" -> "location";
            case "io.logisticsos.wms.v1.WmsTaskEvent" -> "wms-task";
            case "io.logisticsos.wms.v1.InventoryMove" -> "inventory";
            default -> "unknown";
        };
    }
}