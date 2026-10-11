package io.logisticsos.bffops;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BffOpsControllerTest {

    private final ObjectMapper json = new ObjectMapper();
    private BffOpsController controller;
    private FakeOps fake;

    @BeforeEach
    void setUp() {
        fake = new FakeOps();
        controller = new BffOpsController(fake, new OpsViewMapper(json));
    }

    @Test
    void overviewDelegatesToComposePorts() {
        JsonNode overview = controller.overview();
        assertThat(overview.get("shipments").size()).isEqualTo(2);
        assertThat(overview.get("sourcesAvailable_readModel").asBoolean()).isTrue();
        assertThat(fake.shipmentsCalls).isEqualTo(1);
    }

    @Test
    void unknownShipmentReturnsNotFound() {
        fake.missingShipment = true;
        assertThat(controller.shipment("NOPE").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void actionForwardingReturnsAck() {
        fake.ack = json.createObjectNode().put("commandId", "CMD-1");
        JsonNode ack = controller.notifyCustomer(new BffOpsController.NotifyCustomerRequest("SHP-1", "CASE-1")).getBody();
        assertThat(ack.get("commandId").asText()).isEqualTo("CMD-1");
    }

    @Test
    void actionForwardingTimeoutReturnsBadGateway() {
        fake.ack = null;
        assertThat(controller.notifyCustomer(new BffOpsController.NotifyCustomerRequest("SHP-1", null))
                .getStatusCode().value()).isEqualTo(502);
    }

    private static class FakeOps implements OpsPort {

        private final ObjectMapper json = new ObjectMapper();
        boolean missingShipment;
        JsonNode ack = json.createObjectNode().put("commandId", "CMD-1");
        int shipmentsCalls;

        @Override
        public Optional<JsonNode> shipments(String status) {
            shipmentsCalls++;
            return Optional.of(json.createArrayNode()
                    .add(new com.fasterxml.jackson.databind.node.ObjectNode(json.getNodeFactory())
                            .put("shipmentId", "SHP-1"))
                    .add(new com.fasterxml.jackson.databind.node.ObjectNode(json.getNodeFactory())
                            .put("shipmentId", "SHP-2")));
        }

        @Override
        public Optional<JsonNode> shipment(String shipmentId) {
            if (missingShipment) {
                return Optional.empty();
            }
            return Optional.of(json.createObjectNode().put("shipmentId", shipmentId));
        }

        @Override
        public Optional<JsonNode> inventory() {
            return Optional.of(json.createArrayNode());
        }

        @Override
        public Optional<JsonNode> towerAlerts() {
            return Optional.of(json.createArrayNode());
        }

        @Override
        public Optional<JsonNode> towerHealth() {
            return Optional.of(json.createObjectNode().put("status", "ok"));
        }

        @Override
        public Optional<JsonNode> mdmLocation(String locationId) {
            return Optional.of(json.createObjectNode().put("canonicalJson", "{\"name\":\"X\"}"));
        }

        @Override
        public Optional<JsonNode> mdmLocations() {
            return Optional.of(json.createArrayNode());
        }

        @Override
        public Optional<JsonNode> entitlement() {
            return Optional.of(json.createObjectNode().put("edition", "professional"));
        }

        @Override
        public Optional<JsonNode> notifyCustomer(String shipmentId, String caseId) {
            return Optional.ofNullable(ack);
        }
    }
}