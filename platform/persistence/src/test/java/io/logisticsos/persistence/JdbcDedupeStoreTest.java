package io.logisticsos.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.logisticsos.idempotency.IdempotencyKey;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcDedupeStoreTest {

    private static final String MIGRATION = "/io/logisticsos/persistence/migration/V901__idempotency_keys.sql";

    private DataSource dataSource;
    private final AtomicLong now = new AtomicLong(1_000_000L);

    @BeforeEach
    void setUp() {
        dataSource = SchemaSupport.h2();
        SchemaSupport.apply(dataSource, MIGRATION);
    }

    private JdbcDedupeStore store() {
        return new JdbcDedupeStore(dataSource, Duration.ofMinutes(10), now::get);
    }

    @Test
    void claimWinsOnceThenIsDuplicate() {
        JdbcDedupeStore store = store();
        IdempotencyKey key = IdempotencyKey.of("ShipmentBooked", "SH-1", 1);

        assertThat(store.claim(key)).isTrue();
        assertThat(store.claim(key)).isFalse();
    }

    @Test
    void recordOutcomeMarksProcessedAndStoresResultRef() {
        JdbcDedupeStore store = store();
        IdempotencyKey key = IdempotencyKey.of("ShipmentBooked", "SH-1", 1);
        store.claim(key);

        store.recordOutcome(key, "corr-42");

        assertThat(store.isProcessed(key)).isTrue();
        assertThat(store.resultRef(key)).contains("corr-42");
    }

    @Test
    void recordOutcomeCreatesEntryWhenAbsent() {
        JdbcDedupeStore store = store();
        IdempotencyKey key = IdempotencyKey.of("ShipmentBooked", "SH-1", 1);

        store.recordOutcome(key, null);

        assertThat(store.isProcessed(key)).isTrue();
        assertThat(store.resultRef(key)).isEmpty();
    }

    @Test
    void releaseAllowsReclaim() {
        JdbcDedupeStore store = store();
        IdempotencyKey key = IdempotencyKey.of("ShipmentBooked", "SH-1", 1);
        store.claim(key);

        store.release(key);

        assertThat(store.isProcessed(key)).isFalse();
        assertThat(store.claim(key)).isTrue();
    }

    @Test
    void releaseIsNoOpAfterOutcomeRecorded() {
        JdbcDedupeStore store = store();
        IdempotencyKey key = IdempotencyKey.of("ShipmentBooked", "SH-1", 1);
        store.claim(key);
        store.recordOutcome(key, "corr-42");

        store.release(key);

        assertThat(store.isProcessed(key)).isTrue();
    }

    @Test
    void expiredEntryIsReclaimable() {
        JdbcDedupeStore store = new JdbcDedupeStore(dataSource, Duration.ofSeconds(1), now::get);
        IdempotencyKey key = IdempotencyKey.of("ShipmentBooked", "SH-1", 1);
        store.claim(key);
        store.recordOutcome(key, "corr-42");

        now.addAndGet(Duration.ofSeconds(2).toMillis());

        assertThat(store.isProcessed(key)).isFalse();
        assertThat(store.claim(key)).isTrue();
    }

    @Test
    void unknownKeyIsNotProcessed() {
        JdbcDedupeStore store = store();
        IdempotencyKey key = IdempotencyKey.of("ShipmentBooked", "SH-1", 1);

        assertThat(store.isProcessed(key)).isFalse();
        assertThat(store.resultRef(key)).isEmpty();
    }
}
