package com.emf.controlplane.dto;

import com.emf.controlplane.entity.OrgWideDefault;

public class OrgWideDefaultDto {

    private String id;
    private String collectionId;
    private String internalAccess;
    private String externalAccess;

    public OrgWideDefaultDto() {}

    public OrgWideDefaultDto(String id, String collectionId, String internalAccess, String externalAccess) {
        this.id = id;
        this.collectionId = collectionId;
        this.internalAccess = internalAccess;
        this.externalAccess = externalAccess;
    }

    public static OrgWideDefaultDto fromEntity(OrgWideDefault entity) {
        return new OrgWideDefaultDto(
                entity.getId(),
                entity.getCollectionId(),
                entity.getInternalAccess(),
                entity.getExternalAccess()
        );
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }

    public String getInternalAccess() { return internalAccess; }
    public void setInternalAccess(String internalAccess) { this.internalAccess = internalAccess; }

    public String getExternalAccess() { return externalAccess; }
    public void setExternalAccess(String externalAccess) { this.externalAccess = externalAccess; }
}
