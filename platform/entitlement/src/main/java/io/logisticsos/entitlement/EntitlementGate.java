package io.logisticsos.entitlement;

import java.util.Objects;

/**
 * The single seam services use for feature checks, so soft → hard enforcement is a change here, not
 * in callers. Inject one per application; the entity is immutable.
 */
public final class EntitlementGate {

    private final Entitlement entitlement;

    public EntitlementGate(Entitlement entitlement) {
        this.entitlement = Objects.requireNonNull(entitlement, "entitlement");
    }

    public static EntitlementGate fromEnvironment() {
        return new EntitlementGate(EntitlementLoader.load());
    }

    public boolean isEnabled(String feature) {
        return entitlement.isEnabled(feature);
    }

    public Entitlement entitlement() {
        return entitlement;
    }
}