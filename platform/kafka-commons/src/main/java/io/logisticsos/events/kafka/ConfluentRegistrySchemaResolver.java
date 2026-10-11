package io.logisticsos.events.kafka;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import java.io.IOException;
import java.util.Map;
import org.apache.avro.Schema;

/**
 * Resolves subjects against a Confluent Schema Registry. The latest registered schema is cached
 * per subject and refreshed on demand.
 */
public final class ConfluentRegistrySchemaResolver implements SchemaResolver {

    public static final String SCHEMA_REGISTRY_URL_CONFIG = "schema.registry.url";
    public static final String SCHEMA_REGISTRY_CACHE_CAPACITY = "schema.registry.cache.capacity";

    private final SchemaRegistryClient client;

    public ConfluentRegistrySchemaResolver(String url, int cacheCapacity) {
        this.client = new CachedSchemaRegistryClient(url, Math.max(cacheCapacity, 1));
    }

    public ConfluentRegistrySchemaResolver(SchemaRegistryClient client) {
        this.client = client;
    }

    @Override
    public Schema resolve(String subject) {
        try {
            CachedSchemaRegistryClient cached = (CachedSchemaRegistryClient) client;
            io.confluent.kafka.schemaregistry.client.SchemaMetadata metadata =
                    cached.getLatestSchemaMetadata(subject);
            return new Schema.Parser().parse(metadata.getSchema());
        } catch (IOException | RestClientException ex) {
            throw new IllegalStateException("failed to resolve schema for subject " + subject, ex);
        }
    }

    public static final class Factory implements SchemaResolver.Factory {

        @Override
        public SchemaResolver create(Map<String, ?> configs) {
            Object url = configs.get(SCHEMA_REGISTRY_URL_CONFIG);
            if (url == null) {
                url = System.getProperty(SCHEMA_REGISTRY_URL_CONFIG);
            }
            if (url == null) {
                throw new IllegalStateException("missing " + SCHEMA_REGISTRY_URL_CONFIG);
            }
            Object capacity = configs.get(SCHEMA_REGISTRY_CACHE_CAPACITY);
            int cap = capacity instanceof Number n ? n.intValue() : 1000;
            return new ConfluentRegistrySchemaResolver(String.valueOf(url), cap);
        }
    }
}