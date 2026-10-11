package io.logisticsos.tower;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe hot store for the tower: latest shipment/inventory snapshots plus the alert queue.
 * Populated by {@link TowerStatePoller}; alerts de-duplicated and resolved by {@link #applyAlerts}.
 */
public final class TowerCache {

    private final Map<String, ShipmentView> shipments = new ConcurrentHashMap<>();
    private final Map<String, InventoryView> inventory = new ConcurrentHashMap<>();
    private final Map<String, Alert> alerts = new ConcurrentHashMap<>();
    private volatile long lastSnapshotMillis;

    public void putShipment(ShipmentView view) {
        shipments.put(view.getShipmentId(), view);
        lastSnapshotMillis = System.currentTimeMillis();
    }

    public void putInventory(InventoryView view) {
        inventory.put(view.getInventoryId(), view);
        lastSnapshotMillis = System.currentTimeMillis();
    }

    public List<ShipmentView> shipments(String status) {
        return shipments.values().stream()
                .filter(v -> status == null || status.equalsIgnoreCase(v.getStatus()))
                .toList();
    }

    public Optional<ShipmentView> shipment(String shipmentId) {
        return Optional.ofNullable(shipments.get(shipmentId));
    }

    public List<InventoryView> inventory() {
        return List.copyOf(inventory.values());
    }

    public List<Alert> alerts(Alert.Status status) {
        return alerts.values().stream()
                .filter(a -> status == null || a.getStatus() == status)
                .toList();
    }

    public long lastSnapshotMillis() {
        return lastSnapshotMillis;
    }

    public int shipmentCount() {
        return shipments.size();
    }

    public int openAlertCount() {
        return (int) alerts.values().stream().filter(a -> a.getStatus() == Alert.Status.OPEN).count();
    }

    /**
     * Reconciles the alert queue with the currently-active conditions: new conditions are raised,
     * still-active conditions keep their original {@code raisedAt}, cleared conditions are RESOLVED.
     */
    public void applyAlerts(List<Alert> active, long now) {
        Map<String, Alert> next = new ConcurrentHashMap<>();
        for (Alert alert : active) {
            Alert existing = alerts.get(alert.getAlertId());
            if (existing != null && existing.getStatus() == Alert.Status.OPEN) {
                alert.setRaisedAt(existing.getRaisedAt());
            } else {
                alert.setRaisedAt(now);
            }
            alert.setStatus(Alert.Status.OPEN);
            next.put(alert.getAlertId(), alert);
        }
        alerts.values().stream()
                .filter(a -> a.getStatus() == Alert.Status.OPEN && !next.containsKey(a.getAlertId()))
                .forEach(a -> {
                    a.setStatus(Alert.Status.RESOLVED);
                    a.setResolvedAt(now);
                    next.put(a.getAlertId(), a);
                });
        alerts.clear();
        alerts.putAll(next);
    }
}