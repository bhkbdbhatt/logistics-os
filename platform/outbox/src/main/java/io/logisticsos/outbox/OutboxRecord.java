package io.logisticsos.outbox;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One outbox row: the publishable event persisted atomically with the state change (ADR-0007).
 * {@code dedupeKey} is the canonical idempotency key (or producer key); the store must treat it as unique.
 */
public record OutboxRecord(
        String id,
        String dedupeKey,
        String aggregateType,
        String aggregateId,
        long aggregateVersion,
        String topic,
        String partitionKey,
        Map<String, String> headers,
        byte[] payload,
        long createdAtMs,
        OutboxState state,
        int attemptCount) {

    public OutboxRecord {
        payload = payload == null ? null : Arrays.copyOf(payload, payload.length);
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dedupeKey, "dedupeKey");
        Objects.requireNonNull(topic, "topic");
    }

    public byte[] payload() {
        return payload == null ? null : Arrays.copyOf(payload, payload.length);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String dedupeKey;
        private String aggregateType;
        private String aggregateId;
        private long aggregateVersion;
        private String topic;
        private String partitionKey;
        private Map<String, String> headers = new LinkedHashMap<>();
        private byte[] payload;
        private long createdAtMs = System.currentTimeMillis();
        private OutboxState state = OutboxState.PENDING;
        private int attemptCount;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder dedupeKey(String dedupeKey) {
            this.dedupeKey = dedupeKey;
            return this;
        }

        public Builder aggregateType(String aggregateType) {
            this.aggregateType = aggregateType;
            return this;
        }

        public Builder aggregateId(String aggregateId) {
            this.aggregateId = aggregateId;
            return this;
        }

        public Builder aggregateVersion(long aggregateVersion) {
            this.aggregateVersion = aggregateVersion;
            return this;
        }

        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public Builder partitionKey(String partitionKey) {
            this.partitionKey = partitionKey;
            return this;
        }

        public Builder header(String key, String value) {
            headers.put(key, value);
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
            return this;
        }

        public Builder payload(byte[] payload) {
            this.payload = payload;
            return this;
        }

        public Builder createdAtMs(long createdAtMs) {
            this.createdAtMs = createdAtMs;
            return this;
        }

        public Builder state(OutboxState state) {
            this.state = state;
            return this;
        }

        public Builder attemptCount(int attemptCount) {
            this.attemptCount = attemptCount;
            return this;
        }

        public OutboxRecord build() {
            if (partitionKey == null || partitionKey.isBlank()) {
                partitionKey = aggregateId;
            }
            return new OutboxRecord(id, dedupeKey, aggregateType, aggregateId, aggregateVersion,
                    topic, partitionKey, headers, payload, createdAtMs, state, attemptCount);
        }
    }
}