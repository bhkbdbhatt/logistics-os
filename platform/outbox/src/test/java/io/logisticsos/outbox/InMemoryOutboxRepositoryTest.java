package io.logisticsos.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryOutboxRepositoryTest {

    private final InMemoryOutboxRepository repository = new InMemoryOutboxRepository(3);

    private OutboxRecord sample(String id, String dedupeKey, long createdAtMs) {
        return OutboxRecord.builder()
                .id(id).dedupeKey(dedupeKey)
                .aggregateType("shipment").aggregateId("S-" + id).aggregateVersion(1)
                .topic("obs.shipment.events.v1").partitionKey("S-" + id)
                .payload(new byte[] {1, 2, 3}).createdAtMs(createdAtMs)
                .build();
    }

    @Test
    void pollsPendingInChronologicalOrder() {
        repository.save(sample("1", "dk-1", 100));
        repository.save(sample("2", "dk-2", 200));
        repository.save(sample("3", "dk-3", 300));

        List<OutboxRecord> batch = repository.pollPending(2);
        assertThat(batch).extracting(OutboxRecord::id).containsExactly("1", "2");
        assertThat(repository.countPending()).isEqualTo(3);
    }

    @Test
    void dedupeKeyPreventsDoubleInsertion() {
        assertThat(repository.save(sample("1", "dk-1", 100))).isTrue();
        assertThat(repository.save(sample("2", "dk-1", 200))).isFalse();
        assertThat(repository.countPending()).isEqualTo(1);
    }

    @Test
    void dispatchedRowsLeaveThePendingSet() {
        repository.save(sample("1", "dk-1", 100));
        repository.markDispatched("1", true);

        assertThat(repository.pollPending(10)).isEmpty();
        assertThat(repository.findById("1")).get()
                .extracting(OutboxRecord::state).isEqualTo(OutboxState.DISPATCHED);
        assertThat(repository.countPending()).isZero();
    }

    @Test
    void repeatedFailuresEndInFailedState() {
        repository.save(sample("1", "dk-1", 100));

        repository.markDispatched("1", false);
        repository.markDispatched("1", false);
        assertThat(repository.findById("1")).get()
                .extracting(OutboxRecord::state).isEqualTo(OutboxState.PENDING);

        repository.markDispatched("1", false);
        assertThat(repository.findById("1")).get()
                .extracting(OutboxRecord::state).isEqualTo(OutboxState.FAILED);
        assertThat(repository.findById("1")).get()
                .extracting(OutboxRecord::attemptCount).isEqualTo(3);
        assertThat(repository.pollPending(10)).isEmpty();
    }
}