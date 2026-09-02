package com.lms.certificate.tenancy;

import com.lms.certificate.config.TenantRuntimeConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/** Resolves a certificate-service request to one active tenant before JPA is used. */
@Component
public class TenantContextFilter extends OncePerRequestFilter {
    private static final String PUBLIC_TENANT_PREFIX = "/api/v1/public/tenants/";

    private final TenantRuntimeConfig config;
    private final TenantRegistry tenantRegistry;
    private final TenantSecretCipher cipher;

    public TenantContextFilter(TenantRuntimeConfig config, TenantRegistry tenantRegistry, TenantSecretCipher cipher) {
        this.config = config;
        this.tenantRegistry = tenantRegistry;
        this.cipher = cipher;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/")
                || request.getRequestURI().startsWith("/api/v1/.well-known/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!config.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String slug = resolveSlug(request);
        if (slug == null) {
            if (config.isTenantResolutionRequired()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "A tenant must be selected");
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        TenantRegistry.EncryptedTenantConnection tenant = tenantRegistry.findActiveBySlug(slug).orElse(null);
        if (tenant == null || tenant.jdbcUrl() == null || tenant.username() == null || tenant.encryptedPassword() == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Active tenant not found");
            return;
        }

        TenantContext.set(new TenantConnection(tenant.tenantId(), tenant.slug(), tenant.jdbcUrl(), tenant.username(),
                cipher.decrypt(tenant.encryptedPassword())));
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String resolveSlug(HttpServletRequest request) {
        String header = request.getHeader(config.getTenantHeader());
        if (header != null && !header.isBlank()) return normalise(header);

        String path = request.getRequestURI();
        if (path.startsWith(PUBLIC_TENANT_PREFIX)) {
            String rest = path.substring(PUBLIC_TENANT_PREFIX.length());
            int separator = rest.indexOf('/');
            return separator > 0 ? normalise(rest.substring(0, separator)) : null;
        }
        return null;
    }

    private String normalise(String value) {
        String slug = value.trim().toLowerCase(Locale.ROOT);
        return slug.matches("^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$") ? slug : null;
    }
}
