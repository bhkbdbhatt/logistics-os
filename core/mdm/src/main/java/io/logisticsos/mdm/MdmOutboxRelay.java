package io.logisticsos.mdm;

import io.logisticsos.outbox.OutboxDispatcher;
import io.logisticsos.outbox.OutboxPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Scheduled relay for the MDM outbox (ADR-0007/0021). Polls pending rows and dispatches them; safe
 * under replay because the store dedupes on save and the dispatcher is keyed/idempotent.
 */
public final class MdmOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(MdmOutboxRelay.class);

    private final OutboxPublisher publisher;
    private final OutboxDispatcher dispatcher;
    private final int batchSize;

    public MdmOutboxRelay(OutboxPublisher publisher, OutboxDispatcher dispatcher, int batchSize) {
        this.publisher = publisher;
        this.dispatcher = dispatcher;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${logistics.mdm.outbox.poll-interval-ms:1000}")
    public void relay() {
        OutboxPublisher.PublishSummary summary = publisher.dispatchBatch(batchSize, dispatcher);
        if (summary.dispatched() > 0 || summary.failed() > 0) {
            log.info("mdm outbox relay: polled={} dispatched={} failed={} remaining={}",
                    summary.polled(), summary.dispatched(), summary.failed(), summary.remainingPending());
        }
    }
}
