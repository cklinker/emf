package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Defines the baseline record access level per collection.
 * If no OWD record exists, default behavior is PUBLIC_READ_WRITE.
 */
@Entity
@Table(name = "org_wide_default")
@EntityListeners(AuditingEntityListener.class)
public class OrgWideDefault extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "collection_id", nullable = false, length = 36)
    private String collectionId;

    @Column(name = "internal_access", nullable = false, length = 20)
    private String internalAccess = "PUBLIC_READ_WRITE";

    @Column(name = "external_access", length = 20)
    private String externalAccess = "PRIVATE";

    public OrgWideDefault() {
        super();
    }

    public OrgWideDefault(String tenantId, String collectionId, String internalAccess) {
        super();
        this.tenantId = tenantId;
        this.collectionId = collectionId;
        this.internalAccess = internalAccess;
    }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }

    public String getInternalAccess() { return internalAccess; }
    public void setInternalAccess(String internalAccess) { this.internalAccess = internalAccess; }

    public String getExternalAccess() { return externalAccess; }
    public void setExternalAccess(String externalAccess) { this.externalAccess = externalAccess; }
}
