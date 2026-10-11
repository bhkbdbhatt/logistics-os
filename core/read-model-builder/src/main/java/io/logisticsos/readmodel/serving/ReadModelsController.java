package io.logisticsos.readmodel.serving;

import io.logisticsos.readmodel.projection.InventoryProjection;
import io.logisticsos.readmodel.projection.ShipmentProjection;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-model serving endpoints for the BFFs (L6). Reads the CQRS snapshot cache; no domain logic,
 * no L3 databases (ADR-0003).
 */
@RestController
@RequestMapping("/views")
public class ReadModelsController {

    private final ReadModelCache cache;

    public ReadModelsController(ReadModelCache cache) {
        this.cache = cache;
    }

    @GetMapping("/shipments/{shipmentId}")
    public ResponseEntity<ShipmentProjection> shipment(@PathVariable String shipmentId) {
        return cache.shipment(shipmentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/shipments")
    public List<ShipmentProjection> shipments(@RequestParam(value = "status", required = false) String status) {
        return cache.shipmentsByStatus(status);
    }

    @GetMapping("/inventory/{inventoryId}")
    public ResponseEntity<InventoryProjection> inventory(@PathVariable String inventoryId) {
        return cache.inventory(inventoryId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/inventory")
    public List<InventoryProjection> inventoryBySku(@RequestParam(value = "sku", required = false) String sku) {
        return cache.inventoryBySku(sku);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "shipmentCount", cache.shipmentCount(),
                "inventoryCount", cache.inventoryCount(),
                "lastSnapshotAt", cache.lastSnapshotMillis(),
                "status", "ok");
    }
}