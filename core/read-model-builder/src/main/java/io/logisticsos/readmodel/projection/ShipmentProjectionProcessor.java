package io.logisticsos.readmodel.projection;

import io.logisticsos.events.EventEnvelope;
import java.util.Optional;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies shipment events to the {@code shipment-view} store, emitting the latest projection to
 * {@code shipment.state.v1}. Replay/out-of-order safe: an event is applied only when its occurredAt
 * is not older than the stored projection's updatedAt.
 */
public final class ShipmentProjectionProcessor implements Processor<String, EventEnvelope, String, ShipmentProjection> {

    private static final Logger log = LoggerFactory.getLogger(ShipmentProjectionProcessor.class);

    private final EnvelopePayloadDecoder decoder;
    private final String storeName;
    private KeyValueStore<String, ShipmentProjection> store;
    private ProcessorContext<String, ShipmentProjection> context;

    public ShipmentProjectionProcessor(EnvelopePayloadDecoder decoder, String storeName) {
        this.decoder = decoder;
        this.storeName = storeName;
    }

    @Override
    public void init(ProcessorContext<String, ShipmentProjection> context) {
        this.context = context;
        this.store = context.getStateStore(storeName);
    }

    @Override
    public void process(Record<String, EventEnvelope> record) {
        EventEnvelope envelope = record.value();
        if (envelope == null) {
            return;
        }
        ShipmentProjection incoming;
        try {
            incoming = ShipmentProjectionMapper.from(envelope, decoder.decode(envelope));
        } catch (Exception ex) {
            log.warn("skipping undecodable shipment event id={} type={}: {}", envelope.eventId(),
                    envelope.eventType(), ex.getMessage());
            return;
        }
        ShipmentProjection current = store.get(incoming.getShipmentId());
        if (current != null && incoming.getUpdatedAt() < current.getUpdatedAt()) {
            return; // stale/out-of-order replay
        }
        store.put(incoming.getShipmentId(), incoming);
        context.forward(new Record<>(incoming.getShipmentId(), incoming, record.timestamp()));
        log.debug("shipment projection updated id={} status={} updatedAt={}",
                incoming.getShipmentId(), incoming.getStatus(), incoming.getUpdatedAt());
        context.commit();
    }

    @Override
    public void close() {
        // nothing to release
    }
}