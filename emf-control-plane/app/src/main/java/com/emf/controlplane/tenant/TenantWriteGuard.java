package com.emf.controlplane.tenant;

import com.emf.controlplane.entity.TenantScopedEntity;
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
 */
@Component
public class TenantWriteGuard {

    private static final Logger log = LoggerFactory.getLogger(TenantWriteGuard.class);

    @PrePersist
    @PreUpdate
    public void validateTenantOnWrite(Object entity) {
        if (entity instanceof TenantScopedEntity scoped) {
            String currentTenantId = TenantContextHolder.getTenantId();
            if (currentTenantId != null && scoped.getTenantId() != null
                    && !currentTenantId.equals(scoped.getTenantId())) {
                log.error("Cross-tenant write attempt: entity tenant={}, context tenant={}",
                        scoped.getTenantId(), currentTenantId);
                throw new SecurityException(
                        "Attempted to write entity to tenant " + scoped.getTenantId()
                        + " from tenant context " + currentTenantId);
            }
        }
    }
}
