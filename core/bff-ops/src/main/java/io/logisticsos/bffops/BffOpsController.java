package io.logisticsos.bffops;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ops BFF surface. Composes {@code *_view} reads + tower alerts/actions + MDM decoration; forwards
 * one-click actions verbatim (no domain logic).
 */
@RestController
@RequestMapping("/ops")
public class BffOpsController {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NotifyCustomerRequest(String shipmentId, String caseId) {
    }

    private final OpsPort io;
    private final OpsViewMapper mapper;

    public BffOpsController(OpsPort io, OpsViewMapper mapper) {
        this.io = io;
        this.mapper = mapper;
    }

    @GetMapping("/overview")
    public JsonNode overview() {
        return mapper.overview(
                io.shipments(null),
                io.inventory(),
                io.towerAlerts(),
                io.towerHealth(),
                System.currentTimeMillis());
    }

    @GetMapping("/shipments/{shipmentId}")
    public ResponseEntity<JsonNode> shipment(@PathVariable String shipmentId) {
        return io.shipment(shipmentId)
                .map(shipment -> ResponseEntity.ok(mapper.shipmentDetail(
                        shipment,
                        locationName(io, shipment, "pickupLocationId"),
                        locationName(io, shipment, "deliveryLocationId"))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/actions/notify-customer")
    public ResponseEntity<JsonNode> notifyCustomer(@RequestBody NotifyCustomerRequest request) {
        return io.notifyCustomer(request.shipmentId(), request.caseId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.BAD_GATEWAY).build());
    }

    @GetMapping("/health")
    public JsonNode health() {
        return mapper.health(io.inventory(), io.towerHealth(), io.mdmLocations());
    }

    private java.util.Optional<JsonNode> locationName(OpsPort io, JsonNode shipment, String field) {
        JsonNode id = shipment.get(field);
        if (id == null || id.isNull() || id.asText().isBlank()) {
            return java.util.Optional.empty();
        }
        return io.mdmLocation(id.asText());
    }
}