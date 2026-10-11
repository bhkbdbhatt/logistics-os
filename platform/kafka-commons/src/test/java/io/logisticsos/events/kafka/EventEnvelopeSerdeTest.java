package io.logisticsos.events.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.EventEnvelopeBuilder;
import io.logisticsos.events.Ulid;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventEnvelopeSerdeTest {

    private Path schemaFile;
    private Path tempDir;

    private static final String SUBJECT = "io.logisticsos.obs.v1.Telemetry-value";

    @BeforeEach
    void copySchema() throws IOException {
        tempDir = Files.createTempDirectory("event-envelope-test");
        schemaFile = tempDir.resolve("EventEnvelope.avsc");
        try (InputStream in = EventEnvelopeSerdeTest.class
                .getResourceAsStream("/io/logisticsos/events/EventEnvelope.avsc")) {
            Files.copy(in, schemaFile);
        }
    }

    @AfterEach
    void cleanup() throws IOException {
        Files.deleteIfExists(schemaFile);
        Files.deleteIfExists(tempDir);
    }

    private EventEnvelope sample() {
        return new EventEnvelopeBuilder()
                .eventType("io.logisticsos.obs.v1.Telemetry")
                .tenantId("acme").aggregateType("vehicle").aggregateId("V-77")
                .aggregateVersion(5).producer("obs-edge@0.1.0")
                .schemaSubject(SUBJECT)
                .payload(new byte[] {1, 2, 3, 4})
                .build();
    }

    @Test
    void serializerDeserializerRoundTrip() {
        Map<String, String> config = Map.of("schema.file", schemaFile.toString(),
                EventEnvelopeSerde.ENVELOPE_SUBJECT_CONFIG, EventEnvelopeSerde.DEFAULT_ENVELOPE_SUBJECT);

        EventEnvelopeSerializer serializer = new EventEnvelopeSerializer();
        serializer.configure(config, false);
        EventEnvelopeDeserializer deserializer = new EventEnvelopeDeserializer();
        deserializer.configure(config, false);

        EventEnvelope original = sample();
        byte[] bytes = serializer.serialize("obs.telemetry.v1", original);
        EventEnvelope decoded = deserializer.deserialize("obs.telemetry.v1", bytes);

        assertThat(decoded.eventId()).isEqualTo(original.eventId());
        assertThat(decoded.eventType()).isEqualTo("io.logisticsos.obs.v1.Telemetry");
        assertThat(decoded.tenantId()).isEqualTo("acme");
        assertThat(decoded.aggregateId()).isEqualTo("V-77");
        assertThat(decoded.aggregateVersion()).isEqualTo(5);
        assertThat(decoded.schemaSubject()).isEqualTo(SUBJECT);
        assertThat(decoded.payload()).containsExactly(1, 2, 3, 4);
    }

    @Test
    void subjectDefaultsFromConfig() {
        assertThat(EventEnvelopeSerde.DEFAULT_ENVELOPE_SUBJECT)
                .isEqualTo("io.logisticsos.events.EventEnvelope-value");

        Map<String, String> config = Map.of("schema.file", schemaFile.toString());
        EventEnvelopeDeserializer deserializer = new EventEnvelopeDeserializer();
        deserializer.configure(config, false);

        EventEnvelopeSerializer serializer = new EventEnvelopeSerializer();
        serializer.configure(config, false);

        byte[] bytes = serializer.serialize("obs.telemetry.v1", sample());
        EventEnvelope decoded = deserializer.deserialize("obs.telemetry.v1", bytes);

        assertThat(decoded.eventId()).isEqualTo(sample().eventId());
    }

    @Test
    void resourceResolverLoadsEnvelopeSchemaFromContractLayout() {
        ResourceSchemaResolver resolver = new ResourceSchemaResolver();
        org.apache.avro.Schema schema =
                resolver.resolve(EventEnvelopeSerde.DEFAULT_ENVELOPE_SUBJECT);

        assertThat(schema.getFullName()).isEqualTo("io.logisticsos.events.EventEnvelope");
        assertThat(schema.getField("payload").schema().getType())
                .isEqualTo(org.apache.avro.Schema.Type.BYTES);
    }

    @Test
    void resourceResolverRejectsUnknownSubject() {
        ResourceSchemaResolver resolver = new ResourceSchemaResolver();
        assertThatThrownBy(() -> resolver.resolve("io.logisticsos.unknown.Nope-value"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void headersCarryIdentityAndRouting() {
        EventEnvelope e = sample();
        var headers = EnvelopeHeaders.toHeaders(e);

        assertThat(EnvelopeHeaders.read(headers, EnvelopeHeaders.IDEMPOTENCY_KEY))
                .isEqualTo(e.idempotencyKey());
        assertThat(EnvelopeHeaders.read(headers, EnvelopeHeaders.EVENT_TYPE))
                .isEqualTo("io.logisticsos.obs.v1.Telemetry");
        assertThat(EnvelopeHeaders.read(headers, EnvelopeHeaders.AGGREGATE_ID)).isEqualTo("V-77");
        assertThat(EnvelopeHeaders.read(headers, EnvelopeHeaders.TENANT_ID)).isEqualTo("acme");
        assertThat(Long.parseLong(EnvelopeHeaders.read(headers, EnvelopeHeaders.OCCURRED_AT))
                > 0).isTrue();
    }

    @Test
    void routingConstants() {
        assertThat(EnvelopeRouting.partitionKey(sample())).isEqualTo("V-77");
        assertThat(EnvelopeRouting.topic(EnvelopeRouting.Layer.OBS, "telemetry", 1))
                .isEqualTo("obs.telemetry.v1");
        assertThat(EnvelopeRouting.topic(EnvelopeRouting.Layer.TMS, "shipment", "events", 1))
                .isEqualTo("tms.shipment.events.v1");
    }

    @Test
    void ulidIdempotencyFromBuilder() {
        assertThat(Ulid.isValid(sample().eventId())).isTrue();
    }
}