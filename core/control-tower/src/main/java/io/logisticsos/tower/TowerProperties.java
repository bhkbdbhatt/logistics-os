package io.logisticsos.tower;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tower")
public class TowerProperties {

    private String shipmentStateTopic = "shipment.state.v1";
    private String inventoryStateTopic = "inventory.state.v1";
    private String commandTopic = "orch.command.v1";
    private boolean snapshotConsumeEnabled = true;
    private long slaAtRiskMillis = 1_800_000L;
    private String defaultTenant = "tenant-1";
    private String producer = "control-tower@0.1.0";

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

    public String getCommandTopic() {
        return commandTopic;
    }

    public void setCommandTopic(String commandTopic) {
        this.commandTopic = commandTopic;
    }

    public boolean isSnapshotConsumeEnabled() {
        return snapshotConsumeEnabled;
    }

    public void setSnapshotConsumeEnabled(boolean snapshotConsumeEnabled) {
        this.snapshotConsumeEnabled = snapshotConsumeEnabled;
    }

    public long getSlaAtRiskMillis() {
        return slaAtRiskMillis;
    }

    public void setSlaAtRiskMillis(long slaAtRiskMillis) {
        this.slaAtRiskMillis = slaAtRiskMillis;
    }

    public String getDefaultTenant() {
        return defaultTenant;
    }

    public void setDefaultTenant(String defaultTenant) {
        this.defaultTenant = defaultTenant;
    }

    public String getProducer() {
        return producer;
    }

    public void setProducer(String producer) {
        this.producer = producer;
    }
}