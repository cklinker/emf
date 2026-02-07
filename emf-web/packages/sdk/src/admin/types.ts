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

/**
 * UI Page definition
 */
export interface UIPage {
  id?: string;
  name: string;
  path: string;
  component?: string;
  layout?: string;
  metadata?: Record<string, unknown>;
}

/**
 * UI Menu definition
 */
export interface UIMenu {
  id?: string;
  name: string;
  items: UIMenuItem[];
  position?: string;
}

/**
 * UI Menu item
 */
export interface UIMenuItem {
  id?: string;
  label: string;
  path?: string;
  icon?: string;
  children?: UIMenuItem[];
  order?: number;
}

/**
 * Tenant definition
 */
export interface Tenant {
  id: string;
  slug: string;
  name: string;
  edition: 'FREE' | 'PROFESSIONAL' | 'ENTERPRISE' | 'UNLIMITED';
  status: 'PROVISIONING' | 'ACTIVE' | 'SUSPENDED' | 'DECOMMISSIONED';
  settings?: string;
  limits?: string;
  createdAt: string;
  updatedAt: string;
}

/**
 * Request to create a tenant
 */
export interface CreateTenantRequest {
  slug: string;
  name: string;
  edition?: string;
  settings?: Record<string, unknown>;
  limits?: Partial<GovernorLimits>;
}

/**
 * Request to update a tenant
 */
export interface UpdateTenantRequest {
  name?: string;
  edition?: string;
  settings?: Record<string, unknown>;
  limits?: Partial<GovernorLimits>;
}

/**
 * Governor limits for a tenant
 */
export interface GovernorLimits {
  apiCallsPerDay: number;
  storageGb: number;
  maxUsers: number;
  maxCollections: number;
  maxFieldsPerCollection: number;
  maxWorkflows: number;
  maxReports: number;
}

/**
 * Paginated response
 */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/**
 * Platform user
 */
export interface PlatformUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  username?: string;
  status: 'ACTIVE' | 'INACTIVE' | 'LOCKED' | 'PENDING_ACTIVATION';
  locale: string;
  timezone: string;
  profileId?: string;
  managerId?: string;
  lastLoginAt?: string;
  loginCount: number;
  mfaEnabled: boolean;
  createdAt: string;
  updatedAt: string;
}

/**
 * Request to create a user
 */
export interface CreatePlatformUserRequest {
  email: string;
  firstName: string;
  lastName: string;
  username?: string;
  locale?: string;
  timezone?: string;
  profileId?: string;
}

/**
 * Request to update a user
 */
export interface UpdatePlatformUserRequest {
  firstName?: string;
  lastName?: string;
  username?: string;
  locale?: string;
  timezone?: string;
  managerId?: string;
  profileId?: string;
}

/**
 * Login history entry
 */
export interface LoginHistoryEntry {
  id: string;
  userId: string;
  loginTime: string;
  sourceIp: string;
  loginType: 'UI' | 'API' | 'OAUTH' | 'SERVICE_ACCOUNT';
  status: 'SUCCESS' | 'FAILED' | 'LOCKED_OUT';
  userAgent: string;
}

/**
 * Profile definition
 */
export interface Profile {
  id: string;
  name: string;
  description?: string;
  system: boolean;
  objectPermissions?: ObjectPermission[];
  fieldPermissions?: FieldPermissionEntry[];
  systemPermissions?: SystemPermissionEntry[];
  createdAt: string;
  updatedAt: string;
}

/**
 * Object permission (collection-level CRUD)
 */
export interface ObjectPermission {
  id?: string;
  collectionId: string;
  canCreate: boolean;
  canRead: boolean;
  canEdit: boolean;
  canDelete: boolean;
  canViewAll: boolean;
  canModifyAll: boolean;
}

/**
 * Field permission entry
 */
export interface FieldPermissionEntry {
  id?: string;
  fieldId: string;
  visibility: 'VISIBLE' | 'READ_ONLY' | 'HIDDEN';
}

/**
 * System permission entry
 */
export interface SystemPermissionEntry {
  id?: string;
  permissionKey: string;
  granted: boolean;
}

/**
 * Request to create a profile
 */
export interface CreateProfileRequest {
  name: string;
  description?: string;
}

/**
 * Request to update a profile
 */
export interface UpdateProfileRequest {
  name?: string;
  description?: string;
}

/**
 * Request to set object permissions
 */
export interface ObjectPermissionRequest {
  canCreate: boolean;
  canRead: boolean;
  canEdit: boolean;
  canDelete: boolean;
  canViewAll: boolean;
  canModifyAll: boolean;
}

/**
 * Request to set field permissions
 */
export interface FieldPermissionRequest {
  fieldId: string;
  visibility: 'VISIBLE' | 'READ_ONLY' | 'HIDDEN';
}

/**
 * Request to set system permissions
 */
export interface SystemPermissionRequest {
  permissionKey: string;
  granted: boolean;
}

/**
 * Permission set definition
 */
export interface PermissionSet {
  id: string;
  name: string;
  description?: string;
  system: boolean;
  objectPermissions?: ObjectPermission[];
  fieldPermissions?: FieldPermissionEntry[];
  systemPermissions?: SystemPermissionEntry[];
  createdAt: string;
  updatedAt: string;
}

/**
 * Request to create a permission set
 */
export interface CreatePermissionSetRequest {
  name: string;
  description?: string;
}

/**
 * Request to update a permission set
 */
export interface UpdatePermissionSetRequest {
  name?: string;
  description?: string;
}

/**
 * Migration plan result
 */
export interface MigrationPlan {
  steps: MigrationStep[];
  warnings?: string[];
  estimatedDuration?: number;
}

/**
 * Migration step
 */
export interface MigrationStep {
  type: string;
  description: string;
  sql?: string;
  reversible: boolean;
}

/**
 * Migration run details
 */
export interface MigrationRun {
  id: string;
  migrationId: string;
  status: 'pending' | 'running' | 'completed' | 'failed';
  startedAt?: string;
  completedAt?: string;
  error?: string;
  steps: MigrationStepResult[];
}

/**
 * Migration step result
 */
export interface MigrationStepResult {
  step: MigrationStep;
  status: 'pending' | 'running' | 'completed' | 'failed';
  error?: string;
}
