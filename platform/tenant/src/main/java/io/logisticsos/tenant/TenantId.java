package io.logisticsos.tenant;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Tenant identifier. Every event row carries a mandatory tenantId to keep isolation at the broker
 * and storage layers (docs: multi-tenant design).
 */
public record TenantId(String value) {

    private static final Pattern VALID = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

    public TenantId {
        Objects.requireNonNull(value, "value");
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid tenantId: '" + value + "'");
        }
    }

    public static TenantId from(String value) {
        return new TenantId(value.toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return value();
    }
}