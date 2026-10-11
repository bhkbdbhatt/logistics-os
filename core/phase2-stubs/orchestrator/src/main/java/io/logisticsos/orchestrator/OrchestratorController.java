package io.logisticsos.orchestrator;

import io.logisticsos.events.EventEnvelope;
import java.util.Map;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual smoke hook: emits a {@code NOTIFY_CUSTOMER} command for a shipment so a live cluster
 * (or a human) can verify the {@code orch.command.v1} path without the scheduled emitter.
 */
@RestController
@RequestMapping("/orch/commands")
public class OrchestratorController {

    private final KafkaTemplate<String, EventEnvelope> template;
    private final StubCommandBuilder builder;

    public OrchestratorController(KafkaTemplate<String, EventEnvelope> template,
                                  StubCommandBuilder builder) {
        this.template = template;
        this.builder = builder;
    }

    public record NotifyRequest(String shipmentId, String tenantId, String caseId) {
    }

    @PostMapping(consumes = "application/json")
    public Map<String, String> notify(@RequestBody NotifyRequest request) {
        if (request.shipmentId() == null || request.shipmentId().isBlank()) {
            throw new IllegalArgumentException("shipmentId is required");
        }
        StubCommandBuilder.Dispatch dispatch =
                builder.notifyCustomer(request.shipmentId(), request.tenantId(), request.caseId());
        template.send(dispatch.topic(), request.shipmentId(), dispatch.envelope());
        return Map.of("commandId", dispatch.commandId(), "eventId", dispatch.eventId(), "topic", dispatch.topic());
    }
}