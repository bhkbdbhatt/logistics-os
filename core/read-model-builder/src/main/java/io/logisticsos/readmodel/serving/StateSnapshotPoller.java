package io.logisticsos.readmodel.serving;

import io.logisticsos.readmodel.RmbProperties;
import io.logisticsos.readmodel.projection.InventoryProjection;
import io.logisticsos.readmodel.projection.ShipmentProjection;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Serving layer: keeps the in-process {@link ReadModelCache} in sync with the compacted state
 * topics emitted by the projection topology. Last-write-wins copies of version-guarded projections
 * (independent of the Streams state store, so the REST tier can run without Kafka Streams access).
 * Scales horizontally behind a topic-keyed cache partition in production.
 */
@Component
public class StateSnapshotPoller {

    private static final Logger log = LoggerFactory.getLogger(StateSnapshotPoller.class);

    private final KafkaProperties kafkaProperties;
    private final RmbProperties properties;
    private final ReadModelCache cache;

    public StateSnapshotPoller(KafkaProperties kafkaProperties, RmbProperties properties, ReadModelCache cache) {
        this.kafkaProperties = kafkaProperties;
        this.properties = properties;
        this.cache = cache;
    }

    @Scheduled(fixedDelayString = "${rmb.snapshot-interval-ms:1500}", initialDelay = 1500)
    public void pollStateTopics() {
        if (!properties.isSnapshotConsumeEnabled()) {
            return;
        }
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "read-model-builder.snapshot");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(properties.getShipmentStateTopic(), properties.getInventoryStateTopic()));
            applyRecords(consumer);
            consumer.unsubscribe();
        } catch (Exception ex) {
            log.warn("snapshot poll failed (retrying on next tick)", ex);
        }
    }

    private void applyRecords(KafkaConsumer<String, String> consumer) {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(400));
            if (records.isEmpty()) {
                continue;
            }
            for (ConsumerRecord<String, String> record : records) {
                if (properties.getShipmentStateTopic().equals(record.topic())) {
                    ShipmentProjection p = StateLineParser.parseShipment(record.value());
                    if (p != null && p.getShipmentId() != null) {
                        cache.putShipment(p);
                    }
                } else if (properties.getInventoryStateTopic().equals(record.topic())) {
                    InventoryProjection p = StateLineParser.parseInventory(record.value());
                    if (p != null && p.getInventoryId() != null) {
                        cache.putInventory(p);
                    }
                }
            }
        }
    }
}