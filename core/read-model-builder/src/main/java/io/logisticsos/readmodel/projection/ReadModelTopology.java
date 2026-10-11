package io.logisticsos.readmodel.projection;

import static io.logisticsos.readmodel.projection.ProjectionNames.INVENTORY_INPUT;
import static io.logisticsos.readmodel.projection.ProjectionNames.INVENTORY_STATE_TOPIC;
import static io.logisticsos.readmodel.projection.ProjectionNames.INVENTORY_STORE;
import static io.logisticsos.readmodel.projection.ProjectionNames.SHIPMENT_INPUT;
import static io.logisticsos.readmodel.projection.ProjectionNames.SHIPMENT_STATE_TOPIC;
import static io.logisticsos.readmodel.projection.ProjectionNames.SHIPMENT_STORE;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.kafka.EventEnvelopeDeserializer;
import io.logisticsos.events.kafka.EventEnvelopeSerializer;
import io.logisticsos.events.kafka.ResourceSchemaResolver;
import io.logisticsos.events.kafka.SchemaResolver;
import io.logisticsos.events.kafka.SchemaResolvers;
import java.util.Map;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.state.Stores;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.apache.kafka.streams.kstream.Consumed;

/**
 * The read-model topology. Built via the Processor API for explicit, testable projection stores:
 * shipment + inventory events in, version-guarded projections persisted to compacted state topics out.
 */
public final class ReadModelTopology {

    public static final String TMS_SOURCE = "tms-events-in";
    public static final String TMS_PROCESSOR = "shipment-projection";
    public static final String TMS_SINK = "tms-state-out";
    public static final String INVENTORY_SOURCE = "inventory-events-in";
    public static final String INVENTORY_PROCESSOR = "inventory-projection";
    public static final String INVENTORY_SINK = "inventory-state-out";

    private ReadModelTopology() {
    }

    public static SchemaResolver schemaResolver() {
        return new ResourceSchemaResolver();
    }

    public static Serde<EventEnvelope> envelopeSerde() {
        EventEnvelopeSerializer serializer = new EventEnvelopeSerializer();
        EventEnvelopeDeserializer deserializer = new EventEnvelopeDeserializer();
        Map<String, Object> config = Map.of(SchemaResolvers.SCHEMA_RESOURCE_CONFIG, true);
        serializer.configure(config, false);
        deserializer.configure(config, false);
        return Serdes.serdeFrom(serializer, deserializer);
    }

    public static Topology build() {
        EnvelopePayloadDecoder decoder = new EnvelopePayloadDecoder(schemaResolver());

        Topology topology = new Topology();

        topology.addSource(TMS_SOURCE, Consumed.with(Serdes.String(), envelopeSerde()), SHIPMENT_INPUT);
        topology.addProcessor(TMS_PROCESSOR,
                () -> new ShipmentProjectionProcessor(decoder, SHIPMENT_STORE), TMS_SOURCE);
        topology.addStateStore(Stores.keyValueStoreBuilder(
                        Stores.persistentKeyValueStore(SHIPMENT_STORE),
                        Serdes.String(), new JsonSerde<>(ShipmentProjection.class)), TMS_PROCESSOR);
        topology.addSink(TMS_SINK, SHIPMENT_STATE_TOPIC, Serdes.String().serializer(),
                new org.springframework.kafka.support.serializer.JsonSerializer<>(), TMS_PROCESSOR);

        topology.addSource(INVENTORY_SOURCE,
                Consumed.with(Serdes.String(), envelopeSerde()), INVENTORY_INPUT);
        topology.addProcessor(INVENTORY_PROCESSOR,
                () -> new InventoryProjectionProcessor(decoder, INVENTORY_STORE), INVENTORY_SOURCE);
        topology.addStateStore(Stores.keyValueStoreBuilder(
                        Stores.persistentKeyValueStore(INVENTORY_STORE),
                        Serdes.String(), new JsonSerde<>(InventoryProjection.class)), INVENTORY_PROCESSOR);
        topology.addSink(INVENTORY_SINK, INVENTORY_STATE_TOPIC, Serdes.String().serializer(),
                new org.springframework.kafka.support.serializer.JsonSerializer<>(), INVENTORY_PROCESSOR);

        return topology;
    }

    /** Streams configuration for tests that run the topology standalone. */
    public static java.util.Properties standaloneProperties(String bootstrapServers) {
        java.util.Properties props = new java.util.Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "rmb-test-" + System.nanoTime());
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        try {
            props.put(StreamsConfig.STATE_DIR_CONFIG,
                    java.nio.file.Files.createTempDirectory("rmb-state").toString());
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("failed to create state dir for test", ex);
        }
        return props;
    }
}