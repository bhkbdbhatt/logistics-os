package io.logisticsos.events.kafka;

import java.io.File;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import org.apache.avro.Schema;

/**
 * Resolves schemas from classpath resources using the contract layout (subject
 * {@code io.logisticsos.obs.v1.Telemetry-value} -> resource {@code io/logisticsos/obs/v1/Telemetry.avsc}).
 *
 * Because payload schemas reference shared types ({@code io.logisticsos.types.*},
 * {@code io.logisticsos.shipment.ShipmentStatus}, ...), the resolver parses <b>all</b> .avsc found
 * under {@code io/logisticsos} into one shared {@link Schema.Parser}, retrying until the dependency
 * graph closes. Keeps {@code contracts/} the single source of truth and makes services locally
 * runnable without a Schema Registry.
 */
public final class ResourceSchemaResolver implements SchemaResolver {

    public static final String SCHEMA_RESOURCE_CONFIG = "schema.resource";
    private static final String VALUE_SUFFIX = "-value";
    private static final Map<ClassLoader, ParserHolder> CACHE = new ConcurrentHashMap<>();

    @Override
    public Schema resolve(String subject) {
        String fullName = fullNameOf(subject);
        ParserHolder holder = CACHE.computeIfAbsent(Thread.currentThread().getContextClassLoader(),
                ResourceSchemaResolver::scanAndParse);
        Schema schema = holder.parser.getSchema(fullName);
        if (schema == null) {
            throw new IllegalStateException("no schema on classpath for subject " + subject
                    + " (registered: " + holder.names + ")");
        }
        return schema;
    }

    private static String fullNameOf(String subject) {
        if (!subject.endsWith(VALUE_SUFFIX)) {
            throw new IllegalArgumentException("expected record-name-style subject ending in "
                    + VALUE_SUFFIX + ": " + subject);
        }
        return subject.substring(0, subject.length() - VALUE_SUFFIX.length());
    }

    private static ParserHolder scanAndParse(ClassLoader loader) {
        List<URL> resources = new ArrayList<>();
        try {
            var enumeration = loader.getResources("io/logisticsos");
            while (enumeration.hasMoreElements()) {
                collectAvsc(enumeration.nextElement(), resources);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("failed to scan io/logisticsos resources", ex);
        }

        Map<String, String> byName = new LinkedHashMap<>();
        Schema.Parser parser = new Schema.Parser();
        List<URL> remaining = new ArrayList<>(resources);
        boolean progress = true;
        while (progress && !remaining.isEmpty()) {
            progress = false;
            List<URL> next = new ArrayList<>();
            for (URL url : remaining) {
                try {
                    Schema schema = parser.parse(url);
                    byName.put(schema.getFullName(), url.toString());
                    progress = true;
                } catch (Exception ex) {
                    // unresolved name references are deferred until their dependencies parse
                    next.add(url);
                }
            }
            remaining = next;
        }
        if (!remaining.isEmpty()) {
            throw new IllegalStateException("schemas failed to resolve dependency graph: "
                    + remaining.stream().map(URL::toString).toList());
        }
        return new ParserHolder(parser, byName.keySet());
    }

    private static void collectAvsc(URL dirUrl, List<URL> out) throws Exception {
        if ("file".equalsIgnoreCase(dirUrl.getProtocol())) {
            collectFromDir(new File(dirUrl.toURI()), out);
        } else if ("jar".equalsIgnoreCase(dirUrl.getProtocol())) {
            try (JarFile jar = ((JarURLConnection) dirUrl.openConnection()).getJarFile()) {
                String base = "io/logisticsos/";
                jar.stream()
                        .filter(e -> !e.isDirectory() && e.getName().startsWith(base) && e.getName().endsWith(".avsc"))
                        .map(e -> Thread.currentThread().getContextClassLoader().getResource(e.getName()))
                        .filter(java.util.Objects::nonNull)
                        .forEach(out::add);
            }
        }
    }

    private static void collectFromDir(File dir, List<URL> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectFromDir(child, out);
            } else if (child.getName().endsWith(".avsc")) {
                try {
                    out.add(child.toURI().toURL());
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }
        }
    }

    private record ParserHolder(Schema.Parser parser, List<String> names) {
    }

    public static final class Factory implements SchemaResolver.Factory {

        @Override
        public SchemaResolver create(Map<String, ?> configs) {
            return new ResourceSchemaResolver();
        }
    }
}