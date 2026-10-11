package io.logisticsos.readmodel.projection;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable snapshot of a shipment for the control tower / BFFs (ADR-0003). Written to the compacted
 * {@code shipment.state.v1} topic; guard by {@code updatedAt} (occurredAt of the applied event) so
 * replay and out-of-order delivery cannot regress the view.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShipmentProjection {

    private String shipmentId;
    private String eventType;
    private String status;
    private long aggregateVersion = 1;
    private long updatedAt;
    private String mode;
    private String serviceLevel;
    private String pickupLocationId;
    private String deliveryLocationId;
    private String originLocationId;
    private String planId;
    private double totalWeightKg;
    private final Map<String, String> extras = new LinkedHashMap<>();

    public String getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(String shipmentId) {
        this.shipmentId = shipmentId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getOriginLocationId() {
        return originLocationId;
    }

    public void setOriginLocationId(String originLocationId) {
        this.originLocationId = originLocationId;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public double getTotalWeightKg() {
        return totalWeightKg;
    }

    public void setTotalWeightKg(double totalWeightKg) {
        this.totalWeightKg = totalWeightKg;
    }

    public Map<String, String> getExtras() {
        return extras;
    }
}