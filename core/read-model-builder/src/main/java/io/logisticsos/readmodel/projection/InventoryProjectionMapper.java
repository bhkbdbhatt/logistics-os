package io.logisticsos.readmodel.projection;

import io.logisticsos.events.EventEnvelope;
import org.apache.avro.generic.GenericRecord;

/** Pure mapping of inventory movement payload events onto the {@link InventoryProjection} read model. */
public final class InventoryProjectionMapper {

    private InventoryProjectionMapper() {
    }

    public static InventoryProjection from(EventEnvelope envelope, GenericRecord payload) {
        InventoryProjection p = new InventoryProjection();
        p.setInventoryId(envelope.aggregateId());
        p.setSku(AvroText.of(payload.get("sku")));
        p.setMoveType(AvroText.of(payload.get("moveType")));
        Object moveSequence = payload.get("moveSequence");
        p.setMoveSequence(moveSequence instanceof Number n ? n.longValue() : 0L);
        Object toLocation = payload.get("toLocation");
        Object fromLocation = payload.get("fromLocation");
        p.setLocation(toLocation != null ? AvroText.of(toLocation) : AvroText.of(fromLocation));
        p.setReasonCode(AvroText.of(payload.get("reasonCode")));
        Object quantity = payload.get("quantity");
        if (quantity instanceof GenericRecord q) {
            p.setQuantity(q.get("value") instanceof Number n ? n.doubleValue() : 0d);
            p.setUom(AvroText.of(q.get("uom")));
        }
        Object occurredAt = payload.get("occurredAt");
        p.setUpdatedAt(occurredAt instanceof Number n ? n.longValue() : envelope.occurredAt());
        return p;
    }
}