package io.logisticsos.acl.tms;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.kafka.ResourceSchemaResolver;
import io.logisticsos.events.kafka.SchemaResolvers;
import io.logisticsos.events.kafka.EventEnvelopeSerializer;
import io.logisticsos.events.kafka.SchemaResolver;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration(proxyBeanMethods = false)
public class KafkaSupport {

    @Bean
    public SchemaResolver schemaResolver() {
        return new ResourceSchemaResolver();
    }

    @Bean
    public ConsumerFactory<String, String> legacyConsumerFactory(KafkaProperties properties) {
        Map<String, Object> props = properties.buildConsumerProperties();
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> factory) {
        ConcurrentKafkaListenerContainerFactory<String, String> container =
                new ConcurrentKafkaListenerContainerFactory<>();
        container.setConsumerFactory(factory);
        return container;
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