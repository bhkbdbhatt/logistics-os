package io.logisticsos.readmodel.projection;

/** Naming constants for the read-model state stores and output topics. */
public final class ProjectionNames {

    public static final String SHIPMENT_INPUT = "tms.shipment.events.v1";
    public static final String INVENTORY_INPUT = "wms.inventory.events.v1";

    public static final String SHIPMENT_STATE_TOPIC = "shipment.state.v1";
    public static final String INVENTORY_STATE_TOPIC = "inventory.state.v1";

    public static final String SHIPMENT_STORE = "shipment-view";
    public static final String INVENTORY_STORE = "inventory-view";

    private ProjectionNames() {
    }
}