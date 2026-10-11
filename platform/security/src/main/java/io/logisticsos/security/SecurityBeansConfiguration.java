package io.logisticsos.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Auto-configured auth beans for {@code platform:security} (ADR-0020). Selects the decoder/issuer by
 * {@code logistics.security.mode}: {@code oidc} validates issuer JWTs; {@code local} (default) issues
 * and validates HS256 dev tokens. The web {@code SecurityFilterChain} lives in
 * {@link ResourceServerConfiguration}.
 */
@AutoConfiguration
@EnableConfigurationProperties(LogisticsSecurityProperties.class)
@EnableMethodSecurity
public class SecurityBeansConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityBeansConfiguration.class);

    private static final int MIN_HS256_SECRET_BYTES = 32;

    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    @ConditionalOnProperty(name = "logistics.security.mode", havingValue = "oidc")
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder oidcJwtDecoder(LogisticsSecurityProperties properties) {
        String issuer = properties.getIssuerUri();
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException(
                    "logistics.security.issuer-uri is required when logistics.security.mode=oidc");
        }
        NimbusJwtDecoder decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuer);
        OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(issuer);
        if (properties.getAudience() != null && !properties.getAudience().isBlank()) {
            String audience = properties.getAudience();
            validator = new DelegatingOAuth2TokenValidator<>(validator,
                    new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                            aud -> aud != null && aud.contains(audience)));
        }
        decoder.setJwtValidator(validator);
        log.info("logistics-os security: mode=oidc issuer={}", issuer);
        return decoder;
    }

    @Bean
    @ConditionalOnProperty(name = "logistics.security.mode", havingValue = "local", matchIfMissing = true)
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder localJwtDecoder(LogisticsSecurityProperties properties) {
        if (properties.getLocal().isDefaultSecret()) {
            log.warn("logistics-os security: mode=local using the built-in dev secret — "
                    + "set logistics.security.local.secret (or use mode=oidc) before production");
        }
        return NimbusJwtDecoder.withSecretKey(localSecretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "logistics.security.mode", havingValue = "local", matchIfMissing = true)
    @ConditionalOnMissingBean
    public JwtEncoder jwtEncoder(LogisticsSecurityProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(localSecretKey(properties)));
    }

    @Bean
    @ConditionalOnProperty(name = "logistics.security.mode", havingValue = "local", matchIfMissing = true)
    public LocalUserStore localUserStore(LogisticsSecurityProperties properties, PasswordEncoder encoder) {
        String file = properties.getLocal().getUsersFile();
        List<LocalUser> users = (file == null || file.isBlank())
                ? LocalUserStore.devUsers()
                : LocalUserStore.load(Path.of(file));
        log.info("logistics-os security: local users loaded ({})", users.size());
        return new LocalUserStore(users, encoder);
    }

    @Bean
    @ConditionalOnProperty(name = "logistics.security.mode", havingValue = "local", matchIfMissing = true)
    public LocalTokenService localTokenService(JwtEncoder jwtEncoder) {
        return new LocalTokenService(jwtEncoder);
    }

    @Bean
    @ConditionalOnProperty(name = "logistics.security.mode", havingValue = "local", matchIfMissing = true)
    public AuthController authController(LocalUserStore users, LocalTokenService tokens,
            LogisticsSecurityProperties properties) {
        return new AuthController(users, tokens, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantContextFilter tenantContextFilter(LogisticsSecurityProperties properties) {
        return new TenantContextFilter(properties);
    }

    private static SecretKey localSecretKey(LogisticsSecurityProperties properties) {
        byte[] bytes = properties.getLocal().getSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_HS256_SECRET_BYTES) {
            throw new IllegalStateException("logistics.security.local.secret must be at least "
                    + MIN_HS256_SECRET_BYTES + " bytes for HS256");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
