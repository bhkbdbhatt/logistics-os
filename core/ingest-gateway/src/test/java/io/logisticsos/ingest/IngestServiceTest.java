package io.logisticsos.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.logisticsos.events.EventEnvelope;
import io.logisticsos.events.kafka.PayloadAvro;
import io.logisticsos.events.kafka.ResourceSchemaResolver;
import io.logisticsos.events.kafka.SchemaResolver;
import io.logisticsos.idempotency.GuardedProcessor;
import io.logisticsos.idempotency.InMemoryDedupeStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class IngestServiceTest {

    private static final String TELEMETRY_JSON = """
            {
              "telemetryId": "T-1",
              "deviceId": "DEV-7",
              "vehicleId": "VH-42",
              "capturedAt": 1720000000000,
              "geo": { "lat": 48.85, "lng": 2.35 },
              "speedKph": 78.5,
              "engineOn": true
            }
            """;

    private static ResourceSchemaResolver schemas;
    private RecordingPublisher publisher;
    private GuardedProcessor dedupe;

    @BeforeAll
    static void setUpSchemas() {
        schemas = new ResourceSchemaResolver();
    }

    private IngestService newService() {
        publisher = new RecordingPublisher();
        dedupe = new GuardedProcessor(new InMemoryDedupeStore(Duration.ofHours(48)));
        return new IngestService(schemas, publisher, dedupe);
    }

    @Test
    void acceptsValidTelemetryAndPublishesToObsTopic() {
        IngestService service = newService();

        IngestService.Result result = service.ingest(
                IngestKind.TELEMETRY, "acme", "truck-sim", null, TELEMETRY_JSON);

        assertThat(result.duplicate()).isFalse();
        assertThat(result.status()).isEqualTo("ACCEPTED");
        assertThat(publisher.topics).containsExactly("obs.telemetry.v1");
        EventEnvelope envelope = publisher.envelopes.get(0);
        assertThat(envelope.eventType()).isEqualTo("io.logisticsos.obs.v1.Telemetry");
        assertThat(envelope.aggregateType()).isEqualTo("device");
        assertThat(envelope.aggregateId()).isEqualTo("DEV-7");
        assertThat(envelope.tenantId()).isEqualTo("acme");
        assertThat(envelope.schemaSubject()).isEqualTo("io.logisticsos.obs.v1.Telemetry-value");
        assertThat(envelope.idempotencyKey()).startsWith("truck-sim:");
        assertThat(PayloadAvro.fromBytes(envelope.payload(), schemas.resolve(envelope.schemaSubject())))
                .satisfies(rec -> assertThat(rec.get("deviceId").toString()).isEqualTo("DEV-7"));
    }

    @Test
    void sameBodyIsDeduplicatedAndNotRepublished() {
        IngestService service = newService();

        assertThat(service.ingest(IngestKind.TELEMETRY, "acme", "truck-sim", null, TELEMETRY_JSON).duplicate())
                .isFalse();
        IngestService.Result second = service.ingest(
                IngestKind.TELEMETRY, "acme", "truck-sim", null, TELEMETRY_JSON);

        assertThat(second.duplicate()).isTrue();
        assertThat(publisher.topics).containsExactly("obs.telemetry.v1");
    }

    @Test
    void callerIdempotencyKeyWins() {
        IngestService service = newService();

        service.ingest(IngestKind.TELEMETRY, "acme", "truck-sim", "my-retry-key", TELEMETRY_JSON);
        IngestService.Result replay = service.ingest(
                IngestKind.TELEMETRY, "acme", "other-source", "my-retry-key", TELEMETRY_JSON);

        assertThat(replay.duplicate()).isTrue();
        assertThat(publisher.topics).containsExactly("obs.telemetry.v1");
    }

    @Test
    void rejectsInvalidJson() {
        IngestService service = newService();

        assertThatThrownBy(() -> service.ingest(
                IngestKind.TELEMETRY, "acme", "truck-sim", null,
                "{\"telemetryId\":\"T-2\",\"deviceId\":\"DEV-8\",\"capturedAt\":1720000000000}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid payload");
        assertThat(publisher.topics).isEmpty();
    }

    @Test
    void unknownKindIsRejectedBeforeValidation() {
        assertThatThrownBy(() -> IngestKind.fromPath("gps"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown ingest kind: gps");
    }

    private static final class RecordingPublisher implements IngestPublisher {
        final List<String> topics = new ArrayList<>();
        final List<EventEnvelope> envelopes = new ArrayList<>();

        @Override
        public void publish(String topic, EventEnvelope envelope) {
            topics.add(topic);
            envelopes.add(envelope);
        }
    }
}