package io.logisticsos.acl.wms;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes legacy WMS vendor events (plain JSON strings) and republishes them as canonical
 * envelopes. Scaffold-level translation — see ADR-0007 note in the build plan: a transactional
 * outbox binding replaces direct KafkaTemplate usage in the production roadmap.
 */
@Component
public class LegacyWmsListener {

    private static final Logger log = LoggerFactory.getLogger(LegacyWmsListener.class);

    private final LegacyWmsMapper mapper;
    private final WmsEventProducer producer;
    private final String tenantHeader;
    private final String defaultTenant;

    public LegacyWmsListener(LegacyWmsMapper mapper, WmsEventProducer producer,
                             @Value("${acl.tenant-header}") String tenantHeader,
                             @Value("${acl.default-tenant}") String defaultTenant) {
        this.mapper = mapper;
        this.producer = producer;
        this.tenantHeader = tenantHeader;
        this.defaultTenant = defaultTenant;
    }

    @KafkaListener(topics = "${acl.legacy-topic}")
    public void onLegacyEvent(ConsumerRecord<String, String> record) {
        String tenantId = record.headers().lastHeader(tenantHeader) != null
                ? new String(record.headers().lastHeader(tenantHeader).value())
                : defaultTenant;
        try {
            LegacyWmsMapper.ParsedEvent event = mapper.map(record.value());
            producer.publish(event, tenantId, "legacy-wms");
            log.info("mapped legacy event type={} topic={} key={}", event.payloadType(), event.topic(), event.aggregateId());
        } catch (Exception ex) {
            // Dead-letter / alerting hook for scaffold; rejections are logged for observability.
            log.error("failed to translate legacy WMS event at offset {} topic {}",
                    record.offset(), record.topic(), ex);
        }
    }
}