package io.logisticsos.entitlement;

/** Thrown when a supplied entitlement license cannot be read, parsed, or (later) verified. */
public class EntitlementException extends RuntimeException {

    public EntitlementException(String message) {
        super(message);
    }

    public EntitlementException(String message, Throwable cause) {
        super(message, cause);
    }
}