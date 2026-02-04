package com.emf.controlplane.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Response DTO for package import preview (dry-run mode).
 * Shows what changes would be made without actually applying them.
 */
public class ImportPreviewDto {

    /**
     * Package metadata.
     */
    private String packageName;
    private String packageVersion;

    /**
     * Whether the package is valid for import.
     */
    private boolean valid;

    /**
     * Validation errors if the package is invalid.
     */
    private List<String> validationErrors = new ArrayList<>();

    /**
     * Items that would be created (don't exist in target).
     */
    private List<ImportItemDto> itemsToCreate = new ArrayList<>();

    /**
     * Items that would be updated (exist in target with different data).
     */
    private List<ImportItemDto> itemsToUpdate = new ArrayList<>();

    /**
     * Items that would be skipped (already exist with same data or conflict strategy is SKIP).
     */
    private List<ImportItemDto> itemsToSkip = new ArrayList<>();

    /**
     * Items that have conflicts (exist in target, conflict strategy determines action).
     */
    private List<ImportItemDto> conflicts = new ArrayList<>();

    /**
     * Summary counts.
     */
    private int totalItems;
    private int createCount;
    private int updateCount;
    private int skipCount;
    private int conflictCount;

    public ImportPreviewDto() {
    }

    public ImportPreviewDto(String packageName, String packageVersion) {
        this.packageName = packageName;
        this.packageVersion = packageVersion;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getPackageVersion() {
        return packageVersion;
    }

    public void setPackageVersion(String packageVersion) {
        this.packageVersion = packageVersion;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(List<String> validationErrors) {
        this.validationErrors = validationErrors != null ? validationErrors : new ArrayList<>();
    }

    public void addValidationError(String error) {
        this.validationErrors.add(error);
    }

    public List<ImportItemDto> getItemsToCreate() {
        return itemsToCreate;
    }

    public void setItemsToCreate(List<ImportItemDto> itemsToCreate) {
        this.itemsToCreate = itemsToCreate != null ? itemsToCreate : new ArrayList<>();
    }

    public void addItemToCreate(ImportItemDto item) {
        this.itemsToCreate.add(item);
    }

    public List<ImportItemDto> getItemsToUpdate() {
        return itemsToUpdate;
    }

    public void setItemsToUpdate(List<ImportItemDto> itemsToUpdate) {
        this.itemsToUpdate = itemsToUpdate != null ? itemsToUpdate : new ArrayList<>();
    }

    public void addItemToUpdate(ImportItemDto item) {
        this.itemsToUpdate.add(item);
    }

    public List<ImportItemDto> getItemsToSkip() {
        return itemsToSkip;
    }

    public void setItemsToSkip(List<ImportItemDto> itemsToSkip) {
        this.itemsToSkip = itemsToSkip != null ? itemsToSkip : new ArrayList<>();
    }

    public void addItemToSkip(ImportItemDto item) {
        this.itemsToSkip.add(item);
    }

    public List<ImportItemDto> getConflicts() {
        return conflicts;
    }

    public void setConflicts(List<ImportItemDto> conflicts) {
        this.conflicts = conflicts != null ? conflicts : new ArrayList<>();
    }

    public void addConflict(ImportItemDto item) {
        this.conflicts.add(item);
    }

    public int getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
    }

    public int getCreateCount() {
        return createCount;
    }

    public void setCreateCount(int createCount) {
        this.createCount = createCount;
    }

    public int getUpdateCount() {
        return updateCount;
    }

    public void setUpdateCount(int updateCount) {
        this.updateCount = updateCount;
    }

    public int getSkipCount() {
        return skipCount;
    }

    public void setSkipCount(int skipCount) {
        this.skipCount = skipCount;
    }

    public int getConflictCount() {
        return conflictCount;
    }

    public void setConflictCount(int conflictCount) {
        this.conflictCount = conflictCount;
    }

    /**
     * Updates the summary counts based on the item lists.
     */
    public void updateCounts() {
        this.createCount = itemsToCreate.size();
        this.updateCount = itemsToUpdate.size();
        this.skipCount = itemsToSkip.size();
        this.conflictCount = conflicts.size();
        this.totalItems = createCount + updateCount + skipCount + conflictCount;
    }

    @Override
    public String toString() {
        return "ImportPreviewDto{" +
                "packageName='" + packageName + '\'' +
                ", packageVersion='" + packageVersion + '\'' +
                ", valid=" + valid +
                ", totalItems=" + totalItems +
                ", createCount=" + createCount +
                ", updateCount=" + updateCount +
                ", skipCount=" + skipCount +
                ", conflictCount=" + conflictCount +
                '}';
    }

    /**
     * Represents a single item in the import preview.
     */
    public static class ImportItemDto {
        private String itemType;
        private String itemId;
        private String itemName;
        private ImportAction action;
        private String reason;

        public ImportItemDto() {
        }

        public ImportItemDto(String itemType, String itemId, String itemName, ImportAction action) {
            this.itemType = itemType;
            this.itemId = itemId;
            this.itemName = itemName;
            this.action = action;
        }

        public ImportItemDto(String itemType, String itemId, String itemName, ImportAction action, String reason) {
            this(itemType, itemId, itemName, action);
            this.reason = reason;
        }

        public String getItemType() {
            return itemType;
        }

        public void setItemType(String itemType) {
            this.itemType = itemType;
        }

        public String getItemId() {
            return itemId;
        }

        public void setItemId(String itemId) {
            this.itemId = itemId;
        }

        public String getItemName() {
            return itemName;
        }

        public void setItemName(String itemName) {
            this.itemName = itemName;
        }

        public ImportAction getAction() {
            return action;
        }

        public void setAction(ImportAction action) {
            this.action = action;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ImportItemDto that = (ImportItemDto) o;
            return Objects.equals(itemType, that.itemType) &&
                    Objects.equals(itemId, that.itemId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(itemType, itemId);
        }

        @Override
        public String toString() {
            return "ImportItemDto{" +
                    "itemType='" + itemType + '\'' +
                    ", itemId='" + itemId + '\'' +
                    ", itemName='" + itemName + '\'' +
                    ", action=" + action +
                    '}';
        }
    }

    /**
     * Action to be taken for an import item.
     */
    public enum ImportAction {
        CREATE,
        UPDATE,
        SKIP,
        CONFLICT
    }
}
