package com.emf.controlplane.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Helper for operations that legitimately need to query across tenants.
 * Temporarily disables the Hibernate tenant filter, executes the callback,
 * then re-enables the filter.
 *
 * <p>Use cases:
 * <ul>
 *   <li>TenantController — PLATFORM_ADMIN managing tenants</li>
 *   <li>GatewayBootstrapController — bootstrap/slug-map endpoints</li>
 *   <li>InternalOidcController — internal OIDC provider lookup</li>
 *   <li>TenantSchemaManager — provisioning</li>
 * </ul>
 */
@Service
public class CrossTenantOperationHelper {

    private static final Logger log = LoggerFactory.getLogger(CrossTenantOperationHelper.class);

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Execute a callback with the tenant filter temporarily disabled.
     * The filter is re-enabled after the callback completes (or throws).
     *
     * @param callback the operation to execute without tenant filtering
     * @param <T> return type
     * @return the callback's return value
     */
    public <T> T withoutTenantFilter(Supplier<T> callback) {
        Session session = entityManager.unwrap(Session.class);
        try {
            session.disableFilter("tenantFilter");
            log.trace("Temporarily disabled tenant filter for cross-tenant operation");
            return callback.get();
        } finally {
            String tenantId = TenantContextHolder.getTenantId();
            if (tenantId != null) {
                session.enableFilter("tenantFilter")
                       .setParameter("tenantId", tenantId);
                log.trace("Re-enabled tenant filter for tenant: {}", tenantId);
            }
        }
    }

    /**
     * Execute a void callback with the tenant filter temporarily disabled.
     *
     * @param callback the operation to execute without tenant filtering
     */
    public void withoutTenantFilter(Runnable callback) {
        withoutTenantFilter(() -> {
            callback.run();
            return null;
        });
    }
}
