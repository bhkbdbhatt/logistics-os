package io.logisticsos.events.kafka;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.avro.Schema;

/**
 * Resolves a single schema from a local .avsc file. Non-production convenience for tests and
 * offline dev tooling; production uses {@link ConfluentRegistrySchemaResolver}.
 */
public final class LocalFileSchemaResolver implements SchemaResolver {

    public static final String SCHEMA_FILE_CONFIG = "schema.file";

    private final String schemaFile;

    public LocalFileSchemaResolver(String schemaFile) {
        this.schemaFile = schemaFile;
    }

    @Override
    public Schema resolve(String subject) {
        try {
            return new Schema.Parser().parse(Files.newInputStream(Path.of(schemaFile)));
        } catch (IOException ex) {
            throw new IllegalStateException("failed to load schema from " + schemaFile, ex);
        }
    }

    public static final class Factory implements SchemaResolver.Factory {

        @Override
        public SchemaResolver create(Map<String, ?> configs) {
            Object file = configs.get(SCHEMA_FILE_CONFIG);
            if (file == null) {
                throw new IllegalStateException("missing " + SCHEMA_FILE_CONFIG);
            }
            return new LocalFileSchemaResolver(String.valueOf(file));
        }
    }
}