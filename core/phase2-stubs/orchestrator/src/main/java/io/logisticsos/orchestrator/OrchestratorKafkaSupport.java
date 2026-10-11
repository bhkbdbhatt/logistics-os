package io.logisticsos.orchestrator;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.kafka.EventEnvelopeSerializer;
import io.logisticsos.events.kafka.ResourceSchemaResolver;
import io.logisticsos.events.kafka.SchemaResolvers;
import io.logisticsos.events.kafka.SchemaResolver;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration(proxyBeanMethods = false)
public class OrchestratorKafkaSupport {

    @Bean
    public SchemaResolver schemaResolver() {
        return new ResourceSchemaResolver();
    }

    @Bean(name = "eventEnvelopeTemplate")
    public KafkaTemplate<String, EventEnvelope> eventEnvelopeTemplate(KafkaProperties properties) {
        Map<String, Object> props = properties.buildProducerProperties();
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, EventEnvelopeSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(SchemaResolvers.SCHEMA_RESOURCE_CONFIG, true);
        ProducerFactory<String, EventEnvelope> factory = new DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(factory);
    }
}