package io.logisticsos.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutboxPublisherTest {

    private final InMemoryOutboxRepository repository = new InMemoryOutboxRepository(2);
    private final OutboxPublisher publisher = new OutboxPublisher(repository);

    private OutboxRecord sample(String id, String dedupeKey) {
        return OutboxRecord.builder()
                .id(id).dedupeKey(dedupeKey)
                .aggregateType("shipment").aggregateId("S-" + id).aggregateVersion(1)
                .topic("obs.shipment.events.v1").partitionKey("S-" + id)
                .build();
    }

    @Test
    void dispatchesPendingRowsInOrder() {
        repository.save(sample("1", "dk-1"));
        repository.save(sample("2", "dk-2"));
        List<String> published = new ArrayList<>();

        OutboxPublisher.PublishSummary summary =
                publisher.dispatchBatch(10, record -> published.add(record.id()));

        assertThat(published).containsExactly("1", "2");
        assertThat(summary.polled()).isEqualTo(2);
        assertThat(summary.dispatched()).isEqualTo(2);
        assertThat(summary.failed()).isZero();
        assertThat(repository.countPending()).isZero();
    }

    @Test
    void failureRetriedThenFailedOut() {
        repository.save(sample("1", "dk-1"));
        var failingDispatcher = new OutboxDispatcher() {
            @Override
            public void dispatch(OutboxRecord record) {
                throw new IllegalStateException("broker down");
            }
        };

        publisher.dispatchBatch(10, failingDispatcher);
        assertThat(repository.countPending()).isEqualTo(1); // still retryable
        publisher.dispatchBatch(10, failingDispatcher);
        assertThat(repository.countPending()).isZero(); // exhausted -> FAILED

        assertThat(repository.findById("1")).get()
                .extracting(OutboxRecord::state).isEqualTo(OutboxState.FAILED);
    }

    @Test
    void summariesReflectMixedOutcome() {
        repository.save(sample("1", "dk-1"));
        repository.save(sample("2", "dk-2"));
        repository.save(sample("3", "dk-3"));

        OutboxPublisher.PublishSummary summary = publisher.dispatchBatch(10, record -> {
            if (record.id().equals("2")) {
                throw new IOException("timeout");
            }
        });

        assertThat(summary.polled()).isEqualTo(3);
        assertThat(summary.dispatched()).isEqualTo(2);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.allFailed()).isFalse();
    }
}