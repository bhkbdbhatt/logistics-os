package io.logisticsos.ingest;

/** Callback the service uses to hand a validated envelope to the transport layer. */
public interface IngestPublisher {

    void publish(String topic, io.logisticsos.events.EventEnvelope envelope);
}