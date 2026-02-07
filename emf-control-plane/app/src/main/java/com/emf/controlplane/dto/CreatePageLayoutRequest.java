package com.emf.controlplane.dto;

import java.util.List;

public class CreatePageLayoutRequest {

    private String name;
    private String description;
    private String layoutType;
    private boolean isDefault;
    private List<SectionRequest> sections;
    private List<RelatedListRequest> relatedLists;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLayoutType() { return layoutType; }
    public void setLayoutType(String layoutType) { this.layoutType = layoutType; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
    public List<SectionRequest> getSections() { return sections; }
    public void setSections(List<SectionRequest> sections) { this.sections = sections; }
    public List<RelatedListRequest> getRelatedLists() { return relatedLists; }
    public void setRelatedLists(List<RelatedListRequest> relatedLists) { this.relatedLists = relatedLists; }

    public static class SectionRequest {
        private String heading;
        private int columns = 2;
        private int sortOrder;
        private boolean collapsed;
        private String style;
        private List<FieldPlacementRequest> fields;

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
        public List<FieldPlacementRequest> getFields() { return fields; }
        public void setFields(List<FieldPlacementRequest> fields) { this.fields = fields; }
    }

    public static class FieldPlacementRequest {
        private String fieldId;
        private int columnNumber = 1;
        private int sortOrder;
        private boolean requiredOnLayout;
        private boolean readOnlyOnLayout;

        public String getFieldId() { return fieldId; }
        public void setFieldId(String fieldId) { this.fieldId = fieldId; }
        public int getColumnNumber() { return columnNumber; }
        public void setColumnNumber(int columnNumber) { this.columnNumber = columnNumber; }
        public int getSortOrder() { return sortOrder; }
        public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
        public boolean isRequiredOnLayout() { return requiredOnLayout; }
        public void setRequiredOnLayout(boolean requiredOnLayout) { this.requiredOnLayout = requiredOnLayout; }
        public boolean isReadOnlyOnLayout() { return readOnlyOnLayout; }
        public void setReadOnlyOnLayout(boolean readOnlyOnLayout) { this.readOnlyOnLayout = readOnlyOnLayout; }
    }

    public static class RelatedListRequest {
        private String relatedCollectionId;
        private String relationshipFieldId;
        private String displayColumns;
        private String sortField;
        private String sortDirection;
        private int rowLimit = 10;
        private int sortOrder;

        public String getRelatedCollectionId() { return relatedCollectionId; }
        public void setRelatedCollectionId(String relatedCollectionId) { this.relatedCollectionId = relatedCollectionId; }
        public String getRelationshipFieldId() { return relationshipFieldId; }
        public void setRelationshipFieldId(String relationshipFieldId) { this.relationshipFieldId = relationshipFieldId; }
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
