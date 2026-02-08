package com.emf.controlplane.tenant;

import com.emf.controlplane.entity.Tenant;
import com.emf.controlplane.repository.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the tenant for every incoming request before business logic runs.
 * Resolution strategy (in order):
 * 1. X-Tenant-ID header (from gateway or service-to-service)
 * 2. X-Tenant-Slug header (lookup by slug)
 *
 * Sets TenantContextHolder for the duration of the request.
 * Exempt paths do not require a tenant context.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantResolutionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantResolutionFilter.class);

    private static final Set<String> EXEMPT_PATH_PREFIXES = Set.of(
            "/actuator",
            "/platform",
            "/control/ui-bootstrap",
            "/control/bootstrap",
            "/openapi",
            "/swagger-ui",
            "/v3/api-docs"
    );

    private final TenantRepository tenantRepository;

    public TenantResolutionFilter(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            String tenantId = resolve(request);
            if (tenantId != null) {
                String tenantSlug = resolveTenantSlug(request, tenantId);
                TenantContextHolder.set(tenantId, tenantSlug);
                log.debug("Resolved tenant: {} ({})", tenantSlug, tenantId);
            } else if (!isExemptPath(request)) {
                log.debug("No tenant context for non-exempt path: {}", request.getRequestURI());
                // For now, allow requests without tenant context during transition
                // In production, this should return 400
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private String resolve(HttpServletRequest request) {
        // 1. X-Tenant-ID header (set by gateway or service-to-service calls)
        String tenantId = request.getHeader("X-Tenant-ID");
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId.trim();
        }

        // 2. X-Tenant-Slug header (lookup by slug)
        String tenantSlug = request.getHeader("X-Tenant-Slug");
        if (tenantSlug != null && !tenantSlug.isBlank()) {
            Optional<Tenant> tenant = tenantRepository.findBySlug(tenantSlug.trim());
            if (tenant.isPresent()) {
                return tenant.get().getId();
            }
            log.warn("Tenant slug not found: {}", tenantSlug);
        }

        return null;
    }

    private String resolveTenantSlug(HttpServletRequest request, String tenantId) {
        // Try header first
        String slug = request.getHeader("X-Tenant-Slug");
        if (slug != null && !slug.isBlank()) {
            return slug.trim();
        }
        // Look up from DB
        return tenantRepository.findById(tenantId)
                .map(Tenant::getSlug)
                .orElse(null);
    }

    private boolean isExemptPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String prefix : EXEMPT_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
