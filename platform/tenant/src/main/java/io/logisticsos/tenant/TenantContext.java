package io.logisticsos.tenant;

import java.util.Objects;
import java.util.Optional;

/**
 * Thread-local tenant binding, propagated across the service call stack. The Kafka/HTTP boundary
 * must resolve the tenant from headers or claims before processing and clear it afterwards.
 */
public final class TenantContext {

    private static final ThreadLocal<TenantId> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Optional<TenantId> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static TenantId require() {
        return current().orElseThrow(() -> new IllegalStateException("no tenant bound to this thread"));
    }

    /** Bind a tenant for the duration of {@code runnable}, restoring any previous binding afterwards. */
    public static void runAs(TenantId tenant, Runnable runnable) {
        try (TenantScope ignored = scope(tenant)) {
            runnable.run();
        }
    }

    /** Bind a tenant scoped to the lifetime of the returned handle. */
    public static TenantScope scope(TenantId tenant) {
        Objects.requireNonNull(tenant, "tenant");
        TenantId previous = CURRENT.get();
        CURRENT.set(tenant);
        return () -> {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        };
    }

    /** Clear the current binding (e.g. in a finally block). */
    public static void clear() {
        CURRENT.remove();
    }

    @FunctionalInterface
    public interface TenantScope extends AutoCloseable {

        @Override
        void close();
    }
}