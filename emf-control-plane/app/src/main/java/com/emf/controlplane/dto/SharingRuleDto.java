package com.emf.controlplane.dto;

import com.emf.controlplane.entity.SharingRule;

public class SharingRuleDto {

    private String id;
    private String collectionId;
    private String name;
    private String ruleType;
    private String sharedFrom;
    private String sharedTo;
    private String sharedToType;
    private String accessLevel;
    private String criteria;
    private boolean active;

    public SharingRuleDto() {}

    public static SharingRuleDto fromEntity(SharingRule entity) {
        SharingRuleDto dto = new SharingRuleDto();
        dto.setId(entity.getId());
        dto.setCollectionId(entity.getCollectionId());
        dto.setName(entity.getName());
        dto.setRuleType(entity.getRuleType());
        dto.setSharedFrom(entity.getSharedFrom());
        dto.setSharedTo(entity.getSharedTo());
        dto.setSharedToType(entity.getSharedToType());
        dto.setAccessLevel(entity.getAccessLevel());
        dto.setCriteria(entity.getCriteria());
        dto.setActive(entity.isActive());
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }

    public String getSharedFrom() { return sharedFrom; }
    public void setSharedFrom(String sharedFrom) { this.sharedFrom = sharedFrom; }

    public String getSharedTo() { return sharedTo; }
    public void setSharedTo(String sharedTo) { this.sharedTo = sharedTo; }

    public String getSharedToType() { return sharedToType; }
    public void setSharedToType(String sharedToType) { this.sharedToType = sharedToType; }

    public String getAccessLevel() { return accessLevel; }
    public void setAccessLevel(String accessLevel) { this.accessLevel = accessLevel; }

    public String getCriteria() { return criteria; }
    public void setCriteria(String criteria) { this.criteria = criteria; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
