package io.logisticsos.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.logisticsos.outbox.OutboxRecord;
import io.logisticsos.outbox.OutboxState;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcOutboxRepositoryTest {

    private static final String MIGRATION = "/io/logisticsos/persistence/migration/V900__outbox.sql";

    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = SchemaSupport.h2();
        SchemaSupport.apply(dataSource, MIGRATION);
    }

    private static OutboxRecord record(String id, String dedupeKey, long createdAtMs) {
        return OutboxRecord.builder()
                .id(id)
                .dedupeKey(dedupeKey)
                .aggregateType("Shipment")
                .aggregateId("SH-1")
                .aggregateVersion(1)
                .topic("mdm.shipment.events.v1")
                .partitionKey("SH-1")
                .header("tenant_id", "acme")
                .payload("payload".getBytes(StandardCharsets.UTF_8))
                .createdAtMs(createdAtMs)
                .build();
    }

    @Test
    void savesAndPollsPendingInCreationOrder() {
        JdbcOutboxRepository repository = new JdbcOutboxRepository(dataSource);
        assertThat(repository.save(record("b", "k2", 2000))).isTrue();
        assertThat(repository.save(record("a", "k1", 1000))).isTrue();

        List<OutboxRecord> pending = repository.pollPending(10);

        assertThat(pending).extracting(OutboxRecord::id).containsExactly("a", "b");
        assertThat(repository.countPending()).isEqualTo(2);
    }

    @Test
    void duplicateDedupeKeyIsSilentNoOp() {
        JdbcOutboxRepository repository = new JdbcOutboxRepository(dataSource);
        assertThat(repository.save(record("a", "k1", 1000))).isTrue();
        assertThat(repository.save(record("b", "k1", 2000))).isFalse();

        assertThat(repository.countPending()).isEqualTo(1);
        assertThat(repository.findById("b")).isEmpty();
    }

    @Test
    void pollPendingHonorsBatchSize() {
        JdbcOutboxRepository repository = new JdbcOutboxRepository(dataSource);
        repository.save(record("a", "k1", 1000));
        repository.save(record("b", "k2", 2000));
        repository.save(record("c", "k3", 3000));

        assertThat(repository.pollPending(2)).hasSize(2);
    }

    @Test
    void headersAndPayloadRoundTrip() {
        JdbcOutboxRepository repository = new JdbcOutboxRepository(dataSource);
        OutboxRecord original = record("a", "k1", 1000);
        repository.save(original);

        OutboxRecord loaded = repository.findById("a").orElseThrow();

        assertThat(loaded.headers()).containsEntry("tenant_id", "acme");
        assertThat(new String(loaded.payload(), StandardCharsets.UTF_8)).isEqualTo("payload");
        assertThat(loaded.state()).isEqualTo(OutboxState.PENDING);
    }

    @Test
    void markDispatchedSuccessMovesToDispatchedWithoutIncrementingAttempts() {
        JdbcOutboxRepository repository = new JdbcOutboxRepository(dataSource);
        repository.save(record("a", "k1", 1000));

        repository.markDispatched("a", true);

        OutboxRecord loaded = repository.findById("a").orElseThrow();
        assertThat(loaded.state()).isEqualTo(OutboxState.DISPATCHED);
        assertThat(loaded.attemptCount()).isZero();
        assertThat(repository.countPending()).isZero();
    }

    @Test
    void markDispatchedFailureRetriesThenFailsWhenExhausted() {
        JdbcOutboxRepository repository = new JdbcOutboxRepository(dataSource, 2);
        repository.save(record("a", "k1", 1000));

        repository.markDispatched("a", false);
        OutboxRecord afterFirst = repository.findById("a").orElseThrow();
        assertThat(afterFirst.state()).isEqualTo(OutboxState.PENDING);
        assertThat(afterFirst.attemptCount()).isEqualTo(1);

        repository.markDispatched("a", false);
        OutboxRecord afterSecond = repository.findById("a").orElseThrow();
        assertThat(afterSecond.state()).isEqualTo(OutboxState.FAILED);
        assertThat(afterSecond.attemptCount()).isEqualTo(2);
        assertThat(repository.countPending()).isZero();
    }
}
