package com.emf.controlplane.tenant;

import com.emf.controlplane.entity.TenantAware;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * JPA entity listener that prevents cross-tenant writes.
 * Validates that the entity's tenant_id matches the current tenant context
 * before any persist or update operation.
 *
 * <p>This is a write-side counterpart to the Hibernate read filter.
 * The read filter prevents querying other tenants' data; this guard
 * prevents accidentally writing data into another tenant's partition.
 *
 * <p>Works with any entity that implements {@link TenantAware}, which
 * includes both {@code TenantScopedEntity} subclasses and standalone
 * entities like {@code SetupAuditTrail} and {@code LoginHistory}.
 */
@Component
public class TenantWriteGuard {

    private static final Logger log = LoggerFactory.getLogger(TenantWriteGuard.class);

    @PrePersist
    @PreUpdate
    public void validateTenantOnWrite(Object entity) {
        if (entity instanceof TenantAware tenantAware) {
            String currentTenantId = TenantContextHolder.getTenantId();
            if (currentTenantId != null && tenantAware.getTenantId() != null
                    && !currentTenantId.equals(tenantAware.getTenantId())) {
                log.error("Cross-tenant write attempt: entity tenant={}, context tenant={}",
                        tenantAware.getTenantId(), currentTenantId);
                throw new SecurityException(
                        "Attempted to write entity to tenant " + tenantAware.getTenantId()
                        + " from tenant context " + currentTenantId);
            }
        }
    }
}
