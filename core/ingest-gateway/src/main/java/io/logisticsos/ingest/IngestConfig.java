package io.logisticsos.ingest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.kafka.ResourceSchemaResolver;
import io.logisticsos.events.kafka.SchemaResolver;
import io.logisticsos.idempotency.GuardedProcessor;
import io.logisticsos.idempotency.InMemoryDedupeStore;
import java.time.Duration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration(proxyBeanMethods = false)
public class IngestConfig {

    @Bean
    public SchemaResolver schemaResolver() {
        return new ResourceSchemaResolver();
    }

    @Bean
    public GuardedProcessor dedupeProcessor() {
        return new GuardedProcessor(new InMemoryDedupeStore(Duration.ofHours(48)));
    }

    @Bean
    public IngestPublisher ingestPublisher(KafkaTemplate<String, EventEnvelope> template) {
        return new KafkaIngestPublisher(template);
    }

    @Bean
    public IngestService ingestService(SchemaResolver schemas, IngestPublisher publisher, GuardedProcessor dedupe) {
        return new IngestService(schemas, publisher, dedupe);
    }
}