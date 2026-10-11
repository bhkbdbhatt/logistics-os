package io.logisticsos.events.kafka;

import java.util.Map;

/**
 * Composite schema-resolver factory used by the serde:
 * 1. {@code schema.file}       -> a single local .avsc (tests/offline)
 * 2. {@code schema.resource}   -> classpath resource per the contracts layout (local dev)
 * 3. {@code schema.registry.url} -> Confluent Schema Registry (production)
 */
public final class SchemaResolvers {

    private SchemaResolvers() {
    }

    public static SchemaResolver create(Map<String, ?> configs) {
        if (configs.containsKey(LocalFileSchemaResolver.SCHEMA_FILE_CONFIG)) {
            return new LocalFileSchemaResolver.Factory().create(configs);
        }
        if (Boolean.parseBoolean(String.valueOf(configs.get(ResourceSchemaResolver.SCHEMA_RESOURCE_CONFIG)))) {
            return new ResourceSchemaResolver.Factory().create(configs);
        }
        return new ConfluentRegistrySchemaResolver.Factory().create(configs);
    }
}