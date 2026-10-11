package io.logisticsos.tower;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AlertRuleEngineTest {

    private final AlertRuleEngine engine = new AlertRuleEngine(1_000L);

    private ShipmentView shipment(String id, String status, long updatedAt) {
        ShipmentView view = new ShipmentView();
        view.setShipmentId(id);
        view.setStatus(status);
        view.setUpdatedAt(updatedAt);
        return view;
    }

    @Test
    void exceptionStatusRaisesExceptionAlert() {
        List<Alert> alerts = engine.evaluate(List.of(shipment("S1", "EXCEPTION", 100)), 10_000);
        assertThat(alerts).hasSize(1);
        Alert alert = alerts.get(0);
        assertThat(alert.getType()).isEqualTo(Alert.Type.EXCEPTION);
        assertThat(alert.getSeverity()).isEqualTo("HIGH");
        assertThat(alert.getAlertId()).isEqualTo("shipment:S1:EXCEPTION");
    }

    @Test
    void staleInfightShipmentRaisesSlaAtRisk() {
        List<Alert> alerts = engine.evaluate(List.of(shipment("S1", "DISPATCHED", 8_200)), 10_000);
        assertThat(alerts).singleElement().satisfies(alert -> {
            assertThat(alert.getType()).isEqualTo(Alert.Type.SLA_AT_RISK);
            assertThat(alert.getSeverity()).isEqualTo("MEDIUM");
        });
    }

    @Test
    void veryStaleInfightShipmentIsCritical() {
        List<Alert> alerts = engine.evaluate(List.of(shipment("S1", "PLANNED", 0)), 10_000);
        assertThat(alerts).singleElement().satisfies(alert ->
                assertThat(alert.getSeverity()).isEqualTo("CRITICAL"));
    }

    @Test
    void freshInfightShipmentRaisesNothing() {
        assertThat(engine.evaluate(List.of(shipment("S1", "DISPATCHED", 9_900)), 10_000)).isEmpty();
    }

    @Test
    void terminalStatusesRaiseNothing() {
        assertThat(engine.evaluate(List.of(
                shipment("S1", "DELIVERED", 100),
                shipment("S2", "CANCELLED", 100)), 10_000)).isEmpty();
    }

    @Test
    void applyAlertsResolvesClearedConditions() {
        TowerCache cache = new TowerCache();
        cache.applyAlerts(engine.evaluate(List.of(shipment("S1", "DISPATCHED", 500)), 10_000), 10_000);
        assertThat(cache.openAlertCount()).isEqualTo(1);

        cache.applyAlerts(engine.evaluate(List.of(shipment("S1", "DELIVERED", 9_900)), 10_001), 10_001);
        assertThat(cache.openAlertCount()).isZero();
        assertThat(cache.alerts(Alert.Status.RESOLVED)).singleElement().satisfies(alert ->
                assertThat(alert.getResolvedAt()).isEqualTo(10_001));
    }

    @Test
    void reApplyingOpenAlertKeepsOriginalRaisedAt() {
        TowerCache cache = new TowerCache();
        cache.applyAlerts(engine.evaluate(List.of(shipment("S1", "DISPATCHED", 500)), 10_000), 10_000);
        cache.applyAlerts(engine.evaluate(List.of(shipment("S1", "DISPATCHED", 500)), 12_000), 12_000);
        assertThat(cache.alerts(Alert.Status.OPEN)).singleElement()
                .satisfies(a -> assertThat(a.getRaisedAt()).isEqualTo(10_000));
    }

    @Test
    void reopenedAlertAfterResolutionGetsNewRaisedAt() {
        TowerCache cache = new TowerCache();
        cache.applyAlerts(engine.evaluate(List.of(shipment("S1", "DISPATCHED", 500)), 10_000), 10_000);
        cache.applyAlerts(engine.evaluate(List.of(shipment("S1", "DELIVERED", 9_900)), 20_000), 20_000);
        assertThat(cache.alerts(Alert.Status.OPEN)).isEmpty();

        cache.applyAlerts(engine.evaluate(List.of(shipment("S1", "DISPATCHED", 1_000)), 30_000), 30_000);
        assertThat(cache.alerts(Alert.Status.OPEN)).singleElement()
                .satisfies(a -> assertThat(a.getRaisedAt()).isEqualTo(30_000));
    }
}