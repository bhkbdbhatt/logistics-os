package io.logisticsos.readmodel.projection;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.EventEnvelopeBuilder;
import io.logisticsos.events.kafka.PayloadAvro;
import io.logisticsos.events.kafka.SchemaResolver;

/** Produces ready-to-send envelopes for the projection ITs. */
final class ProjectionTestEnvelopes {

    private ProjectionTestEnvelopes() {
    }

    static EventEnvelope shipmentCreated(SchemaResolver schemas, String shipmentId, long occurredAt) {
        String json = """
                {
                  "shipmentId": "%s",
                  "externalRefs": [{"type":"PRO","value":"PRO-9"}],
                  "shipper": {"partyId":"CUST-1","role":"SHIPPER"},
                  "consignee": {"partyId":"CUST-2","role":"CONSIGNEE"},
                  "pickupLocation": {"locationId":"WH-AMS","type":"WAREHOUSE"},
                  "deliveryLocation": {"locationId":"STORE-BER","type":"STORE"},
                  "pickupWindow": {"start":%d,"end":%d},
                  "deliveryWindow": {"start":%d,"end":%d},
                  "serviceLevel": "PREMIUM",
                  "mode": "FTL",
                  "totalQuantity": {"value":12.0,"uom":"PLT"},
                  "totalWeightKg": 2400.0,
                  "status": "CREATED"
                }
                """.formatted(shipmentId, occurredAt, occurredAt + 86_400_000L,
                occurredAt + 172_800_000L, occurredAt + 259_200_000L);
        return envelope(schemas, "io.logisticsos.shipment.v1.ShipmentCreated", "shipment", shipmentId,
                json, occurredAt);
    }

    static EventEnvelope shipmentPlanned(SchemaResolver schemas, String shipmentId, long occurredAt) {
        String json = """
                {
                  "shipmentId": "%s",
                  "planId": "PLAN-7",
                  "mode": "FTL",
                  "plannedPickup": %d,
                  "plannedDelivery": %d,
                  "stops": [
                    {"sequence": 1, "location": {"locationId":"WH-AMS","type":"WAREHOUSE"},
                     "type": "PICKUP", "window": {"start":%d,"end":%d}, "legMode": "FTL"}
                  ],
                  "status": "PLANNED"
                }
                """.formatted(shipmentId, occurredAt + 86_400_000L, occurredAt + 259_200_000L,
                occurredAt, occurredAt + 86_400_000L);
        return envelope(schemas, "io.logisticsos.shipment.v1.ShipmentPlanned", "shipment", shipmentId,
                json, occurredAt);
    }

    static EventEnvelope shipmentDispatched(SchemaResolver schemas, String shipmentId, long occurredAt) {
        String json = """
                {
                  "shipmentId": "%s",
                  "dispatchedAt": %d,
                  "originLocation": {"locationId":"WH-AMS","type":"WAREHOUSE"},
                  "status": "DISPATCHED",
                  "vehicle": {"vehicleId":"TRK-1","type":"TRUCK"},
                  "driver": {"driverId":"DRV-5"},
                  "sealNumber": "SEAL-12"
                }
                """.formatted(shipmentId, occurredAt);
        return envelope(schemas, "io.logisticsos.shipment.v1.ShipmentDispatched", "shipment", shipmentId,
                json, occurredAt);
    }

    static EventEnvelope inventoryMove(SchemaResolver schemas, String inventoryId, long sequence,
                                       String moveType, double quantity, long occurredAt) {
        String json = """
                {
                  "inventoryId": "%s",
                  "sku": "SKU-500",
                  "moveType": "%s",
                  "moveSequence": %d,
                  "fromLocation": "A-01-B",
                  "toLocation": "C-09-A",
                  "quantity": {"value": %s, "uom": "PLT"},
                  "reasonCode": "REBALANCE",
                  "occurredAt": %d
                }
                """.formatted(inventoryId, moveType, sequence, quantity, occurredAt);
        return envelope(schemas, "io.logisticsos.wms.v1.InventoryMove", "inventory", inventoryId,
                json, occurredAt);
    }

    private static EventEnvelope envelope(SchemaResolver schemas, String payloadType, String aggregateType,
                                          String aggregateId, String payloadJson, long occurredAt) {
        var schema = schemas.resolve(payloadType + "-value");
        return new EventEnvelopeBuilder(occurredAt)
                .eventType(payloadType)
                .tenantId("acme")
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventVersion(1)
                .aggregateVersion(1)
                .occurredAt(occurredAt)
                .schemaSubject(payloadType + "-value")
                .payload(PayloadAvro.jsonToBytes(payloadJson, schema))
                .build();
    }
}