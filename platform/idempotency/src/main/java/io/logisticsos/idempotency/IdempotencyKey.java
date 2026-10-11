package io.logisticsos.idempotency;

import java.util.Objects;

/**
 * Canonical dedupe key: {@code eventType:aggregateId:aggregateVersion}
 * (docs/schemas/common-types.md 4.1) or any producer-supplied key.
 */
public record IdempotencyKey(String value) {

    public IdempotencyKey {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("idempotency key must not be blank");
        }
        if (value.length() > 512) {
            throw new IllegalArgumentException("idempotency key too long: " + value.length());
        }
    }

    public static IdempotencyKey of(String eventType, String aggregateId, long aggregateVersion) {
        return new IdempotencyKey(
                Objects.requireNonNull(eventType, "eventType")
                        + ":" + Objects.requireNonNull(aggregateId, "aggregateId") + ":" + aggregateVersion);
    }

    @Override
    public String toString() {
        return value();
    }
}