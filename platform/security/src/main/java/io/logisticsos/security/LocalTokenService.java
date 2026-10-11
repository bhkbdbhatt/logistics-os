package io.logisticsos.security;

import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * Issues HS256 JWTs for {@code mode=local} using the shared {@link JwtEncoder} (ADR-0020). Claims
 * mirror the production shape: {@code tenant_id} and {@code roles} alongside standard timestamps.
 */
public final class LocalTokenService {

    private static final String ISSUER = "logistics-os-local";

    private final JwtEncoder encoder;

    public LocalTokenService(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    public String issue(LocalUser user, Duration ttl) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.username())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("preferred_username", user.username())
                .claim("tenant_id", user.tenant())
                .claim("roles", user.roles())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
