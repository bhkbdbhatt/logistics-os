package io.logisticsos.acl.wms;

import static org.assertj.core.api.Assertions.assertThat;

import io.logisticsos.events.kafka.PayloadAvro;
import io.logisticsos.events.kafka.SchemaResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LegacyWmsMapperTest {

    private static final String SCAN_JSON = """
            {
              "type": "ScanEvent",
              "scanId": "SCN-1",
              "locationId": "WH-AMS",
              "deviceId": "SCANNER-9",
              "scanAt": 1720000000000,
              "scanType": "PICK",
              "refType": "INVENTORY",
              "refId": "INV-77",
              "operatorId": "OP-3",
              "quantity": {"value": 2.0, "uom": "EA"}
            }
            """;

    private static final String TASK_JSON = """
            {
              "type": "WmsTaskUpdate",
              "taskId": "TASK-11",
              "taskType": "PICK",
              "status": "COMPLETED",
              "nodeId": "WH-AMS",
              "orderId": "SO-505",
              "locationId": "A-01-B",
              "quantity": {"value": 4.0, "uom": "CS"},
              "assignedWorkerId": "W-8",
              "startedAt": 1720000000000,
              "completedAt": 1720003600000
            , "occurredAt": 1720003600000 }
            """;

    private static final String STOCK_MOVE_JSON = """
            {
              "type": "StockMove",
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
    void mapsScanEventToObsScan() {
        LegacyWmsMapper.ParsedEvent event = new LegacyWmsMapper(schemas).map(SCAN_JSON);

        assertThat(event.payloadType()).isEqualTo("io.logisticsos.obs.v1.Scan");
        assertThat(event.topic()).isEqualTo("obs.scan.v1");
        assertThat(event.aggregateId()).isEqualTo("SCANNER-9");
        var record = PayloadAvro.fromBytes(PayloadAvro.toBytes(event.record()),
                schemas.resolve(event.schemaSubject()));
        assertThat(record.get("scanId").toString()).isEqualTo("SCN-1");
        assertThat(record.get("scanType").toString()).isEqualTo("PICK");
        assertThat(record.get("refId").toString()).isEqualTo("INV-77");
    }

    @Test
    void mapsTaskUpdateToWmsTaskEvent() {
        LegacyWmsMapper.ParsedEvent event = new LegacyWmsMapper(schemas).map(TASK_JSON);

        assertThat(event.payloadType()).isEqualTo("io.logisticsos.wms.v1.WmsTaskEvent");
        assertThat(event.topic()).isEqualTo("wms.task.events.v1");
        assertThat(event.aggregateId()).isEqualTo("WH-AMS");
        var record = PayloadAvro.fromBytes(PayloadAvro.toBytes(event.record()),
                schemas.resolve(event.schemaSubject()));
        assertThat(record.get("taskId").toString()).isEqualTo("TASK-11");
        assertThat(record.get("status").toString()).isEqualTo("COMPLETED");
        assertThat(record.get("orderId").toString()).isEqualTo("SO-505");
        assertThat(record.get("assignedWorkerId").toString()).isEqualTo("W-8");
    }

    @Test
    void mapsStockMoveToInventoryMove() {
        LegacyWmsMapper.ParsedEvent event = new LegacyWmsMapper(schemas).map(STOCK_MOVE_JSON);

        assertThat(event.payloadType()).isEqualTo("io.logisticsos.wms.v1.InventoryMove");
        assertThat(event.topic()).isEqualTo("wms.inventory.events.v1");
        assertThat(event.aggregateId()).isEqualTo("INV-77");
        var record = PayloadAvro.fromBytes(PayloadAvro.toBytes(event.record()),
                schemas.resolve(event.schemaSubject()));
        assertThat(record.get("sku").toString()).isEqualTo("SKU-500");
        assertThat(record.get("moveSequence")).isEqualTo(3L);
        assertThat(record.get("toLocation").toString()).isEqualTo("C-09-A");
    }

    @Test
    void rejectsUnknownLegacyType() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        new LegacyWmsMapper(schemas).map("{\"type\":\"Nope\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported legacy WMS event type: Nope");
    }
}