package io.logisticsos.orchestrator;

import io.logisticsos.events.EventEnvelope;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Stub heartbeat: when {@code orchestrator.emit-enabled} is true, cycles a demo shipment working
 * set and emits a {@code NOTIFY_CUSTOMER} command per tick so the downstream command path
 * (topic bootstrap → consumer) can be exercised end-to-end on a live environment.
 */
@Component
public class CommandEmitter {

    private static final Logger log = LoggerFactory.getLogger(CommandEmitter.class);

    private final KafkaTemplate<String, EventEnvelope> template;
    private final StubCommandBuilder builder;
    private final OrchestratorProperties properties;
    private final AtomicLong round = new AtomicLong();

    public CommandEmitter(KafkaTemplate<String, EventEnvelope> template,
                          StubCommandBuilder builder,
                          OrchestratorProperties properties) {
        this.template = template;
        this.builder = builder;
        this.properties = properties;
    }

    @Scheduled(fixedRateString = "${orchestrator.emit-interval-millis:5000}")
    public void emitDemoCommand() {
        if (!properties.isEmitEnabled()) {
            return;
        }
        long r = round.getAndIncrement();
        int workingSet = Math.max(1, properties.getDemoWorkingSet());
        String shipmentId = "SH-DEMO-" + (r % workingSet);
        StubCommandBuilder.Dispatch dispatch = builder.notifyCustomer(shipmentId, null, "case-stub");
        template.send(dispatch.topic(), shipmentId, dispatch.envelope());
        log.debug("emitted command {} on {} for {}", dispatch.commandId(), dispatch.topic(), shipmentId);
    }
}