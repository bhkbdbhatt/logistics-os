package io.logisticsos.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reads dotted-path claims from a {@link Jwt}, tolerating nested maps (e.g. Keycloak's realm_access). */
public final class JwtClaimReader {

    private JwtClaimReader() {
    }

    /** Resolve a claim by dotted path; returns the raw value or {@code null}. */
    public static Object resolve(Jwt jwt, String path) {
        if (jwt == null || path == null || path.isBlank()) {
            return null;
        }
        Object current = jwt.getClaims();
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    public static Optional<String> readString(Jwt jwt, String path) {
        Object value = resolve(jwt, path);
        if (value instanceof String s && !s.isBlank()) {
            return Optional.of(s);
        }
        return Optional.empty();
    }

    public static List<String> readStringList(Jwt jwt, String path) {
        Object value = resolve(jwt, path);
        if (value instanceof Collection<?> collection) {
            List<String> result = new ArrayList<>(collection.size());
            for (Object item : collection) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        if (value instanceof String s && !s.isBlank()) {
            return List.of(s);
        }
        return List.of();
    }
}
