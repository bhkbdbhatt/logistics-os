package io.logisticsos.acl.tms;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes legacy TMS vendor events (plain JSON strings) and republishes them as canonical
 * envelope events. Scaffold-level translation (no outbox yet — see ADR-0007 note in the build
 * plan: the canonicalized event producer uses KafkaTemplate directly; a transactional outbox
 * binding replaces it in the production roadmap).
 */
@Component
public class LegacyTmsListener {

    private static final Logger log = LoggerFactory.getLogger(LegacyTmsListener.class);

    private final LegacyTmsMapper mapper;
    private final TmsEventProducer producer;
    private final String tenantHeader;
    private final String defaultTenant;

    public LegacyTmsListener(LegacyTmsMapper mapper, TmsEventProducer producer,
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
            LegacyTmsMapper.ParsedEvent event = mapper.map(record.value());
            producer.publish(event, tenantId, "legacy-tms");
            log.info("mapped legacy event type={} shipmentId={}", event.payloadType(), event.aggregateId());
        } catch (Exception ex) {
            // Dead-letter / alerting hook for scaffold; rejections are logged for observability.
            log.error("failed to translate legacy event at offset {} topic {}",
                    record.offset(), record.topic(), ex);
        }
    }
}