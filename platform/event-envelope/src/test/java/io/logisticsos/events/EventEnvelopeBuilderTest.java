package io.logisticsos.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;

class EventEnvelopeBuilderTest {

    private static final long NOW = 1_800_000_000_000L;

    private static Schema envelopeSchema() {
        Schema.Parser parser = new Schema.Parser();
        return parser.parse(EventEnvelopeBuilderTest.class
                .getResourceAsStream("/io/logisticsos/events/EventEnvelope.avsc"));
    }

    @Test
    void buildsCanonicalDefaults() {
        EventEnvelope e = new EventEnvelopeBuilder(NOW)
                .eventType("io.logisticsos.shipment.v1.ShipmentPickedUp")
                .tenantId("acme")
                .aggregateType("shipment")
                .aggregateId("01ARZ3NDEKTSV4RRFFQ69G5FAV")
                .aggregateVersion(3)
                .sourceSystem("tms")
                .build();

        assertThat(Ulid.isValid(e.eventId())).isTrue();
        assertThat(Ulid.timestampOf(e.eventId())).isEqualTo(NOW);
        assertThat(e.idempotencyKey())
                .isEqualTo("io.logisticsos.shipment.v1.ShipmentPickedUp:01ARZ3NDEKTSV4RRFFQ69G5FAV:3");
        assertThat(e.correlationId()).isEqualTo("shipment:01ARZ3NDEKTSV4RRFFQ69G5FAV");
        assertThat(e.schemaSubject()).isEqualTo("io.logisticsos.shipment.v1.ShipmentPickedUp-value");
        assertThat(e.occurredAt()).isEqualTo(NOW);
        assertThat(e.recordedAt()).isEqualTo(NOW);
        assertThat(e.partitionSequence()).isEqualTo(-1);
        assertThat(e.producer()).isEqualTo("unknown@0.0.0");
    }

    @Test
    void requiresIdentityAndRoutingFields() {
        assertThatThrownBy(() -> new EventEnvelopeBuilder(NOW).eventType("tms.shipment.picked_up").build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenantId");
        assertThatThrownBy(() -> new EventEnvelopeBuilder(NOW)
                .eventType("tms.shipment.picked_up").tenantId("acme").aggregateType("shipment").build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("aggregateId");
    }

    @Test
    void explicitOverridesWin() {
        EventEnvelope e = new EventEnvelopeBuilder(NOW)
                .eventType("io.logisticsos.shipment.v1.ShipmentPickedUp")
                .tenantId("acme").aggregateType("shipment").aggregateId("S-1")
                .idempotencyKey("producer-key").correlationId("corr-9")
                .partitionSequence(42L)
                .build();

        assertThat(e.idempotencyKey()).isEqualTo("producer-key");
        assertThat(e.correlationId()).isEqualTo("corr-9");
        assertThat(e.partitionSequence()).isEqualTo(42);
    }

    @Test
    void avroRoundTripPreservesEveryFieldAndPayload() throws Exception {
        byte[] payload = {0, 1, 2, (byte) 200};
        EventEnvelope original = new EventEnvelopeBuilder(NOW)
                .eventType("io.logisticsos.shipment.v1.ShipmentPickedUp")
                .tenantId("acme").aggregateType("shipment").aggregateId("S-1")
                .aggregateVersion(7).partitionSequence(42)
                .occurredAt(NOW - 5).recordedAt(NOW)
                .producer("acl-tms@0.1.0").correlationId("corr-1").causationId("evt-0")
                .traceparent("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
                .sourceSystem("legacy-tms")
                .payload(payload)
                .build();

        Schema schema = envelopeSchema();
        byte[] encoded = EventEnvelopeCodec.encode(original, schema);
        EventEnvelope decoded = EventEnvelopeCodec.decode(encoded, schema);

        assertThat(decoded.eventId()).isEqualTo(original.eventId());
        assertThat(decoded.eventType()).isEqualTo(original.eventType());
        assertThat(decoded.eventVersion()).isEqualTo(1);
        assertThat(decoded.tenantId()).isEqualTo("acme");
        assertThat(decoded.aggregateType()).isEqualTo("shipment");
        assertThat(decoded.aggregateId()).isEqualTo("S-1");
        assertThat(decoded.aggregateVersion()).isEqualTo(7);
        assertThat(decoded.partitionSequence()).isEqualTo(42);
        assertThat(decoded.occurredAt()).isEqualTo(NOW - 5);
        assertThat(decoded.recordedAt()).isEqualTo(NOW);
        assertThat(decoded.producer()).isEqualTo("acl-tms@0.1.0");
        assertThat(decoded.correlationId()).isEqualTo("corr-1");
        assertThat(decoded.causationId()).isEqualTo("evt-0");
        assertThat(decoded.traceparent()).isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        assertThat(decoded.schemaSubject()).isEqualTo("io.logisticsos.shipment.v1.ShipmentPickedUp-value");
        assertThat(decoded.sourceSystem()).isEqualTo("legacy-tms");
        assertThat(Arrays.equals(decoded.payload(), payload)).isTrue();
    }

    @Test
    void payloadCopiesDefensively() {
        byte[] payload = {1, 2, 3};
        EventEnvelope e = new EventEnvelopeBuilder(NOW)
                .eventType("t").tenantId("acme").aggregateType("shipment").aggregateId("S-1")
                .payload(payload).build();
        payload[0] = 99;

        assertThat(e.payload()[0]).isEqualTo(1);
        e.payload()[0] = 7;
        assertThat(e.payload()[0]).isEqualTo(1);
    }
}