package io.logisticsos.events.kafka;

/** Serde configuration keys and defaults for the EventEnvelope value type. */
public final class EventEnvelopeSerde {

    /** The envelope itself is serialized against one fixed schema regardless of event type. */
    public static final String ENVELOPE_SUBJECT_CONFIG = "logisticsos.envelope.subject";
    public static final String DEFAULT_ENVELOPE_SUBJECT = "io.logisticsos.events.EventEnvelope-value";

    private EventEnvelopeSerde() {
    }
}