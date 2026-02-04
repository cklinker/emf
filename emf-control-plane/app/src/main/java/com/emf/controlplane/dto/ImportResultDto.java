package com.emf.controlplane.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Response DTO for package import results.
 * Contains the outcome of an import operation.
 */
public class ImportResultDto {

    /**
     * Package metadata.
     */
    private String packageId;
    private String packageName;
    private String packageVersion;

    /**
     * Whether the import was successful.
     */
    private boolean success;

    /**
     * Whether this was a dry-run (preview only).
     */
    private boolean dryRun;

    /**
     * Timestamp of the import operation.
     */
    private Instant importedAt;

    /**
     * Error message if the import failed.
     */
    private String errorMessage;

    /**
     * Validation errors if the package was invalid.
     */
    private List<String> validationErrors = new ArrayList<>();

    /**
     * Items that were created.
     */
    private List<ImportedItemDto> createdItems = new ArrayList<>();

    /**
     * Items that were updated.
     */
    private List<ImportedItemDto> updatedItems = new ArrayList<>();

    /**
     * Items that were skipped.
     */
    private List<ImportedItemDto> skippedItems = new ArrayList<>();

    /**
     * Items that failed to import.
     */
    private List<ImportedItemDto> failedItems = new ArrayList<>();

    /**
     * Summary counts.
     */
    private int totalItems;
    private int createdCount;
    private int updatedCount;
    private int skippedCount;
    private int failedCount;

    public ImportResultDto() {
        this.importedAt = Instant.now();
    }

    public ImportResultDto(String packageName, String packageVersion, boolean dryRun) {
        this();
        this.packageName = packageName;
        this.packageVersion = packageVersion;
        this.dryRun = dryRun;
    }

    /**
     * Creates a successful import result.
     */
    public static ImportResultDto success(String packageId, String packageName, String packageVersion, boolean dryRun) {
        ImportResultDto result = new ImportResultDto(packageName, packageVersion, dryRun);
        result.setPackageId(packageId);
        result.setSuccess(true);
        return result;
    }

    /**
     * Creates a failed import result.
     */
    public static ImportResultDto failure(String packageName, String packageVersion, String errorMessage) {
        ImportResultDto result = new ImportResultDto(packageName, packageVersion, false);
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        return result;
    }

    /**
     * Creates a validation failure result.
     */
    public static ImportResultDto validationFailure(String packageName, String packageVersion, List<String> errors) {
        ImportResultDto result = new ImportResultDto(packageName, packageVersion, false);
        result.setSuccess(false);
        result.setValidationErrors(errors);
        result.setErrorMessage("Package validation failed");
        return result;
    }

    public String getPackageId() {
        return packageId;
    }

    public void setPackageId(String packageId) {
        this.packageId = packageId;
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

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(Instant importedAt) {
        this.importedAt = importedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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

    public List<ImportedItemDto> getCreatedItems() {
        return createdItems;
    }

    public void setCreatedItems(List<ImportedItemDto> createdItems) {
        this.createdItems = createdItems != null ? createdItems : new ArrayList<>();
    }

    public void addCreatedItem(ImportedItemDto item) {
        this.createdItems.add(item);
    }

    public List<ImportedItemDto> getUpdatedItems() {
        return updatedItems;
    }

    public void setUpdatedItems(List<ImportedItemDto> updatedItems) {
        this.updatedItems = updatedItems != null ? updatedItems : new ArrayList<>();
    }

    public void addUpdatedItem(ImportedItemDto item) {
        this.updatedItems.add(item);
    }

    public List<ImportedItemDto> getSkippedItems() {
        return skippedItems;
    }

    public void setSkippedItems(List<ImportedItemDto> skippedItems) {
        this.skippedItems = skippedItems != null ? skippedItems : new ArrayList<>();
    }

    public void addSkippedItem(ImportedItemDto item) {
        this.skippedItems.add(item);
    }

    public List<ImportedItemDto> getFailedItems() {
        return failedItems;
    }

    public void setFailedItems(List<ImportedItemDto> failedItems) {
        this.failedItems = failedItems != null ? failedItems : new ArrayList<>();
    }

    public void addFailedItem(ImportedItemDto item) {
        this.failedItems.add(item);
    }

    public int getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
    }

    public int getCreatedCount() {
        return createdCount;
    }

    public void setCreatedCount(int createdCount) {
        this.createdCount = createdCount;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public void setUpdatedCount(int updatedCount) {
        this.updatedCount = updatedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public void setSkippedCount(int skippedCount) {
        this.skippedCount = skippedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    /**
     * Updates the summary counts based on the item lists.
     */
    public void updateCounts() {
        this.createdCount = createdItems.size();
        this.updatedCount = updatedItems.size();
        this.skippedCount = skippedItems.size();
        this.failedCount = failedItems.size();
        this.totalItems = createdCount + updatedCount + skippedCount + failedCount;
    }

    @Override
    public String toString() {
        return "ImportResultDto{" +
                "packageName='" + packageName + '\'' +
                ", packageVersion='" + packageVersion + '\'' +
                ", success=" + success +
                ", dryRun=" + dryRun +
                ", totalItems=" + totalItems +
                ", createdCount=" + createdCount +
                ", updatedCount=" + updatedCount +
                ", skippedCount=" + skippedCount +
                ", failedCount=" + failedCount +
                '}';
    }

    /**
     * Represents a single imported item result.
     */
    public static class ImportedItemDto {
        private String itemType;
        private String itemId;
        private String itemName;
        private String status;
        private String message;

        public ImportedItemDto() {
        }

        public ImportedItemDto(String itemType, String itemId, String itemName, String status) {
            this.itemType = itemType;
            this.itemId = itemId;
            this.itemName = itemName;
            this.status = status;
        }

        public ImportedItemDto(String itemType, String itemId, String itemName, String status, String message) {
            this(itemType, itemId, itemName, status);
            this.message = message;
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

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ImportedItemDto that = (ImportedItemDto) o;
            return Objects.equals(itemType, that.itemType) &&
                    Objects.equals(itemId, that.itemId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(itemType, itemId);
        }

        @Override
        public String toString() {
            return "ImportedItemDto{" +
                    "itemType='" + itemType + '\'' +
                    ", itemId='" + itemId + '\'' +
                    ", itemName='" + itemName + '\'' +
                    ", status='" + status + '\'' +
                    '}';
        }
    }
}
