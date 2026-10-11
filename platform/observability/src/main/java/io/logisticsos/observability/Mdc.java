package io.logisticsos.observability;

import org.slf4j.MDC;
import java.util.Map;
import java.util.Objects;

/**
 * MDC key conventions + scoped helpers. Every inbound boundary fills these keys so service logs are
 * queryable by tenant, aggregate, event and correlation/trace id.
 */
public final class Mdc {

    public static final String TENANT_ID = "tenantId";
    public static final String AGGREGATE_ID = "aggregateId";
    public static final String EVENT_TYPE = "eventType";
    public static final String EVENT_ID = "eventId";
    public static final String CORRELATION_ID = "correlationId";
    public static final String TRACE_ID = "traceId";
    public static final String SPAN_ID = "spanId";

    private Mdc() {
    }

    /** Push a key/value onto the MDC; the returned handle restores the previous value on close. */
    public static CloseableScope scope(String key, String value) {
        Objects.requireNonNull(key, "key");
        String previous = MDC.get(key);
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
        return () -> {
            if (previous == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, previous);
            }
        };
    }

    @FunctionalInterface
    public interface CloseableScope extends AutoCloseable {

        @Override
        void close();
    }
}