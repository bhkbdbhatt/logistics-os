package io.logisticsos.readmodel.serving;

import io.logisticsos.readmodel.projection.InventoryProjection;
import io.logisticsos.readmodel.projection.ShipmentProjection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-process snapshot of the compacted state topics. Serving layer for the BFFs;
 * values are last-write-wins copies of the version-guarded projections.
 */
public final class ReadModelCache {

    private final Map<String, ShipmentProjection> shipments = new ConcurrentHashMap<>();
    private final Map<String, InventoryProjection> inventory = new ConcurrentHashMap<>();
    private volatile long lastSnapshotMillis;

    public long lastSnapshotMillis() {
        return lastSnapshotMillis;
    }

    public void reset() {
        shipments.clear();
        inventory.clear();
        lastSnapshotMillis = 0;
    }

    public void putShipment(ShipmentProjection projection) {
        shipments.put(projection.getShipmentId(), projection);
        lastSnapshotMillis = System.currentTimeMillis();
    }

    public void putInventory(InventoryProjection projection) {
        inventory.put(projection.getInventoryId(), projection);
        lastSnapshotMillis = System.currentTimeMillis();
    }

    public Optional<ShipmentProjection> shipment(String shipmentId) {
        return Optional.ofNullable(shipments.get(shipmentId));
    }

    public List<ShipmentProjection> shipmentsByStatus(String status) {
        return shipments.values().stream()
                .filter(p -> status == null || status.equalsIgnoreCase(p.getStatus()))
                .toList();
    }

    public Optional<InventoryProjection> inventory(String inventoryId) {
        return Optional.ofNullable(inventory.get(inventoryId));
    }

    public List<InventoryProjection> inventoryBySku(String sku) {
        return inventory.values().stream()
                .filter(p -> sku == null || sku.equalsIgnoreCase(p.getSku()))
                .toList();
    }

    public int shipmentCount() {
        return shipments.size();
    }

    public int inventoryCount() {
        return inventory.size();
    }
}