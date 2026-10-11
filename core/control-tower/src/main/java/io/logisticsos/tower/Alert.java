package io.logisticsos.tower;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * UI-facing operational alert over T0/T1 state (the control-tower rule engine).
 * Open alerts are de-duplicated by {@code alertId} = {@code subjectType:subjectId:type}; when the
 * condition clears the alert is marked RESOLVED rather than removed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Alert {

    public enum Type {
        SLA_AT_RISK,
        EXCEPTION
    }

    public enum Status {
        OPEN,
        RESOLVED
    }

    private String alertId;
    private String subjectType = "shipment";
    private String subjectId;
    private Type type;
    private String severity;
    private String message;
    private long raisedAt;
    private Status status = Status.OPEN;
    private Long resolvedAt;

    public static String idFor(String subjectType, String subjectId, Type type) {
        return subjectType + ":" + subjectId + ":" + type;
    }

    public String getAlertId() {
        return alertId;
    }

    public void setAlertId(String alertId) {
        this.alertId = alertId;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getRaisedAt() {
        return raisedAt;
    }

    public void setRaisedAt(long raisedAt) {
        this.raisedAt = raisedAt;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Long getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Long resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}