package io.logisticsos.readmodel.projection;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mutable snapshot of an inventory unit (ADR-0003 inventory_view). Stock quantity/location per
 * {@code inventoryId}; guarded by {@code moveSequence} so replay and out-of-order delivery cannot
 * regress the view.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InventoryProjection {

    private String inventoryId;
    private String sku;
    private String location;
    private double quantity;
    private String uom = "EA";
    private long moveSequence;
    private String moveType;
    private String reasonCode;
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

    public String getMoveType() {
        return moveType;
    }

    public void setMoveType(String moveType) {
        this.moveType = moveType;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}