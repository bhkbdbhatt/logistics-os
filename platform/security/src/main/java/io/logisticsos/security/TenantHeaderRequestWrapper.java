package io.logisticsos.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Request wrapper that presents the token-derived tenant as the {@code X-Tenant-Id} header, so
 * services never trust a client-supplied value (ADR-0020).
 */
final class TenantHeaderRequestWrapper extends HttpServletRequestWrapper {

    private static final String TENANT_HEADER = "X-Tenant-Id";

    private final String tenant;

    TenantHeaderRequestWrapper(HttpServletRequest request, String tenant) {
        super(request);
        this.tenant = tenant;
    }

    @Override
    public String getHeader(String name) {
        if (TENANT_HEADER.equalsIgnoreCase(name)) {
            return tenant;
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (TENANT_HEADER.equalsIgnoreCase(name)) {
            return Collections.enumeration(tenant == null ? Collections.emptyList() : Collections.singletonList(tenant));
        }
        return super.getHeaders(name);
    }
}
