package com.emf.controlplane.dto;

import com.emf.controlplane.entity.LayoutAssignment;

public class LayoutAssignmentDto {

    private String id;
    private String collectionId;
    private String profileId;
    private String recordTypeId;
    private String layoutId;
    private String layoutName;

    public static LayoutAssignmentDto fromEntity(LayoutAssignment entity) {
        LayoutAssignmentDto dto = new LayoutAssignmentDto();
        dto.setId(entity.getId());
        dto.setCollectionId(entity.getCollection().getId());
        dto.setProfileId(entity.getProfileId());
        dto.setRecordTypeId(entity.getRecordTypeId());
        dto.setLayoutId(entity.getLayout().getId());
        dto.setLayoutName(entity.getLayout().getName());
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }
    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }
    public String getRecordTypeId() { return recordTypeId; }
    public void setRecordTypeId(String recordTypeId) { this.recordTypeId = recordTypeId; }
    public String getLayoutId() { return layoutId; }
    public void setLayoutId(String layoutId) { this.layoutId = layoutId; }
    public String getLayoutName() { return layoutName; }
    public void setLayoutName(String layoutName) { this.layoutName = layoutName; }
}
