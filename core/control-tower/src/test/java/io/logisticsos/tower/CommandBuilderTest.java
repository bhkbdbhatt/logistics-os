package io.logisticsos.tower;

import static org.assertj.core.api.Assertions.assertThat;

import io.logisticsos.events.kafka.PayloadAvro;
import io.logisticsos.events.kafka.ResourceSchemaResolver;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

class CommandBuilderTest {

    private final ResourceSchemaResolver resolver = new ResourceSchemaResolver();
    private final CommandBuilder builder = new CommandBuilder(resolver, props());

    private static TowerProperties props() {
        TowerProperties p = new TowerProperties();
        p.setDefaultTenant("tenant-1");
        p.setCommandTopic("orch.command.v1");
        return p;
    }

    @Test
    void notifyCustomerBuildsIdempotentCommandEnvelope() {
        CommandBuilder.Dispatch dispatch = builder.notifyCustomer("SHP-1", "tenant-9", "CASE-2", 1720000000000L);

        assertThat(dispatch.topic()).isEqualTo("orch.command.v1");
        assertThat(dispatch.commandId()).hasSize(26);
        assertThat(dispatch.envelope().aggregateId()).isEqualTo("SHP-1");
        assertThat(dispatch.envelope().tenantId()).isEqualTo("tenant-9");
        assertThat(dispatch.envelope().correlationId()).isEqualTo("shipment:SHP-1");
        assertThat(dispatch.envelope().idempotencyKey())
                .isEqualTo("OrchestrationCommand:" + dispatch.commandId() + ":1");

        Schema schema = resolver.resolve("io.logisticsos.orchestration.v1.OrchestrationCommand-value");
        GenericRecord command = PayloadAvro.fromBytes(dispatch.envelope().payload(), schema);
        assertThat(String.valueOf(command.get("commandId"))).isEqualTo(dispatch.commandId());
        assertThat(command.get("commandType").toString()).isEqualTo("NOTIFY_CUSTOMER");
        assertThat(command.get("targetService")).isEqualTo("notification");
        assertThat(command.get("commandTargetId")).isEqualTo("SHP-1");
        assertThat(command.get("caseId")).isEqualTo("CASE-2");
        assertThat(command.get("priority").toString()).isEqualTo("HIGH");
        assertThat(command.get("issuedAt")).isEqualTo(1720000000000L);

        GenericRecord subject = (GenericRecord) command.get("subject");
        assertThat(subject.get("shipmentIds").toString()).contains("SHP-1");

        @SuppressWarnings("unchecked")
        Iterable<GenericRecord> params = (Iterable<GenericRecord>) command.get("params");
        GenericRecord action = params.iterator().next();
        assertThat(action.get("key")).isEqualTo("action");
        assertThat(action.get("value")).isEqualTo("notify-customer");
    }

    @Test
    void defaultTenantUsedWhenHeaderAbsent() {
        CommandBuilder.Dispatch dispatch = builder.notifyCustomer("SHP-1", null, null, 1720000000000L);
        assertThat(dispatch.envelope().tenantId()).isEqualTo("tenant-1");
    }
}