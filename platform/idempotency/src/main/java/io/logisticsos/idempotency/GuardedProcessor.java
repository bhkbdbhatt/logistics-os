package io.logisticsos.idempotency;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Template implementing the mandatory consumer discipline (docs/schemas/common-types.md 4.2):
 * claim -> compute -> record outcome, with release on failure so the event can be retried safely.
 * The {@code loader} runs exactly once per key as long as the dedupe entry is live.
 */
public final class GuardedProcessor {

    private final DedupeStore store;

    public GuardedProcessor(DedupeStore store) {
        this.store = store;
    }

    public <R> Outcome<R> process(IdempotencyKey key, Supplier<R> loader, Function<R, String> outcomeRef) {
        if (!store.claim(key)) {
            return Outcome.duplicate(key, store.resultRef(key));
        }
        R value;
        try {
            value = loader.get();
        } catch (RuntimeException | Error ex) {
            store.release(key);
            throw ex;
        }
        String ref = outcomeRef.apply(value);
        store.recordOutcome(key, ref);
        return Outcome.processed(key, value, ref);
    }

    public record Outcome<R>(
            IdempotencyKey key,
            boolean duplicate,
            Optional<R> value,
            Optional<String> resultRef) {

        static <R> Outcome<R> duplicate(IdempotencyKey key, Optional<String> ref) {
            return new Outcome<>(key, true, Optional.empty(), ref);
        }

        static <R> Outcome<R> processed(IdempotencyKey key, R value, String ref) {
            return new Outcome<>(key, false, Optional.ofNullable(value), Optional.ofNullable(ref));
        }
    }
}