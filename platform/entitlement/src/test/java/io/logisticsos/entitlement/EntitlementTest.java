package io.logisticsos.entitlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EntitlementTest {

    @Test
    void evaluationIsPermissiveForUnknownFeatures() {
        assertThat(Entitlement.evaluation().isEnabled("anything")).isTrue();
    }

    @Test
    void licensedIsGatedForUnknownFeatures() {
        Entitlement licensed = new Entitlement("standard", "1.0.0", "acme", null, Map.of(),
                Entitlement.Mode.LICENSED);

        assertThat(licensed.isEnabled("l5_orchestration")).isFalse();
    }

    @Test
    void explicitFeatureValueWinsOverDefault() {
        Entitlement licensed = new Entitlement("enterprise", "1.0.0", "acme", null,
                Map.of("l5_orchestration", true, "sso_saml", false), Entitlement.Mode.LICENSED);

        assertThat(licensed.isEnabled("l5_orchestration")).isTrue();
        assertThat(licensed.isEnabled("sso_saml")).isFalse();
    }

    @Test
    void gateDelegatesToEntitlement() {
        EntitlementGate gate = new EntitlementGate(Entitlement.evaluation());

        assertThat(gate.isEnabled("ops_actions")).isTrue();
        assertThat(gate.entitlement().edition()).isEqualTo("evaluation");
    }

    @Test
    void detectsExpiry() {
        Instant expiry = Instant.parse("2026-01-01T00:00:00Z");
        Entitlement e = new Entitlement("enterprise", "1.0.0", "acme", expiry, Map.of(),
                Entitlement.Mode.LICENSED);

        assertThat(e.isExpired(expiry.plusSeconds(1))).isTrue();
        assertThat(e.isExpired(expiry.minusSeconds(1))).isFalse();
        assertThat(Entitlement.evaluation().isExpired(Instant.now())).isFalse();
    }
}