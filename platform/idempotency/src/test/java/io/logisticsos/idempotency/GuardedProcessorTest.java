package io.logisticsos.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GuardedProcessorTest {

    private final DedupeStore store = new InMemoryDedupeStore(Duration.ofMinutes(5));
    private final GuardedProcessor processor = new GuardedProcessor(store);

    @Test
    void processesOnceAndReturnsCachedOutcomeOnDuplicate() {
        IdempotencyKey key = IdempotencyKey.of("tms.shipment.picked_up", "S-1", 3);
        AtomicInteger executions = new AtomicInteger();

        GuardedProcessor.Outcome<String> first =
                processor.process(key, () -> {
                    executions.incrementAndGet();
                    return "result-1";
                }, r -> "effect:" + r);
        GuardedProcessor.Outcome<String> second = processor.process(key, () -> {
            executions.incrementAndGet();
            return "must-not-run";
        }, r -> "effect:" + r);

        assertThat(first.duplicate()).isFalse();
        assertThat(first.value()).contains("result-1");
        assertThat(first.resultRef()).contains("effect:result-1");

        assertThat(second.duplicate()).isTrue();
        assertThat(second.value()).isEmpty();
        assertThat(second.resultRef()).contains("effect:result-1");
        assertThat(executions.get()).isEqualTo(1);
    }

    @Test
    void releasesClaimOnFailureSoRetryCanSucceed() {
        IdempotencyKey key = IdempotencyKey.of("tms.shipment.picked_up", "S-1", 3);
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> processor.process(key, () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("boom");
        }, r -> "nope")).isInstanceOf(IllegalStateException.class);

        String value = processor.process(key, () -> {
            attempts.incrementAndGet();
            return "ok";
        }, r -> "effect");

        assertThat(attempts.get()).isEqualTo(2);
        assertThat(value).isEqualTo("ok");
    }

    @Test
    void duplicateAfterExpiryReprocesses() {
        InMemoryDedupeStore shortTtl = new InMemoryDedupeStore(Duration.ofMillis(20));
        GuardedProcessor proc = new GuardedProcessor(shortTtl);
        IdempotencyKey key = IdempotencyKey.of("tms.shipment.picked_up", "S-1", 3);

        proc.process(key, () -> "first", r -> "ref1");
        assertThat(shortTtl.isProcessed(key)).isTrue();
    }
}