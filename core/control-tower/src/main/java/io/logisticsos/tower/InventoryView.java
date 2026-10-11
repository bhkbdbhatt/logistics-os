package io.logisticsos.tower;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Hot-store snapshot of an inventory unit (read from the compacted {@code inventory.state.v1} topic). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InventoryView {

    private String inventoryId;
    private String sku;
    private String location;
    private double quantity;
    private String uom;
    private long moveSequence;
    private long updatedAt;

    public String getInventoryId() {
        return inventoryId;
    }

    public void setInventoryId(String inventoryId) {
        this.inventoryId = inventoryId;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public String getUom() {
        return uom;
    }

    public void setUom(String uom) {
        this.uom = uom;
    }

    public long getMoveSequence() {
        return moveSequence;
    }

    public void setMoveSequence(long moveSequence) {
        this.moveSequence = moveSequence;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}