package com.emf.controlplane.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor that activates the Hibernate tenant filter for every request
 * that has a tenant context. This ensures all JPA queries on tenant-scoped
 * entities are automatically filtered to the current tenant's data.
 *
 * <p>The filter is enabled in preHandle and disabled in afterCompletion
 * to ensure proper cleanup regardless of exceptions.
 */
@Component
public class TenantFilterInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantFilterInterceptor.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            try {
                Session session = entityManager.unwrap(Session.class);
                session.enableFilter("tenantFilter")
                       .setParameter("tenantId", tenantId);
                log.trace("Enabled tenant filter for tenant: {}", tenantId);
            } catch (Exception e) {
                log.warn("Failed to enable tenant filter: {}", e.getMessage());
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            try {
                Session session = entityManager.unwrap(Session.class);
                session.disableFilter("tenantFilter");
                log.trace("Disabled tenant filter for tenant: {}", tenantId);
            } catch (Exception e) {
                log.trace("Failed to disable tenant filter (session may be closed): {}", e.getMessage());
            }
        }
    }
}
