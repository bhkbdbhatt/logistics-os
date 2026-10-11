package io.logisticsos.security;

import io.logisticsos.tenant.TenantContext;
import io.logisticsos.tenant.TenantId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds {@link TenantContext} from the authenticated token's tenant claim for the request, and clears
 * it afterwards. Requests carrying a JWT without a tenant claim are rejected (ADR-0020). Anonymous
 * requests (health, dev login) pass through unbound.
 */
public class TenantContextFilter extends OncePerRequestFilter {

    private final LogisticsSecurityProperties properties;

    public TenantContextFilter(LogisticsSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            chain.doFilter(request, response);
            return;
        }

        Optional<String> tenant = JwtClaimReader.readString(jwtAuthentication.getToken(), properties.getTenantClaim());
        if (tenant.isEmpty()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "token missing required tenant claim '" + properties.getTenantClaim() + "'");
            return;
        }

        TenantId tenantId = TenantId.from(tenant.get());
        try (TenantContext.TenantScope ignored = TenantContext.scope(tenantId)) {
            chain.doFilter(new TenantHeaderRequestWrapper(request, tenantId.value()), response);
        } finally {
            TenantContext.clear();
        }
    }
}
