package io.logisticsos.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class LocalTokenServiceTest {

    private static final String SECRET = "logistics-os-unit-test-secret-32-bytes-min!!";

    private final SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    private final JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();

    @Test
    void issuesTokenCarryingTenantAndRoles() {
        LocalTokenService service = new LocalTokenService(encoder);
        LocalUser user = new LocalUser("alice", "{noop}secret", "acme", List.of("operator", "viewer"));

        String token = service.issue(user, Duration.ofMinutes(5));
        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("alice");
        assertThat(JwtClaimReader.readString(jwt, "tenant_id")).contains("acme");
        assertThat(JwtClaimReader.readString(jwt, "preferred_username")).contains("alice");
        assertThat(JwtClaimReader.readStringList(jwt, "roles")).containsExactly("operator", "viewer");
        assertThat(jwt.getExpiresAt()).isNotNull();
    }

    @Test
    void issuedTokenIsRejectedByADifferentSecret() {
        LocalTokenService service = new LocalTokenService(encoder);
        String token = service.issue(new LocalUser("alice", "{noop}secret", "acme", List.of("viewer")),
                Duration.ofMinutes(5));

        SecretKey other = new SecretKeySpec("a-totally-different-secret-key-32bytes!".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        JwtDecoder otherDecoder = NimbusJwtDecoder.withSecretKey(other).macAlgorithm(MacAlgorithm.HS256).build();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> otherDecoder.decode(token))
                .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
    }
}
