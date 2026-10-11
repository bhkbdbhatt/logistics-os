package io.logisticsos.readmodel.serving;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.logisticsos.readmodel.projection.InventoryProjection;
import io.logisticsos.readmodel.projection.ShipmentProjection;

/** JSON parsing helpers for the compacted state topics (bad lines are skipped, never fail the poll). */
final class StateLineParser {

    private static final ObjectMapper JSON = new ObjectMapper();

    private StateLineParser() {
    }

    static ShipmentProjection parseShipment(String value) {
        try {
            return JSON.readValue(value, ShipmentProjection.class);
        } catch (Exception ex) {
            return null;
        }
    }

    static InventoryProjection parseInventory(String value) {
        try {
            return JSON.readValue(value, InventoryProjection.class);
        } catch (Exception ex) {
            return null;
        }
    }
}