package io.logisticsos.entitlement;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.logisticsos.entitlement.Entitlement.Mode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds an {@link Entitlement} from the {@code ENTITLEMENT_LICENSE} value.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>blank/absent → permissive {@link Entitlement#evaluation()};</li>
 *   <li>a value that does not start with <code>{</code> → treated as a file path and read;</li>
 *   <li>otherwise → parsed as inline JSON.</li>
 * </ol>
 *
 * <p>Soft phase: signature is retained but not yet verified (see docs/product/licensing.md).
 */
public final class EntitlementLoader {

    public static final String ENV_VAR = "ENTITLEMENT_LICENSE";

    private static final ObjectMapper JSON =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private EntitlementLoader() {
    }

    public static Entitlement load() {
        return load(System.getenv(ENV_VAR));
    }

    public static Entitlement load(String license) {
        if (license == null || license.isBlank()) {
            return Entitlement.evaluation();
        }
        String value = license.trim();
        if (value.startsWith("{")) {
            return parse(value);
        }
        Path path = Path.of(value);
        try {
            return parse(Files.readString(path));
        } catch (IOException e) {
            throw new EntitlementException("cannot read license file: " + path, e);
        }
    }

    public static Entitlement parse(String json) {
        try {
            LicenseDto dto = JSON.readValue(json, LicenseDto.class);
            if (dto.edition() == null || dto.edition().isBlank()) {
                throw new EntitlementException("license is missing 'edition'");
            }
            Map<String, Boolean> features =
                    dto.features() == null ? Map.of() : new LinkedHashMap<>(dto.features());
            Instant expiresAt = dto.expiresAt() == null ? null : Instant.parse(dto.expiresAt());
            return new Entitlement(dto.edition(), dto.version(), dto.tenant(), expiresAt, features, Mode.LICENSED);
        } catch (EntitlementException e) {
            throw e;
        } catch (Exception e) {
            throw new EntitlementException("invalid license json", e);
        }
    }

    /** Wire shape of a license file; unknown fields are ignored by Jackson. */
    record LicenseDto(String licenseId, String tenant, String edition, String version,
            String issuedAt, String expiresAt, Map<String, Boolean> features, String signature) {
    }
}