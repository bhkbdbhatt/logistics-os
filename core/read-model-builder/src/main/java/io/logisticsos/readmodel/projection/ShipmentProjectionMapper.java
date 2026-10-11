package io.logisticsos.readmodel.projection;

import io.logisticsos.events.EventEnvelope;
import org.apache.avro.generic.GenericRecord;

/** Pure mapping of shipment payload events onto the {@link ShipmentProjection} read model. */
public final class ShipmentProjectionMapper {

    private ShipmentProjectionMapper() {
    }

    public static ShipmentProjection from(EventEnvelope envelope, GenericRecord payload) {
        ShipmentProjection p = new ShipmentProjection();
        p.setShipmentId(envelope.aggregateId());
        p.setEventType(envelope.eventType());
        p.setAggregateVersion(envelope.aggregateVersion());
        p.setUpdatedAt(envelope.occurredAt());
        p.setStatus(stringOr(payload.get("status")));
        switch (envelope.eventType()) {
            case "io.logisticsos.shipment.v1.ShipmentCreated" -> {
                p.setMode(stringOr(payload.get("mode")));
                p.setServiceLevel(stringOr(payload.get("serviceLevel")));
                p.setPickupLocationId(locationId(payload.get("pickupLocation")));
                p.setDeliveryLocationId(locationId(payload.get("deliveryLocation")));
                Object weight = payload.get("totalWeightKg");
                if (weight instanceof Number n) {
                    p.setTotalWeightKg(n.doubleValue());
                }
            }
            case "io.logisticsos.shipment.v1.ShipmentPlanned" -> {
                p.setPlanId(stringOr(payload.get("planId")));
                p.setMode(stringOr(payload.get("mode")));
            }
            case "io.logisticsos.shipment.v1.ShipmentDispatched" -> {
                p.setOriginLocationId(locationId(payload.get("originLocation")));
                putIf(p.getExtras(), "dispatchedAt", payload.get("dispatchedAt"));
                putIf(p.getExtras(), "vehicleId", field(payload, "vehicle", "vehicleId"));
                putIf(p.getExtras(), "driverId", field(payload, "driver", "driverId"));
                putIf(p.getExtras(), "sealNumber", payload.get("sealNumber"));
            }
            default -> {
                // scaffold: unknown shipment event types pass the base fields through
            }
        }
        return p;
    }

    private static void putIf(java.util.Map<String, String> target, String key, Object value) {
        if (value != null) {
            target.put(key, AvroText.of(value));
        }
    }

    private static Object field(GenericRecord record, String recordField, String nestedField) {
        Object nested = record == null ? null : record.get(recordField);
        return nested instanceof GenericRecord gr && gr.get(nestedField) != null ? gr.get(nestedField) : null;
    }

    private static String locationId(Object locationRef) {
        return locationRef instanceof GenericRecord gr ? stringOr(gr.get("locationId")) : null;
    }

    private static String stringOr(Object value) {
        return value == null ? null : AvroText.of(value);
    }
}