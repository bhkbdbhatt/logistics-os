package io.logisticsos.tower;

import com.fasterxml.jackson.databind.ObjectMapper;

/** JSON parsing helpers for the compacted state topics (bad lines are skipped, never fail the poll). */
final class TowerLineParser {

    private static final ObjectMapper JSON = new ObjectMapper();

    private TowerLineParser() {
    }

    static ShipmentView parseShipment(String value) {
        try {
            return JSON.readValue(value, ShipmentView.class);
        } catch (Exception ex) {
            return null;
        }
    }

    static InventoryView parseInventory(String value) {
        try {
            return JSON.readValue(value, InventoryView.class);
        } catch (Exception ex) {
            return null;
        }
    }
}