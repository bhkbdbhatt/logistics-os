package io.logisticsos.ingest;

/** Inbound observation stream kinds accepted by the ingest gateway (canonical obs topics). */
public enum IngestKind {

    TELEMETRY("telemetry", "obs.telemetry.v1", "io.logisticsos.obs.v1.Telemetry", "device", "deviceId"),
    SCAN("scan", "obs.scan.v1", "io.logisticsos.obs.v1.Scan", "location", "deviceId"),
    CARRIER_STATUS("carrier-status", "obs.carrier-status.v1", "io.logisticsos.obs.v1.CarrierStatus",
            "shipment", "shipmentId");

    private final String path;
    private final String topic;
    private final String payloadType;
    private final String aggregateType;
    private final String naturalKeyField;

    IngestKind(String path, String topic, String payloadType, String aggregateType, String naturalKeyField) {
        this.path = path;
        this.topic = topic;
        this.payloadType = payloadType;
        this.aggregateType = aggregateType;
        this.naturalKeyField = naturalKeyField;
    }

    public String path() {
        return path;
    }

    public String topic() {
        return topic;
    }

    public String payloadType() {
        return payloadType;
    }

    /** Subject of the payload schema in the form {@code <eventType>-value}. */
    public String schemaSubject() {
        return payloadType + "-value";
    }

    public String aggregateType() {
        return aggregateType;
    }

    public String naturalKeyField() {
        return naturalKeyField;
    }

    public static IngestKind fromPath(String path) {
        for (IngestKind kind : values()) {
            if (kind.path.equals(path)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("unknown ingest kind: " + path);
    }
}