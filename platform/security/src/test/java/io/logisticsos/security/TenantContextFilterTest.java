package io.logisticsos.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.logisticsos.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class TenantContextFilterTest {

    private static final String SECRET = "logistics-os-unit-test-secret-32-bytes-min!!";

    private final SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    private final JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    private final TenantContextFilter filter = new TenantContextFilter(new LogisticsSecurityProperties());

    @BeforeEach
    @AfterEach
    void reset() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void bindsTenantFromTokenAndOverridesHeader() throws Exception {
        authenticate("acme");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "attacker");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> tenantSeen = new AtomicReference<>();
        AtomicReference<String> headerSeen = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            tenantSeen.set(TenantContext.current().map(t -> t.value()).orElse(null));
            headerSeen.set(((HttpServletRequest) req).getHeader("X-Tenant-Id"));
        };

        filter.doFilter(request, response, chain);

        assertThat(tenantSeen.get()).isEqualTo("acme");
        assertThat(headerSeen.get()).isEqualTo("acme");
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void rejectsTokenWithoutTenantClaim() throws Exception {
        Jwt jwt = decode(claimsWithoutTenant());
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of(), "s"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> chainCalled = new AtomicReference<>(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter.doFilter(new MockHttpServletRequest(), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chainCalled.get()).isFalse();
    }

    @Test
    void passesThroughAnonymousRequests() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> chainCalled = new AtomicReference<>(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter.doFilter(new MockHttpServletRequest(), response, chain);

        assertThat(chainCalled.get()).isTrue();
        assertThat(TenantContext.current()).isEmpty();
    }

    private void authenticate(String tenant) {
        Jwt jwt = decode(JwtClaimsSet.builder()
                .subject("alice")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofMinutes(5)))
                .claim("tenant_id", tenant)
                .build());
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of(), "alice"));
    }

    private JwtClaimsSet claimsWithoutTenant() {
        return JwtClaimsSet.builder()
                .subject("alice")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofMinutes(5)))
                .build();
    }

    private Jwt decode(JwtClaimsSet claims) {
        String token = encoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return decoder.decode(token);
    }
}
