package io.logisticsos.acl.tms;

import static org.assertj.core.api.Assertions.assertThat;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.kafka.EventEnvelopeDeserializer;
import io.logisticsos.events.kafka.SchemaResolvers;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
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
import java.util.List;

/**
 * Exercise the full acl-tms pipeline against a real embedded broker: legacy JSON in on
 * legacy.tms.events.v1, canonical envelope out on tms.shipment.events.v1.
 */
class TmsPipelineIT {

    private static EmbeddedKafkaBroker broker;
    private static KafkaTemplate<String, String> legacyProducer;

    @BeforeAll
    static void startBroker() {
        broker = new EmbeddedKafkaBroker(1, true, 1,
                "legacy.tms.events.v1", "tms.shipment.events.v1");
        broker.afterPropertiesSet();

        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        legacyProducer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    @AfterAll
    static void stopBroker() {
        if (legacyProducer != null) {
            legacyProducer.destroy();
        }
        if (broker != null) {
            broker.destroy();
        }
    }

    @Test
    void translatesLegacyCreatedEventToCanonicalEnvelope() {
        var schemas = new io.logisticsos.events.kafka.ResourceSchemaResolver();
        var mapper = new LegacyTmsMapper(schemas);
        var outTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(new HashMap<>(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, io.logisticsos.events.kafka.EventEnvelopeSerializer.class,
                        SchemaResolvers.SCHEMA_RESOURCE_CONFIG, true))));
        var producer = new TmsEventProducer(outTemplate);

        String legacy = """
                {"type":"ShipmentCreated","shipmentId":"SH-200","proNumber":"PRO-5",
                 "shipperId":"CUST-1","consigneeId":"CUST-2",
                 "pickupLocationId":"WH-AMS","deliveryLocationId":"STORE-BER",
                 "pickupEarliest":1720000000000,"pickupLatest":1720086400000,
                 "deliveryEarliest":1720000000000,"deliveryLatest":1720086400000,
                 "serviceLevel":"STANDARD","mode":"FTL","quantity":5.0,"uom":"EA",
                 "totalWeightKg":100.0}
                """;

        producer.publish(mapper.map(legacy), "acme", "legacy-tms");

        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put("bootstrap.servers", broker.getBrokersAsString());
        consumerProps.put("group.id", "it-" + System.nanoTime());
        consumerProps.put("auto.offset.reset", "earliest");
        consumerProps.put("key.deserializer", StringDeserializer.class);
        consumerProps.put("value.deserializer", EventEnvelopeDeserializer.class);
        consumerProps.put(SchemaResolvers.SCHEMA_RESOURCE_CONFIG, true);

        try (KafkaConsumer<String, EventEnvelope> consumer = new KafkaConsumer<>(consumerProps,
                new StringDeserializer(), new EventEnvelopeDeserializer())) {
            consumer.subscribe(List.of("tms.shipment.events.v1"));
            ConsumerRecords<String, EventEnvelope> records = ConsumerRecords.EMPTY;
            long deadline = System.currentTimeMillis() + 15_000;
            while (records.isEmpty() && System.currentTimeMillis() < deadline) {
                records = consumer.poll(Duration.ofMillis(500));
            }

            assertThat(records.count()).isEqualTo(1);
            var record = records.iterator().next();
            assertThat(record.key()).isEqualTo("SH-200");
            assertThat(record.value().eventType()).isEqualTo("io.logisticsos.shipment.v1.ShipmentCreated");
            assertThat(record.value().tenantId()).isEqualTo("acme");
            assertThat(record.value().aggregateType()).isEqualTo("shipment");
            assertThat(new String(record.headers().lastHeader("X-Tenant-Id").value()))
                    .isEqualTo("acme");
        }
    }
}