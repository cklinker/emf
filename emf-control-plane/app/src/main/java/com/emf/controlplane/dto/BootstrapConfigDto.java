package com.emf.controlplane.dto;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Response DTO for the UI bootstrap configuration endpoint.
 * Contains all initial configuration needed by the UI on startup.
 */
public class BootstrapConfigDto {

    private List<UiPageDto> pages;
    private List<UiMenuDto> menus;
    private ThemeConfig theme;
    private BrandingConfig branding;
    private FeatureFlags features;
    private List<OidcProviderSummary> oidcProviders;

    public BootstrapConfigDto() {
    }

    public BootstrapConfigDto(List<UiPageDto> pages, List<UiMenuDto> menus,
                              ThemeConfig theme, BrandingConfig branding,
                              FeatureFlags features, List<OidcProviderSummary> oidcProviders) {
        this.pages = pages;
        this.menus = menus;
        this.theme = theme;
        this.branding = branding;
        this.features = features;
        this.oidcProviders = oidcProviders;
    }

    public List<UiPageDto> getPages() {
        return pages;
    }

    public void setPages(List<UiPageDto> pages) {
        this.pages = pages;
    }

    public List<UiMenuDto> getMenus() {
        return menus;
    }

    public void setMenus(List<UiMenuDto> menus) {
        this.menus = menus;
    }

    public ThemeConfig getTheme() {
        return theme;
    }

    public void setTheme(ThemeConfig theme) {
        this.theme = theme;
    }

    public BrandingConfig getBranding() {
        return branding;
    }

    public void setBranding(BrandingConfig branding) {
        this.branding = branding;
    }

    public FeatureFlags getFeatures() {
        return features;
    }

    public void setFeatures(FeatureFlags features) {
        this.features = features;
    }

    public List<OidcProviderSummary> getOidcProviders() {
        return oidcProviders;
    }

    public void setOidcProviders(List<OidcProviderSummary> oidcProviders) {
        this.oidcProviders = oidcProviders;
    }

    @Override
    public String toString() {
        return "BootstrapConfigDto{" +
                "pages=" + pages +
                ", menus=" + menus +
                ", theme=" + theme +
                ", branding=" + branding +
                ", features=" + features +
                ", oidcProviders=" + oidcProviders +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BootstrapConfigDto that = (BootstrapConfigDto) o;
        return Objects.equals(pages, that.pages) &&
                Objects.equals(menus, that.menus) &&
                Objects.equals(theme, that.theme) &&
                Objects.equals(branding, that.branding) &&
                Objects.equals(features, that.features) &&
                Objects.equals(oidcProviders, that.oidcProviders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pages, menus, theme, branding, features, oidcProviders);
    }

    /**
     * Theme configuration
     */
    public static class ThemeConfig {
        private String primaryColor;
        private String secondaryColor;
        private String fontFamily;
        private String borderRadius;

        public ThemeConfig() {
        }

        public ThemeConfig(String primaryColor, String secondaryColor, String fontFamily, String borderRadius) {
            this.primaryColor = primaryColor;
            this.secondaryColor = secondaryColor;
            this.fontFamily = fontFamily;
            this.borderRadius = borderRadius;
        }

        public String getPrimaryColor() { return primaryColor; }
        public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }
        public String getSecondaryColor() { return secondaryColor; }
        public void setSecondaryColor(String secondaryColor) { this.secondaryColor = secondaryColor; }
        public String getFontFamily() { return fontFamily; }
        public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }
        public String getBorderRadius() { return borderRadius; }
        public void setBorderRadius(String borderRadius) { this.borderRadius = borderRadius; }
    }

    /**
     * Branding configuration
     */
    public static class BrandingConfig {
        private String logoUrl;
        private String applicationName;
        private String faviconUrl;

        public BrandingConfig() {
        }

        public BrandingConfig(String logoUrl, String applicationName, String faviconUrl) {
            this.logoUrl = logoUrl;
            this.applicationName = applicationName;
            this.faviconUrl = faviconUrl;
        }

        public String getLogoUrl() { return logoUrl; }
        public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
        public String getApplicationName() { return applicationName; }
        public void setApplicationName(String applicationName) { this.applicationName = applicationName; }
        public String getFaviconUrl() { return faviconUrl; }
        public void setFaviconUrl(String faviconUrl) { this.faviconUrl = faviconUrl; }
    }

    /**
     * Feature flags
     */
    public static class FeatureFlags {
        private boolean enableBuilder;
        private boolean enableResourceBrowser;
        private boolean enablePackages;
        private boolean enableMigrations;
        private boolean enableDashboard;

        public FeatureFlags() {
        }

        public FeatureFlags(boolean enableBuilder, boolean enableResourceBrowser, 
                           boolean enablePackages, boolean enableMigrations, boolean enableDashboard) {
            this.enableBuilder = enableBuilder;
            this.enableResourceBrowser = enableResourceBrowser;
            this.enablePackages = enablePackages;
            this.enableMigrations = enableMigrations;
            this.enableDashboard = enableDashboard;
        }

        public boolean isEnableBuilder() { return enableBuilder; }
        public void setEnableBuilder(boolean enableBuilder) { this.enableBuilder = enableBuilder; }
        public boolean isEnableResourceBrowser() { return enableResourceBrowser; }
        public void setEnableResourceBrowser(boolean enableResourceBrowser) { this.enableResourceBrowser = enableResourceBrowser; }
        public boolean isEnablePackages() { return enablePackages; }
        public void setEnablePackages(boolean enablePackages) { this.enablePackages = enablePackages; }
        public boolean isEnableMigrations() { return enableMigrations; }
        public void setEnableMigrations(boolean enableMigrations) { this.enableMigrations = enableMigrations; }
        public boolean isEnableDashboard() { return enableDashboard; }
        public void setEnableDashboard(boolean enableDashboard) { this.enableDashboard = enableDashboard; }
    }

    /**
     * OIDC provider summary for login
     */
    public static class OidcProviderSummary {
        private String id;
        private String name;
        private String issuer;
        private String clientId;

        public OidcProviderSummary() {
        }

        public OidcProviderSummary(String id, String name, String issuer, String clientId) {
            this.id = id;
            this.name = name;
            this.issuer = issuer;
            this.clientId = clientId;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
    }
}
