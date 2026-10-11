package io.logisticsos.readmodel.projection;

import io.logisticsos.events.EventEnvelope;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies inventory movements to the {@code inventory-view} store, emitting the latest projection
 * to {@code inventory.state.v1}. Version-guarded by {@code moveSequence} (monotonic per inventoryId)
 * so replay and out-of-order delivery cannot regress stock state (ADR-0007 idempotency discipline).
 */
public final class InventoryProjectionProcessor implements Processor<String, EventEnvelope, String, InventoryProjection> {

    private static final Logger log = LoggerFactory.getLogger(InventoryProjectionProcessor.class);

    private final EnvelopePayloadDecoder decoder;
    private final String storeName;
    private KeyValueStore<String, InventoryProjection> store;
    private ProcessorContext<String, InventoryProjection> context;

    public InventoryProjectionProcessor(EnvelopePayloadDecoder decoder, String storeName) {
        this.decoder = decoder;
        this.storeName = storeName;
    }

    @Override
    public void init(ProcessorContext<String, InventoryProjection> context) {
        this.context = context;
        this.store = context.getStateStore(storeName);
    }

    @Override
    public void process(Record<String, EventEnvelope> record) {
        EventEnvelope envelope = record.value();
        if (envelope == null) {
            return;
        }
        InventoryProjection incoming;
        try {
            incoming = InventoryProjectionMapper.from(envelope, decoder.decode(envelope));
        } catch (Exception ex) {
            log.warn("skipping undecodable inventory event id={} type={}: {}", envelope.eventId(),
                    envelope.eventType(), ex.getMessage());
            return;
        }
        InventoryProjection current = store.get(incoming.getInventoryId());
        if (current != null && incoming.getMoveSequence() <= current.getMoveSequence()) {
            return; // stale or duplicate movement
        }
        store.put(incoming.getInventoryId(), incoming);
        context.forward(new Record<>(incoming.getInventoryId(), incoming, record.timestamp()));
        log.debug("inventory projection updated id={} qty={} seq={}", incoming.getInventoryId(),
                incoming.getQuantity(), incoming.getMoveSequence());
        context.commit();
    }

    @Override
    public void close() {
        // nothing to release
    }
}