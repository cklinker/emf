package com.emf.controlplane.dto;

import com.emf.controlplane.entity.ConfigPackage;
import com.emf.controlplane.entity.OidcProvider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Response DTO for Package API responses.
 * Provides a clean API representation of a ConfigPackage entity with its items.
 */
public class PackageDto {

    private String id;
    private String name;
    private String version;
    private String description;
    private Instant createdAt;

    /**
     * Collections included in this package.
     */
    private List<PackageCollectionDto> collections = new ArrayList<>();

    /**
     * Roles included in this package.
     */
    private List<PackageRoleDto> roles = new ArrayList<>();

    /**
     * Policies included in this package.
     */
    private List<PackagePolicyDto> policies = new ArrayList<>();

    /**
     * OIDC providers included in this package.
     */
    private List<PackageOidcProviderDto> oidcProviders = new ArrayList<>();

    /**
     * UI pages included in this package.
     */
    private List<PackageUiPageDto> uiPages = new ArrayList<>();

    /**
     * UI menus included in this package.
     */
    private List<PackageUiMenuDto> uiMenus = new ArrayList<>();

    public PackageDto() {
    }

    public PackageDto(String id, String name, String version, String description, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.description = description;
        this.createdAt = createdAt;
    }

    /**
     * Creates a PackageDto from a ConfigPackage entity.
     * Note: This only copies basic metadata, not the items.
     * Items should be populated separately.
     *
     * @param pkg The package entity to convert
     * @return A new PackageDto with data from the entity
     */
    public static PackageDto fromEntity(ConfigPackage pkg) {
        if (pkg == null) {
            return null;
        }
        return new PackageDto(
                pkg.getId(),
                pkg.getName(),
                pkg.getVersion(),
                pkg.getDescription(),
                pkg.getCreatedAt()
        );
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<PackageCollectionDto> getCollections() {
        return collections;
    }

    public void setCollections(List<PackageCollectionDto> collections) {
        this.collections = collections != null ? collections : new ArrayList<>();
    }

    public List<PackageRoleDto> getRoles() {
        return roles;
    }

    public void setRoles(List<PackageRoleDto> roles) {
        this.roles = roles != null ? roles : new ArrayList<>();
    }

    public List<PackagePolicyDto> getPolicies() {
        return policies;
    }

    public void setPolicies(List<PackagePolicyDto> policies) {
        this.policies = policies != null ? policies : new ArrayList<>();
    }

    public List<PackageOidcProviderDto> getOidcProviders() {
        return oidcProviders;
    }

    public void setOidcProviders(List<PackageOidcProviderDto> oidcProviders) {
        this.oidcProviders = oidcProviders != null ? oidcProviders : new ArrayList<>();
    }

    public List<PackageUiPageDto> getUiPages() {
        return uiPages;
    }

    public void setUiPages(List<PackageUiPageDto> uiPages) {
        this.uiPages = uiPages != null ? uiPages : new ArrayList<>();
    }

    public List<PackageUiMenuDto> getUiMenus() {
        return uiMenus;
    }

    public void setUiMenus(List<PackageUiMenuDto> uiMenus) {
        this.uiMenus = uiMenus != null ? uiMenus : new ArrayList<>();
    }

    /**
     * Returns the total number of items in this package.
     */
    public int getTotalItemCount() {
        return collections.size() + roles.size() + policies.size() +
                oidcProviders.size() + uiPages.size() + uiMenus.size();
    }

    @Override
    public String toString() {
        return "PackageDto{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", totalItems=" + getTotalItemCount() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PackageDto that = (PackageDto) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, version);
    }

    // Nested DTOs for package items

    /**
     * Collection data for package export/import.
     */
    public static class PackageCollectionDto {
        private String id;
        private String name;
        private String description;
        private Integer currentVersion;
        private List<PackageFieldDto> fields = new ArrayList<>();

        public PackageCollectionDto() {
        }

        public PackageCollectionDto(String id, String name, String description, Integer currentVersion) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.currentVersion = currentVersion;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Integer getCurrentVersion() {
            return currentVersion;
        }

        public void setCurrentVersion(Integer currentVersion) {
            this.currentVersion = currentVersion;
        }

        public List<PackageFieldDto> getFields() {
            return fields;
        }

        public void setFields(List<PackageFieldDto> fields) {
            this.fields = fields != null ? fields : new ArrayList<>();
        }
    }

    /**
     * Field data for package export/import.
     */
    public static class PackageFieldDto {
        private String id;
        private String name;
        private String type;
        private boolean required;
        private String description;
        private String constraints;

        public PackageFieldDto() {
        }

        public PackageFieldDto(String id, String name, String type, boolean required, String description, String constraints) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.required = required;
            this.description = description;
            this.constraints = constraints;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getConstraints() {
            return constraints;
        }

        public void setConstraints(String constraints) {
            this.constraints = constraints;
        }
    }

    /**
     * Role data for package export/import.
     */
    public static class PackageRoleDto {
        private String id;
        private String name;
        private String description;

        public PackageRoleDto() {
        }

        public PackageRoleDto(String id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    /**
     * Policy data for package export/import.
     */
    public static class PackagePolicyDto {
        private String id;
        private String name;
        private String description;
        private String expression;
        private String rules;

        public PackagePolicyDto() {
        }

        public PackagePolicyDto(String id, String name, String description, String expression, String rules) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.expression = expression;
            this.rules = rules;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getExpression() {
            return expression;
        }

        public void setExpression(String expression) {
            this.expression = expression;
        }

        public String getRules() {
            return rules;
        }

        public void setRules(String rules) {
            this.rules = rules;
        }
    }

    /**
     * OIDC provider data for package export/import.
     */
    public static class PackageOidcProviderDto {
        private String id;
        private String name;
        private String issuer;
        private String jwksUri;
        private String clientId;
        private String audience;
        private String rolesClaim;
        private String rolesMapping;
        private String emailClaim;
        private String usernameClaim;
        private String nameClaim;

        public PackageOidcProviderDto() {
        }

        public PackageOidcProviderDto(String id, String name, String issuer, String jwksUri, String clientId, String audience) {
            this.id = id;
            this.name = name;
            this.issuer = issuer;
            this.jwksUri = jwksUri;
            this.clientId = clientId;
            this.audience = audience;
        }

        /**
         * Creates a PackageOidcProviderDto from an OidcProvider entity.
         * Includes all claim mapping fields for export/import functionality.
         *
         * @param provider The OidcProvider entity to convert
         * @return A new PackageOidcProviderDto with data from the entity
         */
        public static PackageOidcProviderDto fromEntity(OidcProvider provider) {
            if (provider == null) {
                return null;
            }
            PackageOidcProviderDto dto = new PackageOidcProviderDto(
                    provider.getId(),
                    provider.getName(),
                    provider.getIssuer(),
                    provider.getJwksUri(),
                    provider.getClientId(),
                    provider.getAudience()
            );
            dto.setRolesClaim(provider.getRolesClaim());
            dto.setRolesMapping(provider.getRolesMapping());
            dto.setEmailClaim(provider.getEmailClaim());
            dto.setUsernameClaim(provider.getUsernameClaim());
            dto.setNameClaim(provider.getNameClaim());
            return dto;
        }

        /**
         * Converts this DTO to an OidcProvider entity.
         * Sets all claim mapping fields on the entity.
         *
         * @return A new OidcProvider entity with data from this DTO
         */
        public OidcProvider toEntity() {
            OidcProvider provider = new OidcProvider(this.name, this.issuer, this.jwksUri);
            provider.setClientId(this.clientId);
            provider.setAudience(this.audience);
            provider.setRolesClaim(this.rolesClaim);
            provider.setRolesMapping(this.rolesMapping);
            provider.setEmailClaim(this.emailClaim);
            provider.setUsernameClaim(this.usernameClaim);
            provider.setNameClaim(this.nameClaim);
            provider.setActive(true);
            return provider;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getJwksUri() {
            return jwksUri;
        }

        public void setJwksUri(String jwksUri) {
            this.jwksUri = jwksUri;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public String getRolesClaim() {
            return rolesClaim;
        }

        public void setRolesClaim(String rolesClaim) {
            this.rolesClaim = rolesClaim;
        }

        public String getRolesMapping() {
            return rolesMapping;
        }

        public void setRolesMapping(String rolesMapping) {
            this.rolesMapping = rolesMapping;
        }

        public String getEmailClaim() {
            return emailClaim;
        }

        public void setEmailClaim(String emailClaim) {
            this.emailClaim = emailClaim;
        }

        public String getUsernameClaim() {
            return usernameClaim;
        }

        public void setUsernameClaim(String usernameClaim) {
            this.usernameClaim = usernameClaim;
        }

        public String getNameClaim() {
            return nameClaim;
        }

        public void setNameClaim(String nameClaim) {
            this.nameClaim = nameClaim;
        }
    }

    /**
     * UI page data for package export/import.
     */
    public static class PackageUiPageDto {
        private String id;
        private String name;
        private String path;
        private String title;
        private String config;

        public PackageUiPageDto() {
        }

        public PackageUiPageDto(String id, String name, String path, String title, String config) {
            this.id = id;
            this.name = name;
            this.path = path;
            this.title = title;
            this.config = config;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getConfig() {
            return config;
        }

        public void setConfig(String config) {
            this.config = config;
        }
    }

    /**
     * UI menu data for package export/import.
     */
    public static class PackageUiMenuDto {
        private String id;
        private String name;
        private String description;
        private List<PackageUiMenuItemDto> items = new ArrayList<>();

        public PackageUiMenuDto() {
        }

        public PackageUiMenuDto(String id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<PackageUiMenuItemDto> getItems() {
            return items;
        }

        public void setItems(List<PackageUiMenuItemDto> items) {
            this.items = items != null ? items : new ArrayList<>();
        }
    }

    /**
     * UI menu item data for package export/import.
     */
    public static class PackageUiMenuItemDto {
        private String id;
        private String label;
        private String path;
        private String icon;
        private Integer displayOrder;

        public PackageUiMenuItemDto() {
        }

        public PackageUiMenuItemDto(String id, String label, String path, String icon, Integer displayOrder) {
            this.id = id;
            this.label = label;
            this.path = path;
            this.icon = icon;
            this.displayOrder = displayOrder;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public Integer getDisplayOrder() {
            return displayOrder;
        }

        public void setDisplayOrder(Integer displayOrder) {
            this.displayOrder = displayOrder;
        }
    }
}
