package io.logisticsos.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Deterministic operational alert rules over the hot-store shipment views (analytics/control-tower.md
 * §"Alert queue"). Distinct from planning-time orch.trigger/exception; correlates by subject id.
 *
 * <ul>
 *   <li>{@code EXCEPTION} — shipment in EXCEPTION status (HIGH).</li>
 *   <li>{@code SLA_AT_RISK} — in-flight shipment whose last state change is older than the SLA
 *       threshold (MEDIUM, CRITICAL past twice the threshold).</li>
 * </ul>
 */
public final class AlertRuleEngine {

    private static final Set<String> TERMINAL = Set.of("DELIVERED", "CANCELLED", "RETURNED", "EXCEPTION");

    private final long slaAtRiskMillis;

    public AlertRuleEngine(long slaAtRiskMillis) {
        this.slaAtRiskMillis = slaAtRiskMillis;
    }

    public List<Alert> evaluate(List<ShipmentView> shipments, long now) {
        List<Alert> alerts = new ArrayList<>();
        for (ShipmentView view : shipments) {
            if (view.getShipmentId() == null || view.getStatus() == null) {
                continue;
            }
            String status = view.getStatus().toUpperCase();
            if ("EXCEPTION".equals(status)) {
                alerts.add(raise(view, Alert.Type.EXCEPTION, "HIGH",
                        "Shipment " + view.getShipmentId() + " is in EXCEPTION"));
                continue;
            }
            if (TERMINAL.contains(status)) {
                continue;
            }
            long age = now - view.getUpdatedAt();
            if (age > slaAtRiskMillis) {
                String severity = age > 2 * slaAtRiskMillis ? "CRITICAL" : "MEDIUM";
                alerts.add(raise(view, Alert.Type.SLA_AT_RISK, severity,
                        "Shipment " + view.getShipmentId() + " untouched for " + (age / 60_000) + "m"));
            }
        }
        return alerts;
    }

    private Alert raise(ShipmentView view, Alert.Type type, String severity, String message) {
        Alert alert = new Alert();
        alert.setAlertId(Alert.idFor("shipment", view.getShipmentId(), type));
        alert.setSubjectId(view.getShipmentId());
        alert.setType(type);
        alert.setSeverity(severity);
        alert.setMessage(message);
        return alert;
    }
}