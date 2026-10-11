package io.logisticsos.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.EventEnvelopeBuilder;
import io.logisticsos.events.kafka.EnvelopeHeaders;
import io.logisticsos.events.kafka.EventEnvelopeDeserializer;
import io.logisticsos.events.kafka.EventEnvelopeSerializer;
import io.logisticsos.events.kafka.SchemaResolvers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
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

class KafkaIngestPublisherIT {

    private static EmbeddedKafkaBroker broker;
    private static KafkaTemplate<String, EventEnvelope> template;

    @BeforeAll
    static void startBroker() {
        broker = new EmbeddedKafkaBroker(1, true, 1, "obs.telemetry.v1");
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
    void publishesEnvelopeKeyedByAggregateIdWithCanonicalHeaders() {
        KafkaIngestPublisher publisher = new KafkaIngestPublisher(template);
        EventEnvelope envelope = new EventEnvelopeBuilder(1720000000000L)
                .eventType("io.logisticsos.obs.v1.Telemetry")
                .tenantId("acme")
                .aggregateType("device")
                .aggregateId("DEV-7")
                .eventVersion(1)
                .aggregateVersion(1)
                .producer("ingest-gateway@0.1.0")
                .sourceSystem("truck-sim")
                .schemaSubject("io.logisticsos.obs.v1.Telemetry-value")
                .payload(new byte[] {1, 2, 3})
                .build();

        publisher.publish("obs.telemetry.v1", envelope);

        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put("bootstrap.servers", broker.getBrokersAsString());
        consumerProps.put("group.id", "it-consumer");
        consumerProps.put("auto.offset.reset", "earliest");
        consumerProps.put("key.deserializer", StringDeserializer.class);
        consumerProps.put("value.deserializer", EventEnvelopeDeserializer.class);
        consumerProps.put(SchemaResolvers.SCHEMA_RESOURCE_CONFIG, true);

        try (KafkaConsumer<String, EventEnvelope> consumer = new KafkaConsumer<>(consumerProps,
                new StringDeserializer(),
                new EventEnvelopeDeserializer())) {
            consumer.subscribe(List.of("obs.telemetry.v1"));

            ConsumerRecords<String, EventEnvelope> records = ConsumerRecords.EMPTY;
            long deadline = System.currentTimeMillis() + 15_000;
            while (records.isEmpty() && System.currentTimeMillis() < deadline) {
                records = consumer.poll(Duration.ofMillis(500));
            }

            assertThat(records.count()).isEqualTo(1);
            var record = records.iterator().next();
            assertThat(record.key()).isEqualTo("DEV-7");
            assertThat(record.value().eventId()).isEqualTo(envelope.eventId());
            assertThat(record.value().tenantId()).isEqualTo("acme");
            assertThat(record.value().schemaSubject()).isEqualTo("io.logisticsos.obs.v1.Telemetry-value");
            assertThat(new String(record.headers().lastHeader(EnvelopeHeaders.TENANT_ID).value(),
                    StandardCharsets.UTF_8)).isEqualTo("acme");
            assertThat(new String(record.headers().lastHeader(EnvelopeHeaders.CORRELATION_ID).value(),
                    StandardCharsets.UTF_8)).isEqualTo("device:DEV-7");
        }
    }
}