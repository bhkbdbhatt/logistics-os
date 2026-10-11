package io.logisticsos.bffops;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OpsViewMapperTest {

    private final ObjectMapper json = new ObjectMapper();
    private final OpsViewMapper mapper = new OpsViewMapper(json);

    @Test
    void overviewComposesSourcesAndMarksAvailability() throws Exception {
        Optional<JsonNode> shipments = Optional.of(json.readTree("[{\"shipmentId\":\"SHP-1\",\"status\":\"DISPATCHED\"}]"));
        Optional<JsonNode> inventory = Optional.of(json.readTree("[{\"inventoryId\":\"INV-1\",\"sku\":\"SKU-1\"}]"));
        Optional<JsonNode> alerts = Optional.empty();
        Optional<JsonNode> health = Optional.of(json.readTree("{\"shipmentCount\":1,\"status\":\"ok\"}"));

        JsonNode overview = mapper.overview(shipments, inventory, alerts, health, 1234L);

        assertThat(overview.get("fetchedAt").asLong()).isEqualTo(1234L);
        assertThat(overview.get("shipments").size()).isEqualTo(1);
        assertThat(overview.get("inventory").size()).isEqualTo(1);
        assertThat(overview.get("sourcesAvailable_readModel").asBoolean()).isTrue();
        assertThat(overview.get("sourcesAvailable_tower").asBoolean()).isFalse();
        assertThat(overview.get("alerts").isArray()).isTrue();
        assertThat(overview.get("freshness").get("shipmentCount").asInt()).isEqualTo(1);
    }

    @Test
    void shipmentDetailResolvesMdmLocationNames() throws Exception {
        JsonNode shipment = json.readTree("{\"shipmentId\":\"SHP-1\",\"pickupLocationId\":\"LOC-WH-AMS\","
                + "\"deliveryLocationId\":\"LOC-STORE-BER\"}");
        Optional<JsonNode> pickup = Optional.of(json.readTree(
                "{\"canonicalJson\":\"{\\\"name\\\":\\\"Amsterdam DC\\\"}\"}"));
        Optional<JsonNode> delivery = Optional.of(json.readTree(
                "{\"canonicalJson\":\"{\\\"name\\\":\\\"Berlin Store\\\"}\"}"));

        JsonNode detail = mapper.shipmentDetail(shipment, pickup, delivery);

        assertThat(detail.get("pickupLocationName").asText()).isEqualTo("Amsterdam DC");
        assertThat(detail.get("deliveryLocationName").asText()).isEqualTo("Berlin Store");
    }

    @Test
    void shipmentDetailToleratesMissingLocations() throws Exception {
        JsonNode shipment = json.readTree("{\"shipmentId\":\"SHP-1\"}");
        JsonNode detail = mapper.shipmentDetail(shipment, Optional.empty(), Optional.empty());
        assertThat(detail.get("pickupLocationName").isNull()).isTrue();
        assertThat(detail.get("deliveryLocationName").isNull()).isTrue();
    }

    @Test
    void healthReflectsUpstreamReachability() {
        JsonNode health = mapper.health(Optional.empty(),
                Optional.of(json.createObjectNode()), Optional.of(json.createArrayNode()));
        assertThat(health.get("readModel").asBoolean()).isFalse();
        assertThat(health.get("controlTower").asBoolean()).isTrue();
        assertThat(health.get("mdm").asBoolean()).isTrue();
    }
}