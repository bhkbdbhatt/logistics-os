package io.logisticsos.mdm;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.kafka.SchemaResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration(proxyBeanMethods = false)
public class MdmConfig {

    @Bean
    public ReferenceCodec referenceCodec(SchemaResolver schemaResolver) {
        return new ReferenceCodec(schemaResolver);
    }

    @Bean
    public MdmPublisher mdmPublisher(
            @Qualifier("eventEnvelopeTemplate") KafkaTemplate<String, EventEnvelope> template,
            ReferenceCodec codec) {
        return new MdmPublisher(template, codec);
    }

    @Bean
    @Primary
    public MdmStore mdmStore(ObjectProvider<JdbcMdmStore> jdbcStore) {
        JdbcMdmStore store = jdbcStore.getIfAvailable();
        return store != null ? store : new MdmRegistry();
    }

    @Bean
    public MdmWrites mdmWrites(MdmStore store, MdmPublisher publisher, ReferenceCodec codec,
            ObjectProvider<OutboxMdmWrites> outboxWrites) {
        OutboxMdmWrites durable = outboxWrites.getIfAvailable();
        return durable != null ? durable : new DirectMdmWrites(store, publisher, codec);
    }

    @Bean
    public MdmService mdmService(MdmStore store, MdmWrites writes, ReferenceCodec codec, ObjectMapper json) {
        return new MdmService(store, writes, codec, json);
    }
}
