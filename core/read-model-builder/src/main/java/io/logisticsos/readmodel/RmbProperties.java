package io.logisticsos.readmodel;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rmb")
public class RmbProperties {

    /** Compacted state topic the snapshot poller reads into the in-process read model cache. */
    private String shipmentStateTopic = "shipment.state.v1";

    /** Compacted state topic inventoryView reads from. */
    private String inventoryStateTopic = "inventory.state.v1";

    /** Switch for the serving layer; set false to serve only from interactive queries in the future. */
    private boolean snapshotConsumeEnabled = true;

    public String getShipmentStateTopic() {
        return shipmentStateTopic;
    }

    public void setShipmentStateTopic(String shipmentStateTopic) {
        this.shipmentStateTopic = shipmentStateTopic;
    }

    public String getInventoryStateTopic() {
        return inventoryStateTopic;
    }

    public void setInventoryStateTopic(String inventoryStateTopic) {
        this.inventoryStateTopic = inventoryStateTopic;
    }

    public boolean isSnapshotConsumeEnabled() {
        return snapshotConsumeEnabled;
    }

    public void setSnapshotConsumeEnabled(boolean snapshotConsumeEnabled) {
        this.snapshotConsumeEnabled = snapshotConsumeEnabled;
    }
}