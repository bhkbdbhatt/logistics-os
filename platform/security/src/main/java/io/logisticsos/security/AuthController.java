package io.logisticsos.security;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-only login endpoint for {@code mode=local} (ADR-0020). Exchanges username/password for an
 * HS256 bearer token. Never registered in {@code mode=oidc}.
 */
@RestController
public class AuthController {

    private final LocalUserStore users;
    private final LocalTokenService tokens;
    private final LogisticsSecurityProperties properties;

    public AuthController(LocalUserStore users, LocalTokenService tokens, LogisticsSecurityProperties properties) {
        this.users = users;
        this.tokens = tokens;
        this.properties = properties;
    }

    @PostMapping("/auth/login")
    public Map<String, Object> login(@RequestBody LoginRequest request) {
        LocalUser user = users.authenticate(request.username(), request.password());
        String token = tokens.issue(user, properties.getLocal().getTokenTtl());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", token);
        body.put("token_type", "Bearer");
        body.put("expires_in", properties.getLocal().getTokenTtl().toSeconds());
        body.put("tenant", user.tenant());
        body.put("roles", user.roles());
        return body;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> unauthorized(BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid_credentials"));
    }

    public record LoginRequest(String username, String password) {
    }
}
