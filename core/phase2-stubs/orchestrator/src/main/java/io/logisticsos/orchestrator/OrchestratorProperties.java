package io.logisticsos.orchestrator;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orchestrator")
public class OrchestratorProperties {

    private String commandTopic = "orch.command.v1";
    private String defaultTenant = "tenant-1";
    private String producer = "orchestrator-stub@0.1.0-SNAPSHOT";
    private boolean emitEnabled = false;
    private long emitIntervalMillis = 5_000;
    private int demoWorkingSet = 1;

    public String getCommandTopic() {
        return commandTopic;
    }

    public void setCommandTopic(String commandTopic) {
        this.commandTopic = commandTopic;
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

    public boolean isEmitEnabled() {
        return emitEnabled;
    }

    public void setEmitEnabled(boolean emitEnabled) {
        this.emitEnabled = emitEnabled;
    }

    public long getEmitIntervalMillis() {
        return emitIntervalMillis;
    }

    public void setEmitIntervalMillis(long emitIntervalMillis) {
        this.emitIntervalMillis = emitIntervalMillis;
    }

    public int getDemoWorkingSet() {
        return demoWorkingSet;
    }

    public void setDemoWorkingSet(int demoWorkingSet) {
        this.demoWorkingSet = demoWorkingSet;
    }
}