package io.logisticsos.tower;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically re-evaluates alert rules against the hot store and reconciles the alert queue. */
@Component
public class TowerAlertScheduler {

    private final TowerCache cache;
    private final AlertRuleEngine engine;

    public TowerAlertScheduler(TowerCache cache, TowerProperties properties) {
        this.cache = cache;
        this.engine = new AlertRuleEngine(properties.getSlaAtRiskMillis());
    }

    @Scheduled(fixedDelayString = "${tower.alert-interval-ms:2000}", initialDelay = 2500)
    public void refreshAlerts() {
        long now = System.currentTimeMillis();
        cache.applyAlerts(engine.evaluate(cache.shipments(null), now), now);
    }
}