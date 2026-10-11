package io.logisticsos.mdm;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.EventEnvelopeBuilder;
import io.logisticsos.events.kafka.PayloadAvro;
import org.apache.avro.generic.GenericRecord;
import org.springframework.kafka.core.KafkaTemplate;

/** Publishes a ReferenceUpserted envelope on the entity's compacted topic, keyed by entity id. */
public final class MdmPublisher implements ReferenceSink {

    private static final String EVENT_TYPE = "io.logisticsos.mdm.v1.ReferenceUpserted";
    private static final String PRODUCER = "mdm@1.0.0";

    private final KafkaTemplate<String, EventEnvelope> template;
    private final ReferenceCodec codec;

    public MdmPublisher(KafkaTemplate<String, EventEnvelope> template, ReferenceCodec codec) {
        this.template = template;
        this.codec = codec;
    }

    public void publishUpsert(MdmEntities entity, MdmRegistry.Entry entry, GenericRecord record,
            boolean active, String sourceSystem) {
        GenericRecord upsert = codec.toUpsert(entity, record, active, sourceSystem);
        EventEnvelope envelope = new EventEnvelopeBuilder()
                .eventType(EVENT_TYPE)
                .tenantId(entry.tenantId())
                .aggregateType(entity.aggregateType())
                .aggregateId(entry.id())
                .aggregateVersion(entry.version())
                .producer(PRODUCER)
                .sourceSystem(sourceSystem)
                .schemaSubject(codec.upsertSchema().getFullName() + "-value")
                .payload(PayloadAvro.toBytes(upsert))
                .build();
        template.send(entity.topic(), entry.id(), envelope);
    }
}