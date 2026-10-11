package io.logisticsos.mdm;

import static org.assertj.core.api.Assertions.assertThat;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.kafka.EventEnvelopeDeserializer;
import io.logisticsos.events.kafka.EventEnvelopeSerializer;
import io.logisticsos.events.kafka.PayloadAvro;
import io.logisticsos.events.kafka.ResourceSchemaResolver;
import io.logisticsos.events.kafka.SchemaResolvers;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;

class MdmPublishIT {

    private static EmbeddedKafkaBroker broker;
    private static KafkaTemplate<String, EventEnvelope> template;

    private final ResourceSchemaResolver resolver = new ResourceSchemaResolver();
    private final ReferenceCodec codec = new ReferenceCodec(resolver);

    @BeforeAll
    static void startBroker() {
        broker = new EmbeddedKafkaBroker(1, true, 1, "mdm.location.events.v1");
        broker.afterPropertiesSet();

        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, EventEnvelopeSerializer.class);
        props.put(SchemaResolvers.SCHEMA_RESOURCE_CONFIG, true);
        template = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    @AfterAll
    static void stopBroker() {
        if (template != null) {
            template.destroy();
        }
        if (broker != null) {
            broker.destroy();
        }
    }

    @Test
    void publishesDecodableReferenceUpsertedKeyedByEntityId() {
        String json = """
                {"locationId":"LOC-9","tenantId":"tenant-1","name":"Rotterdam Port","type":"PORT",
                 "address":{"line1":"1 Dock","city":"Rotterdam","postalCode":"3000","country":"NL"},
                 "timezone":"Europe/Amsterdam","status":"ACTIVE"}""";
        GenericRecord record = codec.recordFromJson(MdmEntities.LOCATION, json);
        MdmRegistry.Entry entry = new MdmRegistry.Entry("location", "LOC-9", "tenant-1", 1, true,
                codec.recordToCanonicalJson(MdmEntities.LOCATION, record));
        new MdmPublisher(template, codec).publishUpsert(MdmEntities.LOCATION, entry, record, true, "it");

        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put("bootstrap.servers", broker.getBrokersAsString());
        consumerProps.put("group.id", "mdm-it");
        consumerProps.put("auto.offset.reset", "earliest");
        consumerProps.put(SchemaResolvers.SCHEMA_RESOURCE_CONFIG, true);

        try (KafkaConsumer<String, EventEnvelope> consumer = new KafkaConsumer<>(consumerProps,
                new StringDeserializer(), new EventEnvelopeDeserializer())) {
            consumer.subscribe(List.of("mdm.location.events.v1"));
            ConsumerRecords<String, EventEnvelope> records = ConsumerRecords.EMPTY;
            long deadline = System.currentTimeMillis() + 15_000;
            while (records.isEmpty() && System.currentTimeMillis() < deadline) {
                records = consumer.poll(Duration.ofMillis(500));
            }

            assertThat(records.count()).isEqualTo(1);
            var record0 = records.iterator().next();
            assertThat(record0.key()).isEqualTo("LOC-9");
            EventEnvelope envelope = record0.value();
            assertThat(envelope.eventType()).isEqualTo("io.logisticsos.mdm.v1.ReferenceUpserted");
            assertThat(envelope.aggregateVersion()).isEqualTo(1);

            GenericRecord upsert = PayloadAvro.fromBytes(envelope.payload(), codec.upsertSchema());
            GenericRecord decoded = codec.recordFromUpsert(MdmEntities.LOCATION, upsert);
            assertThat(codec.entityId(MdmEntities.LOCATION, decoded)).isEqualTo("LOC-9");
            assertThat(upsert.get("active")).isEqualTo(true);
        }
    }
}