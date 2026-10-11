package io.logisticsos.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * In-memory user directory for {@code mode=local} (ADR-0020). Loads {@code users.json}:
 *
 * <pre>
 * { "users": [ { "username": "admin", "password": "{noop}admin", "tenant": "acme",
 *               "roles": ["admin", "operator", "viewer"] } ] }
 * </pre>
 *
 * Passwords use Spring's {@link PasswordEncoder} (delegating — {@code {noop}} for dev, {@code {bcrypt}}
 * for anything real).
 */
public final class LocalUserStore {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Map<String, LocalUser> users;
    private final PasswordEncoder encoder;

    public LocalUserStore(List<LocalUser> users, PasswordEncoder encoder) {
        this.encoder = encoder;
        this.users = new LinkedHashMap<>();
        for (LocalUser user : users) {
            this.users.put(user.username(), user);
        }
    }

    public LocalUser authenticate(String username, String rawPassword) {
        LocalUser user = users.get(username);
        if (user == null || rawPassword == null || !encoder.matches(rawPassword, user.password())) {
            throw new BadCredentialsException("invalid credentials");
        }
        return user;
    }

    public static List<LocalUser> load(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return parse(JSON.readTree(in));
        } catch (IOException e) {
            throw new IllegalStateException("failed to load users file: " + file, e);
        }
    }

    static List<LocalUser> parse(JsonNode root) {
        JsonNode users = root.path("users");
        if (!users.isArray()) {
            throw new IllegalStateException("users.json must contain a 'users' array");
        }
        List<LocalUser> result = new ArrayList<>();
        for (JsonNode node : users) {
            String username = node.path("username").asText(null);
            String password = node.path("password").asText(null);
            String tenant = node.path("tenant").asText(null);
            if (username == null || password == null || tenant == null) {
                throw new IllegalStateException("each user needs username, password and tenant");
            }
            List<String> roles = new ArrayList<>();
            node.path("roles").forEach(role -> roles.add(role.asText()));
            result.add(new LocalUser(username, password, tenant, roles));
        }
        return result;
    }

    /** Built-in dev users when no {@code usersFile} is configured. Not for production. */
    public static List<LocalUser> devUsers() {
        return List.of(
                new LocalUser("admin", "{noop}admin", "acme", List.of("admin", "operator", "viewer")),
                new LocalUser("ops", "{noop}ops", "acme", List.of("operator", "viewer")),
                new LocalUser("viewer", "{noop}viewer", "acme", List.of("viewer")));
    }
}
