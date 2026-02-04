/**
 * Admin/Control Plane types
 */

import type { AuthzConfig } from '../types';

/**
 * Collection definition for admin operations
 */
export interface CollectionDefinition {
  id?: string;
  name: string;
  displayName: string;
  description?: string;
  storageMode?: 'PHYSICAL_TABLE' | 'JSONB';
  active?: boolean;
  currentVersion?: number;
  fields?: FieldDefinition[];
  authz?: AuthzConfig;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * Field definition
 */
export interface FieldDefinition {
  id?: string;
  collectionId?: string;
  name: string;
  displayName?: string;
  type: string;
  required?: boolean;
  unique?: boolean;
  indexed?: boolean;
  defaultValue?: string;
  referenceTarget?: string;
  order?: number;
  active?: boolean;
  description?: string;
  constraints?: string;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * Role definition
 */
export interface Role {
  id?: string;
  name: string;
  description?: string;
  createdAt?: string;
}

/**
 * Policy definition
 */
export interface Policy {
  id?: string;
  name: string;
  description?: string;
  expression?: string;
  rules?: string;
  createdAt?: string;
}

/**
 * OIDC Provider configuration
 */
export interface OIDCProvider {
  id: string;
  name: string;
  issuer: string;
  clientId: string;
  clientSecret: string;
  scopes: string[];
  enabled: boolean;
}

/**
 * UI configuration
 */
export interface UIConfig {
  theme?: ThemeConfig;
  branding?: BrandingConfig;
  features?: Record<string, boolean>;
}

/**
 * Theme configuration
 */
export interface ThemeConfig {
  primaryColor?: string;
  secondaryColor?: string;
  fontFamily?: string;
}

/**
 * Branding configuration
 */
export interface BrandingConfig {
  logo?: string;
  title?: string;
  favicon?: string;
}

/**
 * Package data for import/export
 */
export interface PackageData {
  version: string;
  collections: CollectionDefinition[];
  roles: Role[];
  policies: Policy[];
  uiConfig?: UIConfig;
}

/**
 * Export options
 */
export interface ExportOptions {
  includeCollections?: string[];
  includeRoles?: boolean;
  includePolicies?: boolean;
  includeUIConfig?: boolean;
}

/**
 * Import result
 */
export interface ImportResult {
  success: boolean;
  imported: {
    collections: number;
    roles: number;
    policies: number;
  };
  errors?: string[];
}

/**
 * Migration definition
 */
export interface Migration {
  id: string;
  name: string;
  description?: string;
  status: 'pending' | 'running' | 'completed' | 'failed';
  createdAt: string;
  completedAt?: string;
}

/**
 * Migration result
 */
export interface MigrationResult {
  success: boolean;
  message: string;
  details?: unknown;
}
