package io.logisticsos.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InMemoryDedupeStoreTest {

    private final InMemoryDedupeStore store = new InMemoryDedupeStore(Duration.ofMinutes(5));

    @Test
    void claimsOnceThenSeesProcessed() {
        IdempotencyKey key = IdempotencyKey.of("tms.shipment.picked_up", "S-1", 3);

        assertThat(store.claim(key)).isTrue();
        assertThat(store.isProcessed(key)).isFalse();
        assertThat(store.claim(key)).isFalse(); // duplicate while processing

        store.recordOutcome(key, "ref-1");
        assertThat(store.isProcessed(key)).isTrue();
        assertThat(store.resultRef(key)).contains("ref-1");
        assertThat(store.claim(key)).isFalse(); // processed -> still deduped
    }

    @Test
    void releaseAllowsRetry() {
        IdempotencyKey key = IdempotencyKey.of("tms.shipment.picked_up", "S-1", 3);
        assertThat(store.claim(key)).isTrue();
        store.release(key);

        assertThat(store.claim(key)).isTrue();
        assertThat(store.isProcessed(key)).isFalse();
    }

    @Test
    void ttlExpiryAllowsReprocessing() throws Exception {
        InMemoryDedupeStore shortTtl = new InMemoryDedupeStore(Duration.ofMillis(20));
        IdempotencyKey key = IdempotencyKey.of("tms.shipment.picked_up", "S-1", 3);

        assertThat(shortTtl.claim(key)).isTrue();
        shortTtl.recordOutcome(key, "ref");
        Thread.sleep(40);
        assertThat(shortTtl.isProcessed(key)).isFalse();
        assertThat(shortTtl.claim(key)).isTrue();
    }

    @Test
    void concurrentClaimsAllowExactlyOneWinner() throws Exception {
        IdempotencyKey key = IdempotencyKey.of("tms.shipment.picked_up", "S-1", 3);
        int threads = 8;
        AtomicInteger winners = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (store.claim(key)) {
                    winners.incrementAndGet();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(winners.get()).isEqualTo(1);
    }
}