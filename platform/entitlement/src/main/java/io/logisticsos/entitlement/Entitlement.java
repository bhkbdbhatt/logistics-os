package io.logisticsos.entitlement;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * The technical projection of a customer's license: the edition/version they may run, for which
 * tenant, until when, and which features they are entitled to.
 *
 * <p>See {@code docs/product/licensing.md}. In the soft phase this is derived from configuration;
 * in the hard phase it is verified from a signed license.
 */
public record Entitlement(String edition, String version, String tenant, Instant expiresAt,
        Map<String, Boolean> features, Mode mode) {

    /** How the entitlement was established. */
    public enum Mode {
        /** Unlicensed / no signed license: permissive evaluation mode (soft phase). */
        EVALUATION,
        /** Derived from a (soon: signed) license file. */
        LICENSED,
        /** Present but unverifiable/expired: treated as gated. */
        INVALID
    }

    public Entitlement {
        Objects.requireNonNull(edition, "edition");
        features = features == null ? Map.of() : Map.copyOf(features);
        mode = mode == null ? Mode.EVALUATION : mode;
    }

    /**
     * Whether a feature is enabled. Explicit values in the license win; unknown features default to
     * <em>permissive only in {@link Mode#EVALUATION}</em> and gated otherwise, so moving from soft to
     * hard enforcement only changes this default.
     */
    public boolean isEnabled(String feature) {
        Boolean value = features.get(feature);
        if (value != null) {
            return value;
        }
        return mode == Mode.EVALUATION;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    /** A permissive, unlicensed entitlement used when no license is supplied (soft phase). */
    public static Entitlement evaluation() {
        return new Entitlement("evaluation", "0.0.0", "unlicensed", null, Map.of(), Mode.EVALUATION);
    }
}