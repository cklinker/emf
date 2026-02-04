/**
 * Configuration Types
 * 
 * Types related to bootstrap configuration, pages, menus, themes, and branding.
 */

/**
 * Bootstrap configuration returned from /ui/config/bootstrap
 */
export interface BootstrapConfig {
  pages: PageConfig[];
  menus: MenuConfig[];
  theme: ThemeConfig;
  branding: BrandingConfig;
  features: FeatureFlags;
  oidcProviders: OIDCProviderSummary[];
}

/**
 * Page configuration for dynamic routing
 */
export interface PageConfig {
  id: string;
  path: string;
  title: string;
  component: string;
  props?: Record<string, unknown>;
  policies?: string[];
}

/**
 * Menu configuration for navigation
 */
export interface MenuConfig {
  id: string;
  name: string;
  items: MenuItemConfig[];
}

/**
 * Menu item configuration
 */
export interface MenuItemConfig {
  id: string;
  label: string;
  path?: string;
  icon?: string;
  children?: MenuItemConfig[];
  policies?: string[];
}

/**
 * Theme configuration
 */
export interface ThemeConfig {
  primaryColor: string;
  secondaryColor: string;
  fontFamily: string;
  borderRadius: string;
}

/**
 * Branding configuration
 */
export interface BrandingConfig {
  logoUrl: string;
  applicationName: string;
  faviconUrl: string;
}

/**
 * Feature flags for enabling/disabling features
 */
export interface FeatureFlags {
  enableBuilder: boolean;
  enableResourceBrowser: boolean;
  enablePackages: boolean;
  enableMigrations: boolean;
  enableDashboard: boolean;
}

/**
 * OIDC provider summary for login page
 */
export interface OIDCProviderSummary {
  id: string;
  name: string;
  issuer: string;
  clientId: string;
}
