package io.logisticsos.mdm;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.kafka.SchemaResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration(proxyBeanMethods = false)
public class MdmConfig {

    @Bean
    public MdmRegistry mdmRegistry() {
        return new MdmRegistry();
    }

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
    public MdmService mdmService(MdmRegistry registry, MdmPublisher publisher, ReferenceCodec codec,
            ObjectMapper json) {
        return new MdmService(registry, publisher, codec, json);
    }
}