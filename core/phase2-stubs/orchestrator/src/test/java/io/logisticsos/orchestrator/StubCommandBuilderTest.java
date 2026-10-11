package io.logisticsos.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.kafka.PayloadAvro;
import io.logisticsos.events.kafka.ResourceSchemaResolver;
import io.logisticsos.events.kafka.SchemaResolver;
import io.logisticsos.orchestrator.StubCommandBuilder.Dispatch;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

class StubCommandBuilderTest {

    private static final String COMMAND_SUBJECT = "io.logisticsos.orchestration.v1.OrchestrationCommand-value";

    private final SchemaResolver schemas = new ResourceSchemaResolver();
    private final OrchestratorProperties properties = new OrchestratorProperties();

    @Test
    void buildsIdempotentNotifyCustomerCommand() {
        StubCommandBuilder builder = new StubCommandBuilder(schemas, properties);
        Dispatch dispatch = builder.notifyCustomer("SH-1", null, "case-9");

        EventEnvelope envelope = dispatch.envelope();
        assertThat(envelope.eventType()).isEqualTo("io.logisticsos.orchestration.v1.OrchestrationCommand");
        assertThat(envelope.tenantId()).isEqualTo(properties.getDefaultTenant());
        assertThat(envelope.aggregateType()).isEqualTo("shipment");
        assertThat(envelope.aggregateId()).isEqualTo("SH-1");
        assertThat(envelope.schemaSubject()).isEqualTo(COMMAND_SUBJECT);
        assertThat(envelope.idempotencyKey()).isEqualTo("OrchestrationCommand:" + dispatch.commandId() + ":1");
        assertThat(dispatch.topic()).isEqualTo("orch.command.v1");

        GenericRecord command = PayloadAvro.fromBytes(envelope.payload(),
                schemas.resolve(COMMAND_SUBJECT));
        assertThat(command.get("commandId")).isEqualTo(dispatch.commandId());
        assertThat(command.get("commandType").toString()).isEqualTo("NOTIFY_CUSTOMER");
        assertThat(command.get("targetService")).isEqualTo("notification");
        assertThat(command.get("commandTargetId")).isEqualTo("SH-1");
        assertThat(command.get("caseId")).isEqualTo("case-9");
        assertThat(command.get("priority").toString()).isEqualTo("HIGH");

        GenericRecord subject = (GenericRecord) command.get("subject");
        @SuppressWarnings("unchecked")
        var shipmentIds = (GenericData.Array<String>) subject.get("shipmentIds");
        assertThat(shipmentIds).containsExactly("SH-1");
    }

    @Test
    void appliesExplicitTenantAndBlankCaseAsNull() {
        StubCommandBuilder builder = new StubCommandBuilder(schemas, properties);
        Dispatch dispatch = builder.notifyCustomer("SH-2", "acme", "  ");

        assertThat(dispatch.envelope().tenantId()).isEqualTo("acme");
        GenericRecord command = PayloadAvro.fromBytes(dispatch.envelope().payload(),
                schemas.resolve(COMMAND_SUBJECT));
        assertThat(command.get("caseId")).isNull();
    }

    @Test
    void resolvesSchemaFromContractResources() {
        Schema schema = schemas.resolve(COMMAND_SUBJECT);
        assertThat(schema.getFullName()).isEqualTo("io.logisticsos.orchestration.v1.OrchestrationCommand");
    }
}