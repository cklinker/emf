package com.emf.controlplane.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * Base class for all tenant-scoped entities.
 * Provides automatic Hibernate tenant filtering when the TenantFilterInterceptor
 * activates the "tenantFilter" on each request.
 *
 * <p>Entities extending this class will have their queries automatically filtered
 * to the current tenant's data. Write operations are protected by {@link TenantWriteGuard}.
 *
 * <p>Usage: New tenant-scoped entities should extend this class instead of BaseEntity.
 * Existing entities can be migrated incrementally.
 */
@MappedSuperclass
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public abstract class TenantScopedEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    protected TenantScopedEntity() {
        super();
    }

    protected TenantScopedEntity(String tenantId) {
        super();
        this.tenantId = tenantId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
