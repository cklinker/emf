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
 * Supported field types
 */
export type FieldType =
  | 'string'
  | 'number'
  | 'boolean'
  | 'date'
  | 'datetime'
  | 'json'
  | 'reference'
  | 'picklist'
  | 'multi_picklist'
  | 'currency'
  | 'percent'
  | 'auto_number'
  | 'phone'
  | 'email'
  | 'url'
  | 'rich_text'
  | 'encrypted'
  | 'external_id'
  | 'geolocation'
  | 'lookup'
  | 'master_detail'
  | 'formula'
  | 'rollup_summary';

/**
 * Type-specific configuration for fields
 */
export interface FieldTypeConfig {
  /** Picklist: global picklist ID */
  globalPicklistId?: string;
  /** AutoNumber: prefix string */
  prefix?: string;
  /** AutoNumber: zero-padding width */
  padding?: number;
  /** Currency: decimal precision (0-6) */
  precision?: number;
  /** Currency: default ISO 4217 code */
  defaultCurrencyCode?: string;
  /** Formula: expression string */
  expression?: string;
  /** Formula: return type */
  returnType?: string;
  /** RollupSummary: child collection name */
  childCollection?: string;
  /** RollupSummary: aggregate function */
  aggregateFunction?: 'COUNT' | 'SUM' | 'MIN' | 'MAX' | 'AVG';
  /** RollupSummary: field to aggregate */
  aggregateField?: string;
  /** Geolocation: latitude */
  latitude?: number;
  /** Geolocation: longitude */
  longitude?: number;
  /** Lookup/MasterDetail: target collection name */
  targetCollection?: string;
  /** Lookup/MasterDetail: human-readable relationship name */
  relationshipName?: string;
}

/**
 * Field definition
 */
export interface FieldDefinition {
  id?: string;
  collectionId?: string;
  name: string;
  displayName?: string;
  type: FieldType;
  required?: boolean;
  unique?: boolean;
  indexed?: boolean;
  defaultValue?: string;
  referenceTarget?: string;
  fieldTypeConfig?: FieldTypeConfig;
  order?: number;
  active?: boolean;
  description?: string;
  constraints?: string;
  relationshipType?: 'LOOKUP' | 'MASTER_DETAIL';
  relationshipName?: string;
  cascadeDelete?: boolean;
  referenceCollectionId?: string;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * Relationship information for a field
 */
export interface RelationshipInfo {
  fieldId: string;
  fieldName: string;
  relationshipType: 'LOOKUP' | 'MASTER_DETAIL';
  relationshipName: string;
  targetCollectionId: string;
  targetCollectionName: string;
  cascadeDelete: boolean;
}

/**
 * All relationships for a collection
 */
export interface CollectionRelationships {
  collectionId: string;
  collectionName: string;
  outgoing: RelationshipInfo[];
  incoming: RelationshipInfo[];
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

/**
 * Global picklist definition
 */
export interface GlobalPicklist {
  id: string;
  tenantId: string;
  name: string;
  description?: string;
  sorted: boolean;
  restricted: boolean;
  createdAt: string;
  updatedAt: string;
}

/**
 * Picklist value
 */
export interface PicklistValue {
  id: string;
  value: string;
  label: string;
  isDefault: boolean;
  active: boolean;
  sortOrder: number;
  color?: string;
  description?: string;
}

/**
 * Picklist dependency mapping
 */
export interface PicklistDependency {
  id: string;
  controllingFieldId: string;
  dependentFieldId: string;
  mapping: Record<string, string[]>;
}

/**
 * Request to create a global picklist
 */
export interface CreateGlobalPicklistRequest {
  name: string;
  description?: string;
  sorted?: boolean;
  restricted?: boolean;
  values?: PicklistValueRequest[];
}

/**
 * Request to set picklist values
 */
export interface PicklistValueRequest {
  value: string;
  label: string;
  isDefault?: boolean;
  active?: boolean;
  sortOrder?: number;
  color?: string;
  description?: string;
}

/**
 * Request to set a picklist dependency
 */
export interface SetDependencyRequest {
  controllingFieldId: string;
  dependentFieldId: string;
  mapping: Record<string, string[]>;
}

// --- Validation Rules (Phase 2 Stream D) ---

/**
 * Collection-level validation rule using formula evaluation
 */
export interface CollectionValidationRule {
  id: string;
  collectionId: string;
  name: string;
  description?: string;
  active: boolean;
  errorConditionFormula: string;
  errorMessage: string;
  errorField?: string;
  evaluateOn: 'CREATE' | 'UPDATE' | 'CREATE_AND_UPDATE';
  createdAt: string;
  updatedAt: string;
}

/**
 * Request to create a collection validation rule
 */
export interface CreateCollectionValidationRuleRequest {
  name: string;
  description?: string;
  errorConditionFormula: string;
  errorMessage: string;
  errorField?: string;
  evaluateOn?: 'CREATE' | 'UPDATE' | 'CREATE_AND_UPDATE';
}

/**
 * Validation error returned when a record fails validation
 */
export interface CollectionValidationError {
  ruleName: string;
  errorMessage: string;
  errorField?: string;
}

// --- Record Types (Phase 2 Stream D) ---

/**
 * Record type definition
 */
export interface RecordType {
  id: string;
  collectionId: string;
  name: string;
  description?: string;
  active: boolean;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
}

/**
 * Request to create a record type
 */
export interface CreateRecordTypeRequest {
  name: string;
  description?: string;
  isDefault?: boolean;
}

/**
 * Picklist value override for a record type
 */
export interface RecordTypePicklistOverride {
  id: string;
  fieldId: string;
  fieldName: string;
  availableValues: string;
  defaultValue?: string;
}

/**
 * Request to set picklist override for a record type
 */
export interface SetPicklistOverrideRequest {
  availableValues: string[];
  defaultValue?: string;
}
