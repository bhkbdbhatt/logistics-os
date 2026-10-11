package io.logisticsos.events.kafka;

import org.apache.avro.Schema;

/** Resolves the Avro schema for a schema-registry subject. */
public interface SchemaResolver {

    /** Subject uses record-name strategy: {@code <payloadSchema>-value}. Never returns null. */
    Schema resolve(String subject);

    interface Factory {

        /** Build a resolver from Kafka serializer/deserializer properties. */
        SchemaResolver create(java.util.Map<String, ?> configs);
    }
}