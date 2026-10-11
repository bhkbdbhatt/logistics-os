package io.logisticsos.bffops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Pure JSON composition for the ops surface. Transforms and aggregates only — no business rules
 * (L6 BFF policy, ADR-0003). Decoration: resolves MDM location names for route anchors.
 */
@Component
public class OpsViewMapper {

    private final ObjectMapper json;

    public OpsViewMapper(ObjectMapper json) {
        this.json = json;
    }

    public ObjectNode overview(Optional<JsonNode> shipments, Optional<JsonNode> inventory,
            Optional<JsonNode> alerts, Optional<JsonNode> towerHealth, long fetchedAt) {
        ObjectNode root = json.createObjectNode();
        root.put("fetchedAt", fetchedAt);
        root.set("shipments", shipments.orElse(array()));
        root.set("inventory", inventory.orElse(array()));
        root.set("alerts", alerts.orElse(array()));
        root.put("sourcesAvailable_readModel", shipments.isPresent());
        root.put("sourcesAvailable_tower", alerts.isPresent());
        towerHealth.ifPresent(health -> root.set("freshness", health));
        return root;
    }

    public ObjectNode shipmentDetail(JsonNode shipment, Optional<JsonNode> pickup, Optional<JsonNode> delivery) {
        ObjectNode root = json.createObjectNode();
        root.set("shipment", shipment);
        JsonNode pickupId = shipment.get("pickupLocationId");
        JsonNode deliveryId = shipment.get("deliveryLocationId");
        root.put("pickupLocationName", pickupId == null ? null : nameOf(pickup));
        root.put("deliveryLocationName", deliveryId == null ? null : nameOf(delivery));
        return root;
    }

    public ObjectNode health(Optional<JsonNode> readModel, Optional<JsonNode> tower, Optional<JsonNode> mdm) {
        ObjectNode root = json.createObjectNode();
        root.put("readModel", readModel.isPresent());
        root.put("controlTower", tower.isPresent());
        root.put("mdm", mdm.isPresent());
        root.put("status", "ok");
        return root;
    }

    private ArrayNode array() {
        return json.createArrayNode();
    }

    private String nameOf(Optional<JsonNode> entry) {
        return entry.map(node -> node.get("canonicalJson"))
                .filter(JsonNode::isTextual)
                .map(node -> {
                    try {
                        return json.readTree(node.asText()).get("name");
                    } catch (Exception ex) {
                        return null;
                    }
                })
                .map(JsonNode::asText)
                .orElse(null);
    }
}