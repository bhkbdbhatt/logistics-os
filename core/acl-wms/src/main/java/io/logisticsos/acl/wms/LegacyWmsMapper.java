package io.logisticsos.acl.wms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.logisticsos.events.kafka.EnvelopeRouting;
import io.logisticsos.events.kafka.PayloadAvro;
import io.logisticsos.events.kafka.SchemaResolver;
import org.apache.avro.generic.GenericRecord;
import org.springframework.stereotype.Component;

/**
 * Pure mapping of legacy WMS vendor JSON onto canonical payload schemas. The legacy feed is a
 * simplified stand-in; each canonical payload is validated by construction through
 * {@link PayloadAvro#jsonToGeneric}.
 */
@Component
public class LegacyWmsMapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SchemaResolver schemas;

    public LegacyWmsMapper(SchemaResolver schemas) {
        this.schemas = schemas;
    }

    public ParsedEvent map(String rawJson) {
        JsonNode legacy;
        try {
            legacy = JSON.readTree(rawJson);
        } catch (Exception ex) {
            throw new IllegalArgumentException("legacy event is not valid JSON", ex);
        }
        String type = legacy.path("type").asText("");
        return switch (type) {
            case "ScanEvent" -> scan(legacy);
            case "WmsTaskUpdate" -> task(legacy);
            case "StockMove" -> stockMove(legacy);
            default -> throw new IllegalArgumentException("unsupported legacy WMS event type: " + type);
        };
    }

    private ParsedEvent scan(JsonNode legacy) {
        ObjectNode canonical = JSON.createObjectNode();
        canonical.put("scanId", text(legacy, "scanId"));
        canonical.put("locationId", text(legacy, "locationId"));
        canonical.put("deviceId", text(legacy, "deviceId"));
        canonical.put("scanAt", legacy.path("scanAt").asLong(1720000000000L));
        canonical.put("scanType", legacy.path("scanType").asText("PICK"));
        canonical.put("refType", legacy.path("refType").asText("INVENTORY"));
        canonical.put("refId", text(legacy, "refId"));
        canonical.put("operatorId", text(legacy, "operatorId"));
        canonical.set("quantity", quantity(legacy));
        return parse("io.logisticsos.obs.v1.Scan", canonical,
                text(legacy, "deviceId"),
                EnvelopeRouting.topic(EnvelopeRouting.Layer.OBS, "scan", 1));
    }

    private ParsedEvent task(JsonNode legacy) {
        ObjectNode canonical = JSON.createObjectNode();
        canonical.put("taskId", text(legacy, "taskId"));
        canonical.put("taskType", legacy.path("taskType").asText("PICK"));
        canonical.put("status", legacy.path("status").asText("ASSIGNED"));
        canonical.put("nodeId", text(legacy, "nodeId"));
        if (legacy.has("orderId")) {
            canonical.put("orderId", legacy.path("orderId").asText());
        }
        if (legacy.has("locationId")) {
            canonical.put("locationId", legacy.path("locationId").asText());
        }
        if (legacy.hasNonNull("quantity")) {
            canonical.set("quantity", quantity(legacy));
        }
        if (legacy.has("assignedWorkerId")) {
            canonical.put("assignedWorkerId", legacy.path("assignedWorkerId").asText());
        }
        if (legacy.has("startedAt")) {
            canonical.put("startedAt", legacy.path("startedAt").asLong());
        }
        if (legacy.has("completedAt")) {
            canonical.put("completedAt", legacy.path("completedAt").asLong());
        }
        canonical.put("occurredAt", legacy.path("occurredAt").asLong(1720000000000L));
        return parse("io.logisticsos.wms.v1.WmsTaskEvent", canonical,
                text(legacy, "nodeId"),
                EnvelopeRouting.topic(EnvelopeRouting.Layer.WMS, "task", "events", 1));
    }

    private ParsedEvent stockMove(JsonNode legacy) {
        ObjectNode canonical = JSON.createObjectNode();
        canonical.put("inventoryId", text(legacy, "inventoryId"));
        canonical.put("sku", text(legacy, "sku"));
        canonical.put("moveType", legacy.path("moveType").asText("TRANSFER"));
        canonical.put("moveSequence", legacy.path("moveSequence").asLong());
        if (legacy.has("fromLocation")) {
            canonical.put("fromLocation", legacy.path("fromLocation").asText());
        }
        if (legacy.has("toLocation")) {
            canonical.put("toLocation", legacy.path("toLocation").asText());
        }
        canonical.set("quantity", quantity(legacy));
        if (legacy.has("reasonCode")) {
            canonical.put("reasonCode", legacy.path("reasonCode").asText());
        }
        canonical.put("occurredAt", legacy.path("occurredAt").asLong(1720000000000L));
        return parse("io.logisticsos.wms.v1.InventoryMove", canonical,
                text(legacy, "inventoryId"),
                EnvelopeRouting.topic(EnvelopeRouting.Layer.WMS, "inventory", "events", 1));
    }

    private ParsedEvent parse(String payloadType, ObjectNode canonical, String aggregateId, String topic) {
        var schema = schemas.resolve(payloadType + "-value");
        GenericRecord record = PayloadAvro.jsonToGeneric(canonical.toString(), schema);
        return new ParsedEvent(payloadType, record, aggregateId, topic);
    }

    private static ObjectNode quantity(JsonNode legacy) {
        JsonNode quantity = legacy.path("quantity");
        ObjectNode q = JSON.createObjectNode();
        q.put("value", quantity.path("value").asDouble(quantity.path("qty").asDouble(1d)));
        q.put("uom", quantity.path("uom").asText("EA"));
        return q;
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("legacy event missing required field: " + field);
        }
        return value;
    }

    public record ParsedEvent(String payloadType, GenericRecord record, String aggregateId, String topic) {

        public String schemaSubject() {
            return payloadType + "-value";
        }
    }
}