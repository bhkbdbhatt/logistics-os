package io.logisticsos.mdm;

import io.logisticsos.events.kafka.EnvelopeRouting;

/** Golden-record entity descriptors: schema subject, topic, and the id/tenant field names. */
public enum MdmEntities {

    CUSTOMER("customer", "io.logisticsos.mdm.record.v1.Customer",
            EnvelopeRouting.topic(EnvelopeRouting.Layer.MDM, "customer", "events", 1),
            "customerId", "tenantId", "mdm-customer"),
    CARRIER("carrier", "io.logisticsos.mdm.record.v1.Carrier",
            EnvelopeRouting.topic(EnvelopeRouting.Layer.MDM, "carrier", "events", 1),
            "carrierId", "tenantId", "mdm-carrier"),
    VEHICLE("vehicle", "io.logisticsos.mdm.record.v1.Vehicle",
            EnvelopeRouting.topic(EnvelopeRouting.Layer.MDM, "vehicle", "events", 1),
            "vehicleId", "tenantId", "mdm-vehicle"),
    LOCATION("location", "io.logisticsos.mdm.record.v1.Location",
            EnvelopeRouting.topic(EnvelopeRouting.Layer.MDM, "location", "events", 1),
            "locationId", "tenantId", "mdm-location");

    private final String path;
    private final String subject;
    private final String topic;
    private final String idField;
    private final String tenantField;
    private final String aggregateType;

    MdmEntities(String path, String subject, String topic, String idField, String tenantField,
            String aggregateType) {
        this.path = path;
        this.subject = subject;
        this.topic = topic;
        this.idField = idField;
        this.tenantField = tenantField;
        this.aggregateType = aggregateType;
    }

    public String path() {
        return path;
    }

    public String subject() {
        return subject;
    }

    public String topic() {
        return topic;
    }

    public String idField() {
        return idField;
    }

    public String tenantField() {
        return tenantField;
    }

    public String aggregateType() {
        return aggregateType;
    }

    public String enumSymbol() {
        return name();
    }

    public static MdmEntities fromPath(String path) {
        for (MdmEntities entity : values()) {
            if (entity.path.equals(path)) {
                return entity;
            }
        }
        throw new IllegalArgumentException("unknown golden-record entity '" + path
                + "' (expected one of customer, carrier, vehicle, location)");
    }
}