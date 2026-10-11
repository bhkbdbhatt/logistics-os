package io.logisticsos.tower;

import io.logisticsos.events.EventEnvelope;
import org.springframework.kafka.core.KafkaTemplate;

/** Sends a built command envelope on the tower's configured command topic, keyed by target id. */
public final class CommandDispatcher {

    private final KafkaTemplate<String, EventEnvelope> template;
    private final CommandBuilder builder;

    public CommandDispatcher(KafkaTemplate<String, EventEnvelope> template, CommandBuilder builder) {
        this.template = template;
        this.builder = builder;
    }

    public CommandBuilder.Dispatch notifyCustomer(String shipmentId, String tenantId, String caseId, long issuedAt) {
        CommandBuilder.Dispatch dispatch = builder.notifyCustomer(shipmentId, tenantId, caseId, issuedAt);
        template.send(dispatch.topic(), shipmentId, dispatch.envelope());
        return dispatch;
    }
}