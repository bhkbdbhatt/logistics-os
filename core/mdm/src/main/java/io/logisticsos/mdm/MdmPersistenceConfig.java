package io.logisticsos.mdm;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.outbox.OutboxPublisher;
import io.logisticsos.persistence.JdbcOutboxRepository;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * PostgreSQL system-of-record wiring (ADR-0021), activated by the {@code jdbc} Spring profile.
 * Without it, MDM keeps the in-memory registry + direct publish.
 */
@Configuration(proxyBeanMethods = false)
@Profile("jdbc")
@EnableScheduling
public class MdmPersistenceConfig {

    @Bean
    public JdbcMdmStore jdbcMdmStore(DataSource dataSource) {
        return new JdbcMdmStore(dataSource);
    }

    @Bean
    public JdbcOutboxRepository mdmOutboxRepository(DataSource dataSource) {
        return new JdbcOutboxRepository(dataSource);
    }

    @Bean
    public MdmEventFactory mdmEventFactory(MdmPublisher publisher) {
        return new MdmEventFactory(publisher);
    }

    @Bean
    public OutboxMdmWrites outboxMdmWrites(DataSource dataSource, JdbcMdmStore store,
            JdbcOutboxRepository outbox, MdmEventFactory events, ReferenceCodec codec) {
        return new OutboxMdmWrites(dataSource, store, outbox, events, codec);
    }

    @Bean
    public MdmOutboxRelay mdmOutboxRelay(JdbcOutboxRepository outbox,
            @Qualifier("eventEnvelopeTemplate") KafkaTemplate<String, EventEnvelope> template) {
        return new MdmOutboxRelay(new OutboxPublisher(outbox), new MdmOutboxDispatcher(template), 50);
    }
}
