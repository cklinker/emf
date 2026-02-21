package com.emf.controlplane.dto;

import com.emf.controlplane.entity.LayoutField;
import com.emf.controlplane.entity.LayoutRelatedList;
import com.emf.controlplane.entity.LayoutSection;
import com.emf.controlplane.entity.PageLayout;

import java.time.Instant;
import java.util.List;

public class PageLayoutDto {

    private String id;
    private String collectionId;
    private String name;
    private String description;
    private String layoutType;
    private boolean isDefault;
    private List<SectionDto> sections;
    private List<RelatedListDto> relatedLists;
    private Instant createdAt;
    private Instant updatedAt;

    public static PageLayoutDto fromEntity(PageLayout entity) {
        PageLayoutDto dto = new PageLayoutDto();
        dto.setId(entity.getId());
        dto.setCollectionId(entity.getCollection().getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setLayoutType(entity.getLayoutType());
        dto.setDefault(entity.isDefault());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setSections(entity.getSections().stream().map(SectionDto::fromEntity).toList());
        dto.setRelatedLists(entity.getRelatedLists().stream().map(RelatedListDto::fromEntity).toList());
        return dto;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLayoutType() { return layoutType; }
    public void setLayoutType(String layoutType) { this.layoutType = layoutType; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
    public List<SectionDto> getSections() { return sections; }
    public void setSections(List<SectionDto> sections) { this.sections = sections; }
    public List<RelatedListDto> getRelatedLists() { return relatedLists; }
    public void setRelatedLists(List<RelatedListDto> relatedLists) { this.relatedLists = relatedLists; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static class SectionDto {
        private String id;
        private String heading;
        private int columns;
        private int sortOrder;
        private boolean collapsed;
        private String style;
        private String sectionType;
        private String tabGroup;
        private String tabLabel;
        private String visibilityRule;
        private List<FieldPlacementDto> fields;

        public static SectionDto fromEntity(LayoutSection entity) {
            SectionDto dto = new SectionDto();
            dto.setId(entity.getId());
            dto.setHeading(entity.getHeading());
            dto.setColumns(entity.getColumns());
            dto.setSortOrder(entity.getSortOrder());
            dto.setCollapsed(entity.isCollapsed());
            dto.setStyle(entity.getStyle());
            dto.setSectionType(entity.getSectionType());
            dto.setTabGroup(entity.getTabGroup());
            dto.setTabLabel(entity.getTabLabel());
            dto.setVisibilityRule(entity.getVisibilityRule());
            dto.setFields(entity.getFields().stream().map(FieldPlacementDto::fromEntity).toList());
            return dto;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getHeading() { return heading; }
        public void setHeading(String heading) { this.heading = heading; }
        public int getColumns() { return columns; }
        public void setColumns(int columns) { this.columns = columns; }
        public int getSortOrder() { return sortOrder; }
        public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
        public boolean isCollapsed() { return collapsed; }
        public void setCollapsed(boolean collapsed) { this.collapsed = collapsed; }
        public String getStyle() { return style; }
        public void setStyle(String style) { this.style = style; }
        public String getSectionType() { return sectionType; }
        public void setSectionType(String sectionType) { this.sectionType = sectionType; }
        public String getTabGroup() { return tabGroup; }
        public void setTabGroup(String tabGroup) { this.tabGroup = tabGroup; }
        public String getTabLabel() { return tabLabel; }
        public void setTabLabel(String tabLabel) { this.tabLabel = tabLabel; }
        public String getVisibilityRule() { return visibilityRule; }
        public void setVisibilityRule(String visibilityRule) { this.visibilityRule = visibilityRule; }
        public List<FieldPlacementDto> getFields() { return fields; }
        public void setFields(List<FieldPlacementDto> fields) { this.fields = fields; }
    }

    public static class FieldPlacementDto {
        private String id;
        private String fieldId;
        private String fieldName;
        private String fieldType;
        private String fieldDisplayName;
        private int columnNumber;
        private int columnSpan = 1;
        private int sortOrder;
        private boolean requiredOnLayout;
        private boolean readOnlyOnLayout;
        private String labelOverride;
        private String helpTextOverride;
        private String visibilityRule;

        public static FieldPlacementDto fromEntity(LayoutField entity) {
            FieldPlacementDto dto = new FieldPlacementDto();
            dto.setId(entity.getId());
            dto.setFieldId(entity.getField().getId());
            dto.setFieldName(entity.getField().getName());
            dto.setFieldType(entity.getField().getType());
            dto.setFieldDisplayName(entity.getField().getDisplayName());
            dto.setColumnNumber(entity.getColumnNumber());
            dto.setColumnSpan(entity.getColumnSpan());
            dto.setSortOrder(entity.getSortOrder());
            dto.setRequiredOnLayout(entity.isRequiredOnLayout());
            dto.setReadOnlyOnLayout(entity.isReadOnlyOnLayout());
            dto.setLabelOverride(entity.getLabelOverride());
            dto.setHelpTextOverride(entity.getHelpTextOverride());
            dto.setVisibilityRule(entity.getVisibilityRule());
            return dto;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getFieldId() { return fieldId; }
        public void setFieldId(String fieldId) { this.fieldId = fieldId; }
        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }
        public String getFieldType() { return fieldType; }
        public void setFieldType(String fieldType) { this.fieldType = fieldType; }
        public String getFieldDisplayName() { return fieldDisplayName; }
        public void setFieldDisplayName(String fieldDisplayName) { this.fieldDisplayName = fieldDisplayName; }
        public int getColumnNumber() { return columnNumber; }
        public void setColumnNumber(int columnNumber) { this.columnNumber = columnNumber; }
        public int getColumnSpan() { return columnSpan; }
        public void setColumnSpan(int columnSpan) { this.columnSpan = columnSpan; }
        public int getSortOrder() { return sortOrder; }
        public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
        public boolean isRequiredOnLayout() { return requiredOnLayout; }
        public void setRequiredOnLayout(boolean requiredOnLayout) { this.requiredOnLayout = requiredOnLayout; }
        public boolean isReadOnlyOnLayout() { return readOnlyOnLayout; }
        public void setReadOnlyOnLayout(boolean readOnlyOnLayout) { this.readOnlyOnLayout = readOnlyOnLayout; }
        public String getLabelOverride() { return labelOverride; }
        public void setLabelOverride(String labelOverride) { this.labelOverride = labelOverride; }
        public String getHelpTextOverride() { return helpTextOverride; }
        public void setHelpTextOverride(String helpTextOverride) { this.helpTextOverride = helpTextOverride; }
        public String getVisibilityRule() { return visibilityRule; }
        public void setVisibilityRule(String visibilityRule) { this.visibilityRule = visibilityRule; }
    }

    public static class RelatedListDto {
        private String id;
        private String relatedCollectionId;
        private String relatedCollectionName;
        private String relationshipFieldId;
        private String relationshipFieldName;
        private String displayColumns;
        private String sortField;
        private String sortDirection;
        private int rowLimit;
        private int sortOrder;

        public static RelatedListDto fromEntity(LayoutRelatedList entity) {
            RelatedListDto dto = new RelatedListDto();
            dto.setId(entity.getId());
            dto.setRelatedCollectionId(entity.getRelatedCollection().getId());
            dto.setRelatedCollectionName(entity.getRelatedCollection().getName());
            dto.setRelationshipFieldId(entity.getRelationshipField().getId());
            dto.setRelationshipFieldName(entity.getRelationshipField().getName());
            dto.setDisplayColumns(entity.getDisplayColumns());
            dto.setSortField(entity.getSortField());
            dto.setSortDirection(entity.getSortDirection());
            dto.setRowLimit(entity.getRowLimit());
            dto.setSortOrder(entity.getSortOrder());
            return dto;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getRelatedCollectionId() { return relatedCollectionId; }
        public void setRelatedCollectionId(String relatedCollectionId) { this.relatedCollectionId = relatedCollectionId; }
        public String getRelatedCollectionName() { return relatedCollectionName; }
        public void setRelatedCollectionName(String relatedCollectionName) { this.relatedCollectionName = relatedCollectionName; }
        public String getRelationshipFieldId() { return relationshipFieldId; }
        public void setRelationshipFieldId(String relationshipFieldId) { this.relationshipFieldId = relationshipFieldId; }
        public String getRelationshipFieldName() { return relationshipFieldName; }
        public void setRelationshipFieldName(String relationshipFieldName) { this.relationshipFieldName = relationshipFieldName; }
        public String getDisplayColumns() { return displayColumns; }
        public void setDisplayColumns(String displayColumns) { this.displayColumns = displayColumns; }
        public String getSortField() { return sortField; }
        public void setSortField(String sortField) { this.sortField = sortField; }
        public String getSortDirection() { return sortDirection; }
        public void setSortDirection(String sortDirection) { this.sortDirection = sortDirection; }
        public int getRowLimit() { return rowLimit; }
        public void setRowLimit(int rowLimit) { this.rowLimit = rowLimit; }
        public int getSortOrder() { return sortOrder; }
        public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    }
}
