package io.logisticsos.tower;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Hot-store snapshot of a shipment (read from the compacted {@code shipment.state.v1} topic). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShipmentView {

    private String shipmentId;
    private String status;
    private String mode;
    private String serviceLevel;
    private String pickupLocationId;
    private String deliveryLocationId;
    private String planId;
    private long aggregateVersion;
    private long updatedAt;

    public String getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(String shipmentId) {
        this.shipmentId = shipmentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getServiceLevel() {
        return serviceLevel;
    }

    public void setServiceLevel(String serviceLevel) {
        this.serviceLevel = serviceLevel;
    }

    public String getPickupLocationId() {
        return pickupLocationId;
    }

    public void setPickupLocationId(String pickupLocationId) {
        this.pickupLocationId = pickupLocationId;
    }

    public String getDeliveryLocationId() {
        return deliveryLocationId;
    }

    public void setDeliveryLocationId(String deliveryLocationId) {
        this.deliveryLocationId = deliveryLocationId;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public long getAggregateVersion() {
        return aggregateVersion;
    }

    public void setAggregateVersion(long aggregateVersion) {
        this.aggregateVersion = aggregateVersion;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}