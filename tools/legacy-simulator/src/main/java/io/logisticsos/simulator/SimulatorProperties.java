package io.logisticsos.simulator;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Behaviour of the fake legacy vendors. */
@ConfigurationProperties(prefix = "simulator")
public class SimulatorProperties {

    /** Master switch: set to false to keep the simulator running without emitting. */
    private boolean enabled = true;

    /** Millis between emission rounds. */
    private long intervalMillis = 2_000;

    /** Tenant the feed belongs to (set as X-Tenant-Id header on legacy records). */
    private String tenantId = "acme";

    /** Number of distinct shipments/inventory items the loop cycles through. */
    private int workingSet = 50;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getIntervalMillis() {
        return intervalMillis;
    }

    public void setIntervalMillis(long intervalMillis) {
        this.intervalMillis = intervalMillis;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public int getWorkingSet() {
        return workingSet;
    }

    public void setWorkingSet(int workingSet) {
        this.workingSet = workingSet;
    }
}