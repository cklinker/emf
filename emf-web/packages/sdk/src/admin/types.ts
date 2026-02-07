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
 * Organization-wide default
 */
export interface OrgWideDefault {
  id?: string;
  collectionId: string;
  internalAccess: 'PRIVATE' | 'PUBLIC_READ' | 'PUBLIC_READ_WRITE';
  externalAccess: 'PRIVATE' | 'PUBLIC_READ' | 'PUBLIC_READ_WRITE';
}

/**
 * Request to set OWD
 */
export interface SetOwdRequest {
  internalAccess: 'PRIVATE' | 'PUBLIC_READ' | 'PUBLIC_READ_WRITE';
  externalAccess?: 'PRIVATE' | 'PUBLIC_READ' | 'PUBLIC_READ_WRITE';
}

/**
 * Sharing rule
 */
export interface SharingRule {
  id: string;
  collectionId: string;
  name: string;
  ruleType: 'OWNER_BASED' | 'CRITERIA_BASED';
  sharedFrom?: string;
  sharedTo: string;
  sharedToType: 'ROLE' | 'GROUP' | 'QUEUE';
  accessLevel: 'READ' | 'READ_WRITE';
  criteria?: string;
  active: boolean;
}

/**
 * Request to create a sharing rule
 */
export interface CreateSharingRuleRequest {
  name: string;
  ruleType: 'OWNER_BASED' | 'CRITERIA_BASED';
  sharedFrom?: string;
  sharedTo: string;
  sharedToType: 'ROLE' | 'GROUP' | 'QUEUE';
  accessLevel: 'READ' | 'READ_WRITE';
  criteria?: string;
}

/**
 * Request to update a sharing rule
 */
export interface UpdateSharingRuleRequest {
  name?: string;
  sharedFrom?: string;
  sharedTo?: string;
  sharedToType?: 'ROLE' | 'GROUP' | 'QUEUE';
  accessLevel?: 'READ' | 'READ_WRITE';
  criteria?: string;
  active?: boolean;
}

/**
 * Record share
 */
export interface RecordShare {
  id: string;
  collectionId: string;
  recordId: string;
  sharedWithId: string;
  sharedWithType: 'USER' | 'GROUP' | 'ROLE';
  accessLevel: 'READ' | 'READ_WRITE';
  reason: 'MANUAL' | 'RULE' | 'TEAM' | 'TERRITORY';
  createdBy: string;
  createdAt: string;
}

/**
 * User group
 */
export interface UserGroup {
  id: string;
  name: string;
  description?: string;
  groupType: 'PUBLIC' | 'QUEUE';
  memberIds?: string[];
}

/**
 * Request to create a user group
 */
export interface CreateUserGroupRequest {
  name: string;
  description?: string;
  groupType?: 'PUBLIC' | 'QUEUE';
  memberIds?: string[];
}

/**
 * Role hierarchy node
 */
export interface RoleHierarchyNode {
  id: string;
  name: string;
  description?: string;
  parentRoleId?: string;
  hierarchyLevel: number;
  children?: RoleHierarchyNode[];
}

/**
 * Setup audit trail entry
 */
export interface SetupAuditTrailEntry {
  id: string;
  userId: string;
  action: 'CREATED' | 'UPDATED' | 'DELETED' | 'ACTIVATED' | 'DEACTIVATED';
  section: string;
  entityType: string;
  entityId?: string;
  entityName?: string;
  oldValue?: string;
  newValue?: string;
  timestamp: string;
}

/**
 * Governor limits status
 */
export interface GovernorLimitsStatus {
  limits: GovernorLimits;
  apiCallsUsed: number;
  apiCallsLimit: number;
  usersUsed: number;
  usersLimit: number;
  collectionsUsed: number;
  collectionsLimit: number;
}

/**
 * Governor limits configuration
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

// --- Field History (Phase 2 Stream E) ---

/**
 * Field history entry tracking a single field change
 */
export interface FieldHistoryEntry {
  id: string;
  collectionId: string;
  recordId: string;
  fieldName: string;
  oldValue: unknown;
  newValue: unknown;
  changedBy: string;
  changedAt: string;
  changeSource: 'UI' | 'API' | 'WORKFLOW' | 'SYSTEM' | 'IMPORT';
}

// --- Page Layouts (Phase 3 Stream A) ---

export interface PageLayout {
  id: string;
  tenantId: string;
  collectionId: string;
  name: string;
  description?: string;
  layoutType: 'DETAIL' | 'EDIT' | 'MINI' | 'LIST';
  isDefault: boolean;
  sections: LayoutSection[];
  relatedLists: LayoutRelatedList[];
  createdAt: string;
  updatedAt: string;
}

export interface LayoutSection {
  id: string;
  heading: string;
  columns: number;
  sortOrder: number;
  collapsed: boolean;
  style: 'DEFAULT' | 'COLLAPSIBLE' | 'CARD';
  fields: LayoutFieldPlacement[];
}

export interface LayoutFieldPlacement {
  id: string;
  fieldId: string;
  columnNumber: number;
  sortOrder: number;
  requiredOnLayout: boolean;
  readOnlyOnLayout: boolean;
}

export interface LayoutRelatedList {
  id: string;
  relatedCollectionId: string;
  relationshipField: string;
  displayColumns: string;
  sortField?: string;
  sortDirection: 'ASC' | 'DESC';
  rowLimit: number;
  sortOrder: number;
}

export interface CreatePageLayoutRequest {
  collectionId: string;
  name: string;
  description?: string;
  layoutType: string;
  isDefault?: boolean;
  sections?: CreateLayoutSectionRequest[];
  relatedLists?: CreateRelatedListRequest[];
}

export interface CreateLayoutSectionRequest {
  heading: string;
  columns?: number;
  sortOrder: number;
  collapsed?: boolean;
  style?: string;
  fields?: CreateFieldPlacementRequest[];
}

export interface CreateFieldPlacementRequest {
  fieldId: string;
  columnNumber?: number;
  sortOrder: number;
  requiredOnLayout?: boolean;
  readOnlyOnLayout?: boolean;
}

export interface CreateRelatedListRequest {
  relatedCollectionId: string;
  relationshipField: string;
  displayColumns?: string;
  sortField?: string;
  sortDirection?: string;
  rowLimit?: number;
  sortOrder: number;
}

export interface LayoutAssignment {
  id: string;
  collectionId: string;
  profileId?: string;
  recordTypeId?: string;
  layoutId: string;
  createdAt: string;
  updatedAt: string;
}

export interface LayoutAssignmentRequest {
  collectionId: string;
  profileId?: string;
  recordTypeId?: string;
  layoutId: string;
}

// --- List Views (Phase 3 Stream B) ---

export interface ListView {
  id: string;
  tenantId: string;
  collectionId: string;
  name: string;
  columns: string;
  filters: string;
  filterLogic?: string;
  sortField?: string;
  sortDirection: 'ASC' | 'DESC';
  chartConfig?: string;
  visibility: 'PRIVATE' | 'PUBLIC' | 'GROUP';
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateListViewRequest {
  collectionId: string;
  name: string;
  columns: string;
  filters?: string;
  filterLogic?: string;
  sortField?: string;
  sortDirection?: string;
  chartConfig?: string;
  visibility?: string;
}

// --- Reports (Phase 3 Stream C) ---

export interface Report {
  id: string;
  name: string;
  description?: string;
  reportType: 'TABULAR' | 'SUMMARY' | 'MATRIX';
  primaryCollectionId: string;
  relatedJoins: string;
  columns: string;
  filters: string;
  filterLogic?: string;
  rowGroupings: string;
  columnGroupings: string;
  sortOrder: string;
  chartType?: string;
  chartConfig?: string;
  scope: 'MY_RECORDS' | 'ALL_RECORDS' | 'MY_TEAM_RECORDS';
  folderId?: string;
  accessLevel: 'PRIVATE' | 'PUBLIC' | 'HIDDEN';
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface ReportFolder {
  id: string;
  tenantId: string;
  name: string;
  accessLevel: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateReportRequest {
  name: string;
  description?: string;
  reportType: string;
  primaryCollectionId: string;
  relatedJoins?: string;
  columns: string;
  filters?: string;
  filterLogic?: string;
  rowGroupings?: string;
  columnGroupings?: string;
  sortOrder?: string;
  chartType?: string;
  chartConfig?: string;
  scope?: string;
  folderId?: string;
  accessLevel?: string;
}

// --- Dashboards (Phase 3 Stream D) ---

export interface UserDashboard {
  id: string;
  name: string;
  description?: string;
  folderId?: string;
  accessLevel: 'PRIVATE' | 'PUBLIC' | 'HIDDEN';
  dynamic: boolean;
  runningUserId?: string;
  columnCount: number;
  createdBy: string;
  components: DashboardComponent[];
  createdAt: string;
  updatedAt: string;
}

export interface DashboardComponent {
  id: string;
  reportId: string;
  componentType: 'CHART' | 'GAUGE' | 'METRIC' | 'TABLE';
  title?: string;
  columnPosition: number;
  rowPosition: number;
  columnSpan: number;
  rowSpan: number;
  config: string;
  sortOrder: number;
}

export interface CreateDashboardRequest {
  name: string;
  description?: string;
  folderId?: string;
  accessLevel?: string;
  dynamic?: boolean;
  runningUserId?: string;
  columnCount?: number;
  components?: CreateDashboardComponentRequest[];
}

export interface CreateDashboardComponentRequest {
  reportId: string;
  componentType: string;
  title?: string;
  columnPosition: number;
  rowPosition: number;
  columnSpan?: number;
  rowSpan?: number;
  config?: string;
  sortOrder: number;
}

// --- Data Export (Phase 3 Stream E) ---

export interface ExportRequest {
  filename?: string;
  columns: string[];
  rows: Record<string, unknown>[];
}

// --- Email Templates (Phase 4 Stream A) ---

export interface EmailTemplate {
  id: string;
  name: string;
  description?: string;
  subject: string;
  bodyHtml: string;
  bodyText?: string;
  relatedCollectionId?: string;
  folder?: string;
  active: boolean;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface EmailLog {
  id: string;
  templateId?: string;
  recipientEmail: string;
  subject: string;
  status: 'QUEUED' | 'SENT' | 'FAILED';
  source?: string;
  sourceId?: string;
  errorMessage?: string;
  sentAt?: string;
  createdAt: string;
}

export interface CreateEmailTemplateRequest {
  name: string;
  description?: string;
  subject: string;
  bodyHtml: string;
  bodyText?: string;
  relatedCollectionId?: string;
  folder?: string;
  active?: boolean;
}

// --- Workflow Rules (Phase 4 Stream B) ---

export interface WorkflowRule {
  id: string;
  collectionId: string;
  name: string;
  description?: string;
  active: boolean;
  triggerType: 'ON_CREATE' | 'ON_UPDATE' | 'ON_CREATE_OR_UPDATE' | 'ON_DELETE';
  filterFormula?: string;
  reEvaluateOnUpdate: boolean;
  executionOrder: number;
  actions: WorkflowAction[];
  createdAt: string;
  updatedAt: string;
}

export interface WorkflowAction {
  id: string;
  actionType:
    | 'FIELD_UPDATE'
    | 'EMAIL_ALERT'
    | 'CREATE_RECORD'
    | 'INVOKE_SCRIPT'
    | 'OUTBOUND_MESSAGE'
    | 'CREATE_TASK'
    | 'PUBLISH_EVENT';
  executionOrder: number;
  config: string;
  active: boolean;
}

export interface WorkflowExecutionLog {
  id: string;
  workflowRuleId: string;
  recordId: string;
  triggerType: string;
  status: 'SUCCESS' | 'PARTIAL_FAILURE' | 'FAILURE';
  actionsExecuted: number;
  errorMessage?: string;
  executedAt: string;
  durationMs?: number;
}

export interface CreateWorkflowRuleRequest {
  collectionId: string;
  name: string;
  description?: string;
  active?: boolean;
  triggerType: string;
  filterFormula?: string;
  reEvaluateOnUpdate?: boolean;
  executionOrder?: number;
  actions?: CreateWorkflowActionRequest[];
}

export interface CreateWorkflowActionRequest {
  actionType: string;
  executionOrder?: number;
  config: string;
  active?: boolean;
}

// --- Approval Processes (Phase 4 Stream C) ---

export interface ApprovalProcess {
  id: string;
  collectionId: string;
  name: string;
  description?: string;
  active: boolean;
  entryCriteria?: string;
  recordEditability: 'LOCKED' | 'ADMIN_ONLY';
  initialSubmitterField?: string;
  onSubmitFieldUpdates: string;
  onApprovalFieldUpdates: string;
  onRejectionFieldUpdates: string;
  onRecallFieldUpdates: string;
  allowRecall: boolean;
  executionOrder: number;
  steps: ApprovalStep[];
  createdAt: string;
  updatedAt: string;
}

export interface ApprovalStep {
  id: string;
  stepNumber: number;
  name: string;
  description?: string;
  entryCriteria?: string;
  approverType: 'USER' | 'ROLE' | 'QUEUE' | 'MANAGER_HIERARCHY' | 'RELATED_USER';
  approverId?: string;
  approverField?: string;
  unanimityRequired: boolean;
  escalationTimeoutHours?: number;
  escalationAction?: string;
  onApproveAction: string;
  onRejectAction: string;
}

export interface ApprovalInstance {
  id: string;
  approvalProcessId: string;
  approvalProcessName: string;
  collectionId: string;
  recordId: string;
  submittedBy: string;
  currentStepNumber: number;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'RECALLED';
  submittedAt: string;
  completedAt?: string;
  stepInstances: ApprovalStepInstance[];
}

export interface ApprovalStepInstance {
  id: string;
  stepId: string;
  assignedTo: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'REASSIGNED';
  comments?: string;
  actedAt?: string;
}

export interface CreateApprovalProcessRequest {
  collectionId: string;
  name: string;
  description?: string;
  active?: boolean;
  entryCriteria?: string;
  recordEditability?: string;
  initialSubmitterField?: string;
  onSubmitFieldUpdates?: string;
  onApprovalFieldUpdates?: string;
  onRejectionFieldUpdates?: string;
  onRecallFieldUpdates?: string;
  allowRecall?: boolean;
  executionOrder?: number;
  steps?: CreateApprovalStepRequest[];
}

export interface CreateApprovalStepRequest {
  stepNumber: number;
  name: string;
  description?: string;
  entryCriteria?: string;
  approverType: string;
  approverId?: string;
  approverField?: string;
  unanimityRequired?: boolean;
  escalationTimeoutHours?: number;
  escalationAction?: string;
  onApproveAction?: string;
  onRejectAction?: string;
}

// --- Flow Engine (Phase 4 Stream D) ---

export interface FlowDefinition {
  id: string;
  name: string;
  description?: string;
  flowType: 'RECORD_TRIGGERED' | 'SCHEDULED' | 'AUTOLAUNCHED' | 'SCREEN';
  active: boolean;
  version: number;
  triggerConfig?: string;
  definition: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface FlowExecution {
  id: string;
  flowId: string;
  flowName: string;
  status: 'RUNNING' | 'COMPLETED' | 'FAILED' | 'WAITING' | 'CANCELLED';
  startedBy?: string;
  triggerRecordId?: string;
  variables: string;
  currentNodeId?: string;
  errorMessage?: string;
  startedAt: string;
  completedAt?: string;
}

export interface CreateFlowRequest {
  name: string;
  description?: string;
  flowType: string;
  active?: boolean;
  triggerConfig?: string;
  definition: string;
}

// --- Scheduled Jobs (Phase 4 Stream E) ---

export interface ScheduledJob {
  id: string;
  name: string;
  description?: string;
  jobType: 'FLOW' | 'SCRIPT' | 'REPORT_EXPORT';
  jobReferenceId?: string;
  cronExpression: string;
  timezone: string;
  active: boolean;
  lastRunAt?: string;
  lastStatus?: string;
  nextRunAt?: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface JobExecutionLog {
  id: string;
  jobId: string;
  status: string;
  recordsProcessed: number;
  errorMessage?: string;
  startedAt: string;
  completedAt?: string;
  durationMs?: number;
}

export interface CreateScheduledJobRequest {
  name: string;
  description?: string;
  jobType: string;
  jobReferenceId?: string;
  cronExpression: string;
  timezone?: string;
  active?: boolean;
}

// --- Scripts (Phase 5 Stream A) ---

export interface Script {
  id: string;
  tenantId: string;
  name: string;
  description?: string;
  scriptType:
    | 'BEFORE_TRIGGER'
    | 'AFTER_TRIGGER'
    | 'SCHEDULED'
    | 'API_ENDPOINT'
    | 'VALIDATION'
    | 'EVENT_HANDLER'
    | 'EMAIL_HANDLER';
  language: string;
  sourceCode: string;
  active: boolean;
  version: number;
  createdBy: string;
  triggers: ScriptTrigger[];
  createdAt: string;
  updatedAt: string;
}

export interface ScriptTrigger {
  id: string;
  collectionId: string;
  triggerEvent: 'INSERT' | 'UPDATE' | 'DELETE';
  executionOrder: number;
  active: boolean;
}

export interface ScriptExecutionLog {
  id: string;
  tenantId: string;
  scriptId: string;
  status: 'SUCCESS' | 'FAILURE' | 'TIMEOUT' | 'GOVERNOR_LIMIT';
  triggerType?: string;
  recordId?: string;
  durationMs?: number;
  cpuMs?: number;
  queriesExecuted: number;
  dmlRows: number;
  callouts: number;
  errorMessage?: string;
  logOutput?: string;
  executedAt: string;
}

export interface CreateScriptRequest {
  name: string;
  description?: string;
  scriptType: string;
  language?: string;
  sourceCode: string;
  active?: boolean;
  triggers?: CreateScriptTriggerRequest[];
}

export interface CreateScriptTriggerRequest {
  collectionId: string;
  triggerEvent: string;
  executionOrder?: number;
  active?: boolean;
}

// --- Webhooks (Phase 5 Stream B) ---

export interface Webhook {
  id: string;
  tenantId: string;
  name: string;
  url: string;
  events: string;
  collectionId?: string;
  filterFormula?: string;
  headers?: string;
  secret?: string;
  active: boolean;
  retryPolicy?: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface WebhookDelivery {
  id: string;
  webhookId: string;
  eventType: string;
  payload: string;
  responseStatus?: number;
  responseBody?: string;
  attemptCount: number;
  status: 'PENDING' | 'DELIVERED' | 'FAILED' | 'RETRYING';
  nextRetryAt?: string;
  createdAt: string;
  deliveredAt?: string;
}

export interface CreateWebhookRequest {
  name: string;
  url: string;
  events: string;
  collectionId?: string;
  filterFormula?: string;
  headers?: string;
  secret?: string;
  active?: boolean;
  retryPolicy?: string;
}

// --- Connected Apps (Phase 5 Stream C) ---

export interface ConnectedApp {
  id: string;
  tenantId: string;
  name: string;
  description?: string;
  clientId: string;
  redirectUris?: string;
  scopes?: string;
  ipRestrictions?: string;
  rateLimitPerHour: number;
  active: boolean;
  lastUsedAt?: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface ConnectedAppCreatedResponse extends ConnectedApp {
  clientSecret: string;
}

export interface ConnectedAppToken {
  id: string;
  connectedAppId: string;
  scopes: string;
  issuedAt: string;
  expiresAt: string;
  revoked: boolean;
}

export interface CreateConnectedAppRequest {
  name: string;
  description?: string;
  redirectUris?: string;
  scopes?: string;
  ipRestrictions?: string;
  rateLimitPerHour?: number;
  active?: boolean;
}

// --- Bulk Jobs (Phase 5 Stream D) ---

export interface BulkJob {
  id: string;
  tenantId: string;
  collectionId: string;
  operation: 'INSERT' | 'UPDATE' | 'UPSERT' | 'DELETE';
  status: 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'ABORTED';
  totalRecords: number;
  processedRecords: number;
  successRecords: number;
  errorRecords: number;
  externalIdField?: string;
  contentType: string;
  batchSize: number;
  createdBy: string;
  startedAt?: string;
  completedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface BulkJobResult {
  id: string;
  bulkJobId: string;
  recordIndex: number;
  recordId?: string;
  status: 'SUCCESS' | 'FAILURE';
  errorMessage?: string;
  createdAt: string;
}

export interface CreateBulkJobRequest {
  collectionId: string;
  operation: string;
  externalIdField?: string;
  batchSize?: number;
  records: Record<string, unknown>[];
}

export interface CompositeSubRequest {
  method: string;
  url: string;
  body?: Record<string, unknown>;
  referenceId: string;
}

export interface CompositeRequest {
  compositeRequest: CompositeSubRequest[];
}

export interface CompositeSubResponse {
  referenceId: string;
  httpStatusCode: number;
  body: unknown;
}

export interface CompositeResponse {
  compositeResponse: CompositeSubResponse[];
}
