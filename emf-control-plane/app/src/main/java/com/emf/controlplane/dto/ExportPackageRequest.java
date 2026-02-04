package com.emf.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * Request DTO for exporting a configuration package.
 * Allows selection of specific configuration items to include in the package.
 */
public class ExportPackageRequest {

    @NotBlank(message = "Package name is required")
    @Size(min = 1, max = 100, message = "Package name must be between 1 and 100 characters")
    private String name;

    @NotBlank(message = "Package version is required")
    @Size(min = 1, max = 50, message = "Package version must be between 1 and 50 characters")
    private String version;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    /**
     * List of collection IDs to include in the export.
     * If empty, no collections will be exported.
     */
    private List<String> collectionIds = new ArrayList<>();

    /**
     * List of role IDs to include in the export.
     * If empty, no roles will be exported.
     */
    private List<String> roleIds = new ArrayList<>();

    /**
     * List of policy IDs to include in the export.
     * If empty, no policies will be exported.
     */
    private List<String> policyIds = new ArrayList<>();

    /**
     * List of OIDC provider IDs to include in the export.
     * If empty, no OIDC providers will be exported.
     */
    private List<String> oidcProviderIds = new ArrayList<>();

    /**
     * List of UI page IDs to include in the export.
     * If empty, no UI pages will be exported.
     */
    private List<String> uiPageIds = new ArrayList<>();

    /**
     * List of UI menu IDs to include in the export.
     * If empty, no UI menus will be exported.
     */
    private List<String> uiMenuIds = new ArrayList<>();

    /**
     * If true, export all items of each type instead of using the ID lists.
     */
    private boolean exportAll = false;

    public ExportPackageRequest() {
    }

    public ExportPackageRequest(String name, String version) {
        this.name = name;
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getCollectionIds() {
        return collectionIds;
    }

    public void setCollectionIds(List<String> collectionIds) {
        this.collectionIds = collectionIds != null ? collectionIds : new ArrayList<>();
    }

    public List<String> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<String> roleIds) {
        this.roleIds = roleIds != null ? roleIds : new ArrayList<>();
    }

    public List<String> getPolicyIds() {
        return policyIds;
    }

    public void setPolicyIds(List<String> policyIds) {
        this.policyIds = policyIds != null ? policyIds : new ArrayList<>();
    }

    public List<String> getOidcProviderIds() {
        return oidcProviderIds;
    }

    public void setOidcProviderIds(List<String> oidcProviderIds) {
        this.oidcProviderIds = oidcProviderIds != null ? oidcProviderIds : new ArrayList<>();
    }

    public List<String> getUiPageIds() {
        return uiPageIds;
    }

    public void setUiPageIds(List<String> uiPageIds) {
        this.uiPageIds = uiPageIds != null ? uiPageIds : new ArrayList<>();
    }

    public List<String> getUiMenuIds() {
        return uiMenuIds;
    }

    public void setUiMenuIds(List<String> uiMenuIds) {
        this.uiMenuIds = uiMenuIds != null ? uiMenuIds : new ArrayList<>();
    }

    public boolean isExportAll() {
        return exportAll;
    }

    public void setExportAll(boolean exportAll) {
        this.exportAll = exportAll;
    }

    @Override
    public String toString() {
        return "ExportPackageRequest{" +
                "name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", exportAll=" + exportAll +
                ", collectionIds=" + collectionIds.size() +
                ", roleIds=" + roleIds.size() +
                ", policyIds=" + policyIds.size() +
                ", oidcProviderIds=" + oidcProviderIds.size() +
                ", uiPageIds=" + uiPageIds.size() +
                ", uiMenuIds=" + uiMenuIds.size() +
                '}';
    }
}
