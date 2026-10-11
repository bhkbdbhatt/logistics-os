package io.logisticsos.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for {@code platform:security} under the {@code logistics.security} prefix (ADR-0020).
 *
 * <p>{@code mode=local} (default) issues and validates HS256 tokens from a JSON user list — for
 * dev/CI/offline. {@code mode=oidc} validates tokens from a real issuer (Keycloak/Entra/Okta).
 */
@ConfigurationProperties(prefix = "logistics.security")
public class LogisticsSecurityProperties {

    public enum Mode {
        /** Validate JWTs against an OIDC issuer (production / SaaS). */
        OIDC,
        /** Issue and validate HS256 JWTs from {@code users.json} (dev/CI/self-hosted fallback). */
        LOCAL
    }

    /** Authentication mode. Defaults to {@link Mode#LOCAL} so dev and CI run without an IdP. */
    private Mode mode = Mode.LOCAL;

    /** OIDC issuer URI (required when {@code mode=oidc}). */
    private String issuerUri;

    /** Expected token audience (optional; validated when set). */
    private String audience;

    /** Token claim carrying the tenant id (dotted path). */
    private String tenantClaim = "tenant_id";

    /** Token claim carrying roles (dotted path). */
    private String rolesClaim = "roles";

    /** Token claim used as the authenticated principal name. */
    private String usernameClaim = "preferred_username";

    private final Local local = new Local();

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public String getIssuerUri() {
        return issuerUri;
    }

    public void setIssuerUri(String issuerUri) {
        this.issuerUri = issuerUri;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getTenantClaim() {
        return tenantClaim;
    }

    public void setTenantClaim(String tenantClaim) {
        this.tenantClaim = tenantClaim;
    }

    public String getRolesClaim() {
        return rolesClaim;
    }

    public void setRolesClaim(String rolesClaim) {
        this.rolesClaim = rolesClaim;
    }

    public String getUsernameClaim() {
        return usernameClaim;
    }

    public void setUsernameClaim(String usernameClaim) {
        this.usernameClaim = usernameClaim;
    }

    public Local getLocal() {
        return local;
    }

    /** Local-JSON mode settings (not for production). */
    public static class Local {

        private static final String DEV_SECRET = "logistics-os-dev-secret-change-me-please-32bytes";

        /** HS256 signing secret. Must be >= 32 bytes. */
        private String secret = DEV_SECRET;

        /** Path to {@code users.json}; blank uses the built-in dev users. */
        private String usersFile;

        /** Lifetime of issued dev tokens. */
        private Duration tokenTtl = Duration.ofHours(1);

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getUsersFile() {
            return usersFile;
        }

        public void setUsersFile(String usersFile) {
            this.usersFile = usersFile;
        }

        public Duration getTokenTtl() {
            return tokenTtl;
        }

        public void setTokenTtl(Duration tokenTtl) {
            this.tokenTtl = tokenTtl;
        }

        public boolean isDefaultSecret() {
            return DEV_SECRET.equals(secret);
        }
    }
}
