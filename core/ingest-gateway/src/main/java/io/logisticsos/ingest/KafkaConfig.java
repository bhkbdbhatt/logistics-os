package io.logisticsos.ingest;

import io.logisticsos.events.kafka.SchemaResolvers;
import java.util.Map;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;

/**
 * Kafka envelope producer. Locally (and in CI tests) this runs against a real Kafka broker with
 * {@code schema.resource=true}: envelope + payload schemas are loaded from contract copies on the
 * classpath instead of a Schema Registry. Production switches to {@code schema.registry.url}.
 */
@Configuration(proxyBeanMethods = false)
public class KafkaConfig {

    @Bean
    public KafkaTemplate<String, io.logisticsos.events.EventEnvelope> eventEnvelopeKafkaTemplate(
            KafkaProperties properties) {
        Map<String, Object> props = properties.buildProducerProperties();
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                io.logisticsos.events.kafka.EventEnvelopeSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(SchemaResolvers.SCHEMA_RESOURCE_CONFIG, true);
        ProducerFactory<String, io.logisticsos.events.EventEnvelope> factory =
                new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(factory);
    }
}