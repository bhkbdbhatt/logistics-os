package io.logisticsos.acl.tms;

import static org.assertj.core.api.Assertions.assertThat;

import io.logisticsos.events.kafka.PayloadAvro;
import io.logisticsos.events.kafka.SchemaResolver;
import org.apache.avro.util.Utf8;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LegacyTmsMapperTest {

    private static final String CREATED_JSON = """
            {
              "type": "ShipmentCreated",
              "shipmentId": "SH-100",
              "proNumber": "PRO-9",
              "customerRef": "PO-42",
              "shipperId": "CUST-1",
              "shipperName": "Northwind",
              "consigneeId": "CUST-2",
              "pickupLocationId": "WH-AMS",
              "pickupFacilityName": "AMS DC",
              "pickupGeo": {"lat": 52.37, "lon": 4.90},
              "deliveryLocationId": "STORE-BER",
              "deliveryEarliest": 1720000000000,
              "deliveryLatest": 1720086400000,
              "pickupEarliest": 1720000000000,
              "pickupLatest": 1720086400000,
              "serviceLevel": "PREMIUM",
              "mode": "FTL",
              "quantity": 12.0,
              "uom": "PLT",
              "totalWeightKg": 2400.0
            }
            """;

    private static final String PLANNED_JSON = """
            {
              "type": "ShipmentPlanned",
              "shipmentId": "SH-100",
              "planId": "PLAN-7",
              "mode": "FTL",
              "plannedPickup": 1720000000000,
              "plannedDelivery": 1720086400000,
              "stops": [
                {"sequence": 1, "locationId": "WH-AMS", "type": "PICKUP",
                 "start": 1720000000000, "end": 1720086400000, "legMode": "FTL"},
                {"sequence": 2, "locationId": "STORE-BER", "type": "DELIVERY",
                 "start": 1720000000000, "end": 1720086400000, "legMode": "FTL"}
              ]
            }
            """;

    private static final String DISPATCHED_JSON = """
            {
              "type": "ShipmentDispatched",
              "shipmentId": "SH-100",
              "dispatchedAt": 1720000000000,
              "originLocationId": "WH-AMS",
              "vehicleId": "TRK-1",
              "driverId": "DRV-5",
              "sealNumber": "SEAL-12"
            }
            """;

    private static SchemaResolver schemas;

    @BeforeAll
    static void setUp() {
        schemas = new io.logisticsos.events.kafka.ResourceSchemaResolver();
    }

    @Test
    void mapsCarrierCreatedEvent() {
        LegacyTmsMapper.ParsedEvent event = new LegacyTmsMapper(schemas).map(CREATED_JSON);

        assertThat(event.payloadType()).isEqualTo("io.logisticsos.shipment.v1.ShipmentCreated");
        assertThat(event.aggregateId()).isEqualTo("SH-100");
        var record = PayloadAvro.fromBytes(PayloadAvro.toBytes(event.record()),
                schemas.resolve(event.schemaSubject()));
        assertThat(record.get("shipmentId")).isEqualTo(new Utf8("SH-100"));
        assertThat(record.get("status").toString()).isEqualTo("CREATED");
        assertThat(record.get("mode").toString()).isEqualTo("FTL");
        assertThat(record.get("serviceLevel").toString()).isEqualTo("PREMIUM");
        assertThat(record.get("externalRefs").toString())
                .contains("PRO-9").contains("PO-42");
        assertThat(record.get("shipper").toString()).contains("CUST-1");
        assertThat(record.get("deliveryLocation").toString()).contains("STORE-BER");
        assertThat(((org.apache.avro.generic.GenericRecord) record.get("pickupWindow")).get("start"))
                .isEqualTo(1720000000000L);
    }

    @Test
    void mapsCarrierPlannedEvent() {
        LegacyTmsMapper.ParsedEvent event = new LegacyTmsMapper(schemas).map(PLANNED_JSON);

        assertThat(event.payloadType()).isEqualTo("io.logisticsos.shipment.v1.ShipmentPlanned");
        var record = PayloadAvro.fromBytes(PayloadAvro.toBytes(event.record()),
                schemas.resolve(event.schemaSubject()));
        assertThat(record.get("planId").toString()).isEqualTo("PLAN-7");
        assertThat(record.get("stops").toString()).contains("WH-AMS").contains("STORE-BER");
        assertThat(((java.util.List<?>) record.get("stops"))).hasSize(2);
    }

    @Test
    void mapsCarrierDispatchedEvent() {
        LegacyTmsMapper.ParsedEvent event = new LegacyTmsMapper(schemas).map(DISPATCHED_JSON);

        assertThat(event.payloadType()).isEqualTo("io.logisticsos.shipment.v1.ShipmentDispatched");
        var record = PayloadAvro.fromBytes(PayloadAvro.toBytes(event.record()),
                schemas.resolve(event.schemaSubject()));
        assertThat(record.get("status").toString()).isEqualTo("DISPATCHED");
        assertThat(record.get("originLocation").toString()).contains("WH-AMS");
        assertThat(record.get("vehicle").toString()).contains("TRK-1");
        assertThat(record.get("sealNumber").toString()).isEqualTo("SEAL-12");
    }

    @Test
    void rejectsUnknownLegacyType() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        new LegacyTmsMapper(schemas).map("{\"type\":\"Mystery\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported legacy TMS event type: Mystery");
    }
}