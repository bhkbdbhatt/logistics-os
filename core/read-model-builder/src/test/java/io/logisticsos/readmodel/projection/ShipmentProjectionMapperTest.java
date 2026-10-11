package io.logisticsos.readmodel.projection;

import static org.assertj.core.api.Assertions.assertThat;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.EventEnvelopeBuilder;
import io.logisticsos.events.kafka.PayloadAvro;
import io.logisticsos.events.kafka.SchemaResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ShipmentProjectionMapperTest {

    private static final String CREATED_JSON = """
            {
              "shipmentId": "SH-100",
              "externalRefs": [{"type":"PRO","value":"PRO-9"}],
              "shipper": {"partyId":"CUST-1","role":"SHIPPER"},
              "consignee": {"partyId":"CUST-2","role":"CONSIGNEE"},
              "pickupLocation": {"locationId":"WH-AMS","type":"WAREHOUSE"},
              "deliveryLocation": {"locationId":"STORE-BER","type":"STORE"},
              "pickupWindow": {"start":1720000000000,"end":1720086400000},
              "deliveryWindow": {"start":1720086400000,"end":1720172800000},
              "serviceLevel": "PREMIUM",
              "mode": "FTL",
              "totalQuantity": {"value":12.0,"uom":"PLT"},
              "totalWeightKg": 2400.0,
              "status": "CREATED"
            }
            """;

    private static final String DISPATCHED_JSON = """
            {
              "shipmentId": "SH-100",
              "dispatchedAt": 1720000000000,
              "originLocation": {"locationId":"WH-AMS","type":"WAREHOUSE"},
              "status": "DISPATCHED",
              "vehicle": {"vehicleId":"TRK-1","type":"TRUCK"},
              "driver": {"driverId":"DRV-5"},
              "sealNumber": "SEAL-12"
            }
            """;

    private static SchemaResolver schemas;

    @BeforeAll
    static void setUp() {
        schemas = new io.logisticsos.events.kafka.ResourceSchemaResolver();
    }

    @Test
    void mapsCreatedPayload() {
        EventEnvelope envelope = envelope("io.logisticsos.shipment.v1.ShipmentCreated",
                CREATED_JSON, "SH-100", 1720000000000L);
        ShipmentProjection p = ShipmentProjectionMapper.from(envelope,
                new EnvelopePayloadDecoder(schemas).decode(envelope));

        assertThat(p.getShipmentId()).isEqualTo("SH-100");
        assertThat(p.getStatus()).isEqualTo("CREATED");
        assertThat(p.getMode()).isEqualTo("FTL");
        assertThat(p.getServiceLevel()).isEqualTo("PREMIUM");
        assertThat(p.getPickupLocationId()).isEqualTo("WH-AMS");
        assertThat(p.getDeliveryLocationId()).isEqualTo("STORE-BER");
        assertThat(p.getTotalWeightKg()).isEqualTo(2400.0);
        assertThat(p.getUpdatedAt()).isEqualTo(1720000000000L);
    }

    @Test
    void mapsDispatchedPayload() {
        EventEnvelope envelope = envelope("io.logisticsos.shipment.v1.ShipmentDispatched",
                DISPATCHED_JSON, "SH-100", 1720000000000L);
        ShipmentProjection p = ShipmentProjectionMapper.from(envelope,
                new EnvelopePayloadDecoder(schemas).decode(envelope));

        assertThat(p.getStatus()).isEqualTo("DISPATCHED");
        assertThat(p.getOriginLocationId()).isEqualTo("WH-AMS");
        assertThat(p.getExtras()).containsEntry("vehicleId", "TRK-1");
        assertThat(p.getExtras()).containsEntry("driverId", "DRV-5");
    }

    static EventEnvelope envelope(String payloadType, String payloadJson, String aggregateId, long occurredAt) {
        var schema = schemas.resolve(payloadType + "-value");
        return new EventEnvelopeBuilder(occurredAt)
                .eventType(payloadType)
                .tenantId("acme")
                .aggregateType("shipment")
                .aggregateId(aggregateId)
                .eventVersion(1)
                .aggregateVersion(1)
                .occurredAt(occurredAt)
                .schemaSubject(payloadType + "-value")
                .payload(PayloadAvro.jsonToBytes(payloadJson, schema))
                .build();
    }
}