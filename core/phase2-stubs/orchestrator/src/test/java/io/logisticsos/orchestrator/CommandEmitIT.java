package io.logisticsos.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.kafka.EventEnvelopeDeserializer;
import io.logisticsos.events.kafka.EventEnvelopeSerializer;
import io.logisticsos.events.kafka.SchemaResolvers;
import io.logisticsos.orchestrator.StubCommandBuilder.Dispatch;
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

/**
 * Round-trips a stub command against a real embedded broker: {@code orch.command.v1} in, envelope +
 * payload decoded and asserted. This is the stub's guarantee that what it builds is a valid,
 * consumable {@code OrchestrationCommand}.
 */
class CommandEmitIT {

    private static EmbeddedKafkaBroker broker;
    private static KafkaTemplate<String, EventEnvelope> template;

    @BeforeAll
    static void startBroker() {
        broker = new EmbeddedKafkaBroker(1, true, 1, "orch.command.v1");
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
    void emitsConsumableNotifyCustomerCommandKeyedByShipment() {
        var schemas = new io.logisticsos.events.kafka.ResourceSchemaResolver();
        var builder = new StubCommandBuilder(schemas, new OrchestratorProperties());
        Dispatch dispatch = builder.notifyCustomer("SH-777", "tenant-1", "case-stub");
        template.send(dispatch.topic(), "SH-777", dispatch.envelope());

        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put("bootstrap.servers", broker.getBrokersAsString());
        consumerProps.put("group.id", "orch-it-" + System.nanoTime());
        consumerProps.put("auto.offset.reset", "earliest");
        consumerProps.put("key.deserializer", StringDeserializer.class);
        consumerProps.put("value.deserializer", EventEnvelopeDeserializer.class);
        consumerProps.put(SchemaResolvers.SCHEMA_RESOURCE_CONFIG, true);

        try (KafkaConsumer<String, EventEnvelope> consumer = new KafkaConsumer<>(consumerProps,
                new StringDeserializer(), new EventEnvelopeDeserializer())) {
            consumer.subscribe(List.of("orch.command.v1"));
            ConsumerRecords<String, EventEnvelope> records = ConsumerRecords.EMPTY;
            long deadline = System.currentTimeMillis() + 15_000;
            while (records.isEmpty() && System.currentTimeMillis() < deadline) {
                records = consumer.poll(Duration.ofMillis(500));
            }

            assertThat(records.count()).isEqualTo(1);
            var record = records.iterator().next();
            assertThat(record.key()).isEqualTo("SH-777");
            assertThat(record.value().eventType()).isEqualTo("io.logisticsos.orchestration.v1.OrchestrationCommand");
            assertThat(record.value().tenantId()).isEqualTo("tenant-1");
            assertThat(record.value().aggregateId()).isEqualTo("SH-777");
            assertThat(record.value().eventId()).isEqualTo(dispatch.eventId());
        }
    }
}