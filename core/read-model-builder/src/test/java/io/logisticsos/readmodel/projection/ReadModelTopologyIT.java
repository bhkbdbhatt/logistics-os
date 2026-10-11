package io.logisticsos.readmodel.projection;

import static io.logisticsos.readmodel.projection.ProjectionNames.INVENTORY_INPUT;
import static io.logisticsos.readmodel.projection.ProjectionNames.INVENTORY_STATE_TOPIC;
import static io.logisticsos.readmodel.projection.ProjectionNames.SHIPMENT_INPUT;
import static io.logisticsos.readmodel.projection.ProjectionNames.SHIPMENT_STATE_TOPIC;
import static org.assertj.core.api.Assertions.assertThat;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.kafka.PayloadAvro;
import io.logisticsos.events.kafka.ResourceSchemaResolver;
import io.logisticsos.events.kafka.SchemaResolvers;
import io.logisticsos.events.kafka.EventEnvelopeSerializer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;

/**
 * End-to-end projection topology test against a real embedded broker. Also proves the two version
 * guards: a stale/out-of-order shipment event must not regress the view, and a duplicate inventory
 * move (same moveSequence) must not regress stock.
 */
class ReadModelTopologyIT {

    private EmbeddedKafkaBroker broker;
    private KafkaTemplate<String, EventEnvelope> producer;
    private KafkaStreams streams;

    @BeforeEach
    void start() {
        broker = new EmbeddedKafkaBroker(1, true, 1,
                SHIPMENT_INPUT, INVENTORY_INPUT,
                SHIPMENT_STATE_TOPIC, INVENTORY_STATE_TOPIC);
        broker.afterPropertiesSet();

        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, EventEnvelopeSerializer.class);
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
        producerProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        producerProps.put(SchemaResolvers.SCHEMA_RESOURCE_CONFIG, true);
        producer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));

        streams = new KafkaStreams(ReadModelTopology.build(),
                ReadModelTopology.standaloneProperties(broker.getBrokersAsString()));
        streams.start();
        waitForRunning();
    }

    @AfterEach
    void stop() {
        if (streams != null) {
            streams.close(Duration.ofSeconds(10));
        }
        if (broker != null) {
            broker.destroy();
        }
    }

    @Test
    void projectsShipmentLifecycleAndGuardsAgainstStaleReplay() {
        var schemas = new ResourceSchemaResolver();
        long created = 1720000000000L;
        long planned = created + 1000;
        long dispatched = created + 2000;

        producer.send(ProjectionTestEnvelopes.shipmentCreated(schemas, "SH-100", created));
        producer.send(ProjectionTestEnvelopes.shipmentPlanned(schemas, "SH-100", planned));
        producer.send(ProjectionTestEnvelopes.shipmentDispatched(schemas, "SH-100", dispatched));
        // stale replay: created that must NOT regress the DISPATCHED view
        producer.send(ProjectionTestEnvelopes.shipmentCreated(schemas, "SH-100", 0L));
        producer.flush();

        Map<String, String> state = awaitStateTopic(SHIPMENT_STATE_TOPIC, "SH-100");
        assertThat(state.get("SH-100")).contains("\"DISPATCHED\"");
    }

    @Test
    void projectsInventoryAndGuardsAgainstDuplicateMoveSequence() {
        var schemas = new ResourceSchemaResolver();
        producer.send(ProjectionTestEnvelopes.inventoryMove(schemas, "INV-77", 1, "RECEIPT", 100d, 1720000000000L));
        producer.send(ProjectionTestEnvelopes.inventoryMove(schemas, "INV-77", 2, "TRANSFER", 10d, 1720000000000L));
        producer.send(ProjectionTestEnvelopes.inventoryMove(schemas, "INV-77", 2, "TRANSFER", 999d, 1720000000000L));
        producer.flush();

        Map<String, String> state = awaitStateTopic(INVENTORY_STATE_TOPIC, "INV-77");
        assertThat(state.get("INV-77")).contains("\"moveSequence\":2").doesNotContain("999.0");
    }

    private Map<String, String> awaitStateTopic(String topic, String key) {
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + topic + "-" + System.nanoTime());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        Map<String, String> latestByKey = new HashMap<>();
        long deadline = System.currentTimeMillis() + 20_000;
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of(topic));
            while (System.currentTimeMillis() < deadline && !latestByKey.containsKey(key)) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                records.forEach(r -> latestByKey.put(r.key(), r.value()));
            }
        }
        assertThat(latestByKey).describedAs("expected a state record for key %s on %s", key, topic).isNotEmpty();
        return latestByKey;
    }

    private void waitForRunning() {
        long deadline = System.currentTimeMillis() + 20_000;
        while (streams.state() != KafkaStreams.State.RUNNING && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
        assertThat(streams.state()).isEqualTo(KafkaStreams.State.RUNNING);
    }
}