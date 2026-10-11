package io.logisticsos.events.kafka;

import io.logisticsos.events.EventEnvelope;

/** Partition-key and topic-name conventions (docs: topics are {@code <layer>.<domain>.<type>.vN}). */
public final class EnvelopeRouting {

    public enum Layer {
        OBS("obs"),
        ACL("acl"),
        L1("l1"),
        TMS("tms"),
        WMS("wms"),
        MDM("mdm"),
        L4("l4"),
        INTEL("intel"),
        ANALYTICS("analytics");

        private final String prefix;

        Layer(String prefix) {
            this.prefix = prefix;
        }

        public String prefix() {
            return prefix;
        }
    }

    private EnvelopeRouting() {
    }

    /** Partition key = aggregateId, so an aggregate's events are strictly ordered per partition. */
    public static String partitionKey(EventEnvelope envelope) {
        return envelope.aggregateId();
    }

    /** {@code <layer>.<domain>.vN} for single-type domains (obs.telemetry.v1, intel.prediction.v1, ...). */
    public static String topic(Layer layer, String domain, int version) {
        return layer.prefix() + "." + domain + ".v" + version;
    }

    /** {@code <layer>.<domain>.<type>.vN} for multi-type domains (tms.shipment.events.v1, ...). */
    public static String topic(Layer layer, String domain, String type, int version) {
        return layer.prefix() + "." + domain + "." + type + ".v" + version;
    }
}