package com.emf.controlplane.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for importing a configuration package.
 * Contains the package content to be imported.
 */
public class ImportPackageRequest {

    /**
     * The package data to import.
     */
    @NotNull(message = "Package data is required")
    private PackageDto packageData;

    /**
     * Strategy for handling conflicts during import.
     * SKIP: Skip items that already exist
     * OVERWRITE: Overwrite existing items with imported data
     * FAIL: Fail the import if any conflicts are found
     */
    private ConflictStrategy conflictStrategy = ConflictStrategy.SKIP;

    public ImportPackageRequest() {
    }

    public ImportPackageRequest(PackageDto packageData) {
        this.packageData = packageData;
    }

    public PackageDto getPackageData() {
        return packageData;
    }

    public void setPackageData(PackageDto packageData) {
        this.packageData = packageData;
    }

    public ConflictStrategy getConflictStrategy() {
        return conflictStrategy;
    }

    public void setConflictStrategy(ConflictStrategy conflictStrategy) {
        this.conflictStrategy = conflictStrategy;
    }

    @Override
    public String toString() {
        return "ImportPackageRequest{" +
                "packageData=" + (packageData != null ? packageData.getName() : "null") +
                ", conflictStrategy=" + conflictStrategy +
                '}';
    }

    /**
     * Strategy for handling conflicts during package import.
     */
    public enum ConflictStrategy {
        /**
         * Skip items that already exist in the target environment.
         */
        SKIP,

        /**
         * Overwrite existing items with the imported data.
         */
        OVERWRITE,

        /**
         * Fail the import if any conflicts are detected.
         */
        FAIL
    }
}
