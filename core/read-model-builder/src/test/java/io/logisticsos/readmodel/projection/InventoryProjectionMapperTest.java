package io.logisticsos.readmodel.projection;

import static org.assertj.core.api.Assertions.assertThat;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.EventEnvelopeBuilder;
import io.logisticsos.events.kafka.PayloadAvro;
import io.logisticsos.events.kafka.SchemaResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InventoryProjectionMapperTest {

    private static final String MOVE_JSON = """
            {
              "inventoryId": "INV-77",
              "sku": "SKU-500",
              "moveType": "TRANSFER",
              "moveSequence": 3,
              "fromLocation": "A-01-B",
              "toLocation": "C-09-A",
              "quantity": {"value": 10.0, "uom": "PLT"},
              "reasonCode": "REBALANCE",
              "occurredAt": 1720000000000
            }
            """;

    private static SchemaResolver schemas;

    @BeforeAll
    static void setUp() {
        schemas = new io.logisticsos.events.kafka.ResourceSchemaResolver();
    }

    @Test
    void mapsInventoryMove() {
        var schema = schemas.resolve("io.logisticsos.wms.v1.InventoryMove-value");
        EventEnvelope envelope = new EventEnvelopeBuilder(1720000000000L)
                .eventType("io.logisticsos.wms.v1.InventoryMove")
                .tenantId("acme")
                .aggregateType("inventory")
                .aggregateId("INV-77")
                .eventVersion(1)
                .aggregateVersion(3)
                .occurredAt(1720000000000L)
                .schemaSubject("io.logisticsos.wms.v1.InventoryMove-value")
                .payload(PayloadAvro.jsonToBytes(MOVE_JSON, schema))
                .build();

        InventoryProjection p = InventoryProjectionMapper.from(envelope,
                new EnvelopePayloadDecoder(schemas).decode(envelope));

        assertThat(p.getInventoryId()).isEqualTo("INV-77");
        assertThat(p.getSku()).isEqualTo("SKU-500");
        assertThat(p.getLocation()).isEqualTo("C-09-A");
        assertThat(p.getQuantity()).isEqualTo(10.0);
        assertThat(p.getUom()).isEqualTo("PLT");
        assertThat(p.getMoveSequence()).isEqualTo(3);
        assertThat(p.getReasonCode()).isEqualTo("REBALANCE");
    }
}