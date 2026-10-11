package io.logisticsos.acl.tms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.logisticsos.events.kafka.PayloadAvro;
import io.logisticsos.events.kafka.SchemaResolver;
import java.util.ArrayList;
import java.util.List;
import org.apache.avro.generic.GenericRecord;
import org.springframework.stereotype.Component;

/**
 * Pure mapping of legacy TMS vendor JSON onto canonical shipment payload schemas. The legacy feed
 * is a simplified stand-in; each canonical payload is produced "by construction" as canonical JSON
 * and validated through {@link PayloadAvro#jsonToGeneric}.
 */
@Component
public class LegacyTmsMapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SchemaResolver schemas;

    public LegacyTmsMapper(SchemaResolver schemas) {
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
            case "ShipmentCreated" -> created(legacy);
            case "ShipmentPlanned" -> planned(legacy);
            case "ShipmentDispatched" -> dispatched(legacy);
            default -> throw new IllegalArgumentException("unsupported legacy TMS event type: " + type);
        };
    }

    private ParsedEvent created(JsonNode legacy) {
        ObjectNode canonical = JSON.createObjectNode();
        canonical.put("shipmentId", text(legacy, "shipmentId"));
        canonical.set("externalRefs", externalRefs(legacy));
        canonical.set("shipper", party(legacy, "shipperId", "SHIPPER", "shipperName"));
        canonical.set("consignee", party(legacy, "consigneeId", "CONSIGNEE", "consigneeName"));
        canonical.set("pickupLocation", location(legacy.path("pickupLocationId").asText(),
                legacy.path("pickupFacilityName").asText(null), legacy.path("pickupGeo")));
        canonical.set("deliveryLocation", location(legacy.path("deliveryLocationId").asText(),
                legacy.path("deliveryFacilityName").asText(null), legacy.path("deliveryGeo")));
        canonical.set("pickupWindow", window(legacy, "pickupEarliest", "pickupLatest"));
        canonical.set("deliveryWindow", window(legacy, "deliveryEarliest", "deliveryLatest"));
        canonical.put("serviceLevel", legacy.path("serviceLevel").asText("STANDARD"));
        canonical.put("mode", legacy.path("mode").asText("FTL"));
        canonical.set("totalQuantity", quantity(legacy));
        canonical.put("totalWeightKg", legacy.path("totalWeightKg").asDouble(0d));
        canonical.put("status", "CREATED");
        if (legacy.has("specialInstructions")) {
            canonical.put("specialInstructions", legacy.path("specialInstructions").asText());
        }
        return parse("io.logisticsos.shipment.v1.ShipmentCreated", canonical, shipmentId(legacy));
    }

    private ParsedEvent planned(JsonNode legacy) {
        ObjectNode canonical = JSON.createObjectNode();
        canonical.put("shipmentId", text(legacy, "shipmentId"));
        canonical.put("planId", text(legacy, "planId"));
        canonical.put("mode", legacy.path("mode").asText("FTL"));
        canonical.put("plannedPickup", legacy.path("plannedPickup").asLong(1720000000000L));
        canonical.put("plannedDelivery", legacy.path("plannedDelivery").asLong(1720000000000L));
        ArrayNode stops = canonical.putArray("stops");
        for (JsonNode stop : legacy.path("stops")) {
            ObjectNode s = stops.addObject();
            s.put("sequence", stop.path("sequence").asInt());
            s.set("location", location(stop.path("locationId").asText(), null, null));
            s.put("type", stop.path("type").asText("PICKUP"));
            s.set("window", window(stop, "start", "end"));
            s.put("legMode", stop.path("legMode").asText("FTL"));
        }
        canonical.put("status", "PLANNED");
        return parse("io.logisticsos.shipment.v1.ShipmentPlanned", canonical, shipmentId(legacy));
    }

    private ParsedEvent dispatched(JsonNode legacy) {
        ObjectNode canonical = JSON.createObjectNode();
        canonical.put("shipmentId", text(legacy, "shipmentId"));
        canonical.put("dispatchedAt", legacy.path("dispatchedAt").asLong(1720000000000L));
        canonical.set("originLocation", location(text(legacy, "originLocationId"), null, null));
        canonical.put("status", "DISPATCHED");
        if (legacy.has("vehicleId")) {
            ObjectNode vehicle = canonical.putObject("vehicle");
            vehicle.put("vehicleId", legacy.path("vehicleId").asText());
        }
        if (legacy.has("driverId")) {
            ObjectNode driver = canonical.putObject("driver");
            driver.put("driverId", legacy.path("driverId").asText());
        }
        if (legacy.has("sealNumber")) {
            canonical.put("sealNumber", legacy.path("sealNumber").asText());
        }
        return parse("io.logisticsos.shipment.v1.ShipmentDispatched", canonical, shipmentId(legacy));
    }

    private ParsedEvent parse(String payloadType, ObjectNode canonical, String shipmentId) {
        var schema = schemas.resolve(payloadType + "-value");
        GenericRecord record = PayloadAvro.jsonToGeneric(canonical.toString(), schema);
        return new ParsedEvent(payloadType, record, shipmentId);
    }

    private static ArrayNode externalRefs(JsonNode legacy) {
        ArrayNode refs = JSON.createArrayNode();
        if (legacy.has("proNumber")) {
            refs.addObject().put("type", "PRO").put("value", legacy.path("proNumber").asText());
        }
        if (legacy.has("customerRef")) {
            refs.addObject().put("type", "CUSTOMER").put("value", legacy.path("customerRef").asText());
        }
        return refs;
    }

    private static ObjectNode party(JsonNode legacy, String idField, String role, String nameField) {
        ObjectNode party = JSON.createObjectNode();
        party.put("partyId", text(legacy, idField));
        party.put("role", role);
        if (legacy.has(nameField)) {
            party.put("name", legacy.path(nameField).asText());
        }
        return party;
    }

    private static ObjectNode location(String locationId, String name, JsonNode geo) {
        ObjectNode location = JSON.createObjectNode();
        location.put("locationId", locationId == null || locationId.isEmpty() ? "UNKNOWN" : locationId);
        if (name != null) {
            location.put("name", name);
        }
        if (geo != null && geo.hasNonNull("lat") && geo.hasNonNull("lon")) {
            ObjectNode g = location.putObject("geo");
            g.put("lat", geo.path("lat").asDouble());
            g.put("lon", geo.path("lon").asDouble());
        }
        return location;
    }

    private static ObjectNode window(JsonNode source, String startField, String endField) {
        ObjectNode window = JSON.createObjectNode();
        window.put("start", source.path(startField).asLong(1720000000000L));
        window.put("end", source.path(endField).asLong(1720000000000L));
        return window;
    }

    private static ObjectNode quantity(JsonNode legacy) {
        ObjectNode quantity = JSON.createObjectNode();
        quantity.put("value", legacy.path("quantity").asDouble(1d));
        quantity.put("uom", legacy.path("uom").asText("EA"));
        return quantity;
    }

    private static String shipmentId(JsonNode legacy) {
        String id = legacy.path("shipmentId").asText("");
        if (id.isEmpty()) {
            throw new IllegalArgumentException("legacy event missing shipmentId");
        }
        return id;
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("legacy event missing required field: " + field);
        }
        return value;
    }

    public record ParsedEvent(String payloadType, GenericRecord record, String aggregateId) {

        public String schemaSubject() {
            return payloadType + "-value";
        }
    }

    // referenced by tests / future decoders; keeps mapper stateless
    public List<String> supportedTypes() {
        List<String> types = new ArrayList<>();
        types.add("ShipmentCreated");
        types.add("ShipmentPlanned");
        types.add("ShipmentDispatched");
        return types;
    }
}