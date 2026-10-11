package io.logisticsos.tower;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.kafka.SchemaResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration(proxyBeanMethods = false)
public class TowerConfig {

    @Bean
    public TowerCache towerCache() {
        return new TowerCache();
    }

    @Bean
    public CommandBuilder commandBuilder(SchemaResolver schemaResolver, TowerProperties properties) {
        return new CommandBuilder(schemaResolver, properties);
    }

    @Bean
    public CommandDispatcher commandDispatcher(
            @Qualifier("eventEnvelopeTemplate") KafkaTemplate<String, EventEnvelope> template,
            CommandBuilder builder) {
        return new CommandDispatcher(template, builder);
    }
}