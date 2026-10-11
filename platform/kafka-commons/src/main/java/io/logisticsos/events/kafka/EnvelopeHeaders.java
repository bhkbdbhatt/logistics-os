package io.logisticsos.events.kafka;

import io.logisticsos.events.EventEnvelope;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Headers used to pass routing/identity metadata alongside the Avro value so consumers
 * can route and dedupe without decoding the payload.
 */
public final class EnvelopeHeaders {

    public static final String IDEMPOTENCY_KEY = "logisticsos.idempotency-key";
    public static final String EVENT_TYPE = "logisticsos.event-type";
    public static final String AGGREGATE_ID = "logisticsos.aggregate-id";
    public static final String OCCURRED_AT = "logisticsos.occurred-at-ms";
    public static final String TENANT_ID = "logisticsos.tenant-id";

    private EnvelopeHeaders() {
    }

    /** Set the canonical identity headers on a {@link org.apache.kafka.clients.producer.ProducerRecord}. */
    public static Headers toHeaders(EventEnvelope envelope) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(IDEMPOTENCY_KEY, envelope.idempotencyKey());
        values.put(EVENT_TYPE, envelope.eventType());
        values.put(AGGREGATE_ID, envelope.aggregateId());
        values.put(OCCURRED_AT, Long.toString(envelope.occurredAt()));
        values.put(TENANT_ID, envelope.tenantId());
        RecordHeaders headers = new RecordHeaders();
        values.forEach((k, v) -> headers.add(new RecordHeader(k, v.getBytes(StandardCharsets.UTF_8))));
        return headers;
    }

    public static String read(Headers headers, String key) {
        if (headers == null) {
            return null;
        }
        for (Header header : headers.headers(key)) {
            if (header.value() != null) {
                return new String(header.value(), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    public static Map<String, String> readAll(Headers headers) {
        if (headers == null) {
            return Collections.emptyMap();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Header header : headers) {
            if (header.value() != null) {
                out.putIfAbsent(header.key(), new String(header.value(), StandardCharsets.UTF_8));
            }
        }
        return out;
    }
}