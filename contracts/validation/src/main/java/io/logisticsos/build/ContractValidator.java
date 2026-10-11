package io.logisticsos.build;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.avro.Schema;

/**
 * CI gate over contracts/:
 * 1. every .avsc parses in ONE Schema.Parser (catches duplicate fullnames and unresolved cross-file refs);
 * 2. every payloadSchema in payload-registry.json resolves;
 * 3. every top-level record in a payload namespace has a registry entry;
 * 4. eventType keys are unique.
 */
public final class ContractValidator {

    private static final Set<String> PAYLOAD_NS = Set.of(
            "io.logisticsos.shipment.v1",
            "io.logisticsos.mdm.v1",
            "io.logisticsos.orchestration.v1",
            "io.logisticsos.obs.v1");

    private ContractValidator() {
    }

    public static void main(String[] args) throws Exception {
        Path contracts = Paths.get(args[0]);
        Path avroDir = contracts.resolve("avro");
        Path registryFile = contracts.resolve("payload-registry.json");

        List<String> errors = new ArrayList<>();
        Schema.Parser parser = new Schema.Parser();
        Set<String> definedNames = new LinkedHashSet<>();
        Set<String> fileDeclaredRecords = new LinkedHashSet<>();

        try (Stream<Path> walk = Files.walk(avroDir)) {
            List<Path> avsc = walk.filter(p -> p.toString().endsWith(".avsc")).sorted().toList();
            for (Path file : avsc) {
                try {
                    Schema root = parser.parse(file.toFile());
                    definedNames.add(root.getFullName());
                    if (root.getType() == Schema.Type.RECORD) {
                        fileDeclaredRecords.add(root.getFullName());
                    }
                } catch (Exception ex) {
                    errors.add("PARSE  " + relative(avroDir, file) + " : " + ex.getMessage());
                }
            }
        }

        Set<String> excluded = new LinkedHashSet<>();
        Path exclusionFile = contracts.resolve("validation/registry-exclusions.txt");
        if (Files.exists(exclusionFile)) {
            for (String line : Files.readAllLines(exclusionFile)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) excluded.add(trimmed);
            }
        }

        JsonNode registry = new ObjectMapper().readTree(registryFile.toFile());
        Set<String> referenced = new LinkedHashSet<>();
        Set<String> eventTypes = new LinkedHashSet<>();
        for (JsonNode entry : registry.path("entries")) {
            String et = entry.path("eventType").asText();
            String ps = entry.path("payloadSchema").asText();
            if (!eventTypes.add(et)) errors.add("REGISTRY duplicate eventType: " + et);
            referenced.add(ps);
        }

        for (String ref : referenced) {
            if (parser.getSchema(ref) == null) {
                errors.add("REGISTRY unresolved payloadSchema: " + ref);
            }
        }

        for (String name : fileDeclaredRecords) {
            if (isPayloadNamespace(name) && !referenced.contains(name) && !excluded.contains(name)) {
                errors.add("CONTRACT unreferenced top-level record in payload namespace"
                        + " (add to payload-registry.json or registry-exclusions.txt): " + name);
            }
        }

        if (!errors.isEmpty()) {
            System.err.println("Contract validation FAILED (" + errors.size() + "):");
            errors.forEach(e -> System.err.println("  - " + e));
            System.exit(1);
        }
        System.out.println("Contract validation OK: " + definedNames.size()
                + " defined names, " + fileDeclaredRecords.size() + " top-level records, "
                + referenced.size() + " registry payloads.");
    }

    private static boolean isPayloadNamespace(String fullName) {
        for (String ns : PAYLOAD_NS) {
            if (fullName.startsWith(ns + ".")) return true;
        }
        return false;
    }

    private static String relative(Path base, Path file) {
        return base.relativize(file).toString().replace('\\', '/');
    }
}