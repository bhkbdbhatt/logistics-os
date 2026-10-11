package io.logisticsos.orchestrator;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.EventEnvelopeBuilder;
import io.logisticsos.events.Ulid;
import io.logisticsos.events.kafka.PayloadAvro;
import io.logisticsos.events.kafka.SchemaResolver;
import java.util.List;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

/**
 * Builds idempotent {@code orch.command.v1} {@code NOTIFY_CUSTOMER} commands
 * ({@code commandId} is the dedupe key for target services, per schemas/orchestration-events.md).
 *
 * <p>Stub scope only: this single builder mirrors what control-tower's one-click actions already
 * produce; the real orchestrator replaces it with the full command + saga layer in 0.3.0.
 */
public final class StubCommandBuilder {

    private static final String EVENT_TYPE = "io.logisticsos.orchestration.v1.OrchestrationCommand";
    private static final String COMMAND_SUBJECT = EVENT_TYPE + "-value";

    private final SchemaResolver schemas;
    private final OrchestratorProperties properties;

    public StubCommandBuilder(SchemaResolver schemas, OrchestratorProperties properties) {
        this.schemas = schemas;
        this.properties = properties;
    }

    public record Dispatch(String commandId, String eventId, String topic, EventEnvelope envelope) {
    }

    public Dispatch notifyCustomer(String shipmentId, String tenantId, String caseId) {
        long issuedAt = System.currentTimeMillis();
        Schema schema = schemas.resolve(COMMAND_SUBJECT);
        String commandId = Ulid.generate(issuedAt);

        GenericRecord command = new GenericData.Record(schema);
        command.put("commandId", commandId);
        command.put("commandType", enumValue(schema, "commandType", "NOTIFY_CUSTOMER"));
        command.put("targetService", "notification");
        command.put("commandTargetId", shipmentId);
        command.put("caseId", blankToNull(caseId));
        command.put("decisionId", null);

        Schema subjectSchema = schema.getField("subject").schema();
        GenericRecord subject = new GenericData.Record(subjectSchema);
        subject.put("shipmentIds", new GenericData.Array<>(
                arrayBranch(subjectSchema.getField("shipmentIds").schema()), List.of(shipmentId)));
        subject.put("loadIds", null);
        subject.put("orderIds", null);
        subject.put("inventoryIds", null);
        subject.put("locations", null);
        subject.put("nodeId", null);
        command.put("subject", subject);

        Schema paramSchema = schema.getField("params").schema().getElementType();
        command.put("params", new GenericData.Array<>(schema.getField("params").schema(),
                List.of(param("action", "notify-customer", paramSchema))));
        command.put("priority", enumValue(schema, "priority", "HIGH"));
        command.put("deadline", null);
        command.put("issuedAt", issuedAt);

        String tenant = blankToNull(tenantId) == null ? properties.getDefaultTenant() : tenantId;
        EventEnvelope envelope = new EventEnvelopeBuilder(issuedAt)
                .eventType(EVENT_TYPE)
                .tenantId(tenant)
                .aggregateType("shipment")
                .aggregateId(shipmentId)
                .producer(properties.getProducer())
                .correlationId("shipment:" + shipmentId)
                .idempotencyKey("OrchestrationCommand:" + commandId + ":1")
                .schemaSubject(COMMAND_SUBJECT)
                .payload(PayloadAvro.toBytes(command))
                .build();
        return new Dispatch(commandId, envelope.eventId(), properties.getCommandTopic(), envelope);
    }

    private static GenericData.EnumSymbol enumValue(Schema schema, String field, String symbol) {
        return new GenericData.EnumSymbol(schema.getField(field).schema(), symbol);
    }

    private static GenericRecord param(String key, String value, Schema paramSchema) {
        GenericRecord param = new GenericData.Record(paramSchema);
        param.put("key", key);
        param.put("value", value);
        return param;
    }

    private static Schema arrayBranch(Schema schema) {
        if (schema.getType() == Schema.Type.UNION) {
            for (Schema branch : schema.getTypes()) {
                if (branch.getType() == Schema.Type.ARRAY) {
                    return branch;
                }
            }
        }
        return schema;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}