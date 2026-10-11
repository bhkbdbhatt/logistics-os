package io.logisticsos.tower;

import java.time.Duration;
import java.util.ArrayList;
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

/** Keeps the {@link TowerCache} hot store in sync with the compacted CQRS state topics. */
@Component
public class TowerStatePoller {

    private static final Logger log = LoggerFactory.getLogger(TowerStatePoller.class);

    private final KafkaProperties kafkaProperties;
    private final TowerProperties properties;
    private final TowerCache cache;

    public TowerStatePoller(KafkaProperties kafkaProperties, TowerProperties properties, TowerCache cache) {
        this.kafkaProperties = kafkaProperties;
        this.properties = properties;
        this.cache = cache;
    }

    @Scheduled(fixedDelayString = "${tower.snapshot-interval-ms:2000}", initialDelay = 2000)
    public void pollStateTopics() {
        if (!properties.isSnapshotConsumeEnabled()) {
            return;
        }
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "control-tower.snapshot");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(properties.getShipmentStateTopic(), properties.getInventoryStateTopic()));
            applyRecords(consumer);
            consumer.unsubscribe();
        } catch (Exception ex) {
            log.warn("tower snapshot poll failed (retrying on next tick)", ex);
        }
    }

    private void applyRecords(KafkaConsumer<String, String> consumer) {
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> polled = consumer.poll(Duration.ofMillis(400));
            polled.forEach(records::add);
            if (!polled.isEmpty()) {
                break;
            }
        }
        for (ConsumerRecord<String, String> record : records) {
            if (properties.getShipmentStateTopic().equals(record.topic())) {
                ShipmentView view = TowerLineParser.parseShipment(record.value());
                if (view != null && view.getShipmentId() != null) {
                    cache.putShipment(view);
                }
            } else if (properties.getInventoryStateTopic().equals(record.topic())) {
                InventoryView view = TowerLineParser.parseInventory(record.value());
                if (view != null && view.getInventoryId() != null) {
                    cache.putInventory(view);
                }
            }
        }
    }
}