/**
 * Collection Types
 *
 * Types related to collections, fields, and validation rules.
 */

/**
 * Collection definition
 */
export interface Collection {
  id: string
  name: string
  displayName: string
  description?: string
  storageMode: 'PHYSICAL_TABLE' | 'JSONB'
  active: boolean
  currentVersion: number
  fields?: FieldDefinition[]
  authz?: CollectionAuthz
  createdAt: string
  updatedAt: string
}

/**
 * Field definition within a collection
 */
export interface FieldDefinition {
  id: string
  collectionId?: string
  name: string
  displayName?: string
  type: FieldType
  required: boolean
  unique: boolean
  indexed: boolean
  defaultValue?: string
  referenceTarget?: string
  fieldTypeConfig?: string
  order: number
  active?: boolean
  description?: string
  constraints?: string
  relationshipType?: 'LOOKUP' | 'MASTER_DETAIL'
  relationshipName?: string
  cascadeDelete?: boolean
  referenceCollectionId?: string
  createdAt?: string
  updatedAt?: string
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
  | 'rollup_summary'

/**
 * Validation rule for a field
 */
export interface ValidationRule {
  type: 'min' | 'max' | 'pattern' | 'email' | 'url' | 'custom'
  value?: unknown
  message?: string
}

/**
 * Collection authorization configuration
 */
export interface CollectionAuthz {
  routePolicies: RoutePolicyConfig[]
  fieldPolicies: FieldPolicyConfig[]
}

/**
 * Route-level policy configuration
 */
export interface RoutePolicyConfig {
  operation: 'read' | 'create' | 'update' | 'delete'
  policyId: string
}

/**
 * Field-level policy configuration
 */
export interface FieldPolicyConfig {
  fieldName: string
  operation: 'read' | 'write'
  policyId: string
}

/**
 * Collection version for history tracking
 */
export interface CollectionVersion {
  id: string
  version: number
  schema: string
  createdAt: string
}

/**
 * Collection-level validation rule using formula evaluation
 */
export interface CollectionValidationRule {
  id: string
  collectionId: string
  name: string
  description?: string
  active: boolean
  errorConditionFormula: string
  errorMessage: string
  errorField?: string
  evaluateOn: 'CREATE' | 'UPDATE' | 'CREATE_AND_UPDATE'
  createdAt: string
  updatedAt: string
}

/**
 * Record type definition
 */
export interface RecordType {
  id: string
  collectionId: string
  name: string
  description?: string
  active: boolean
  isDefault: boolean
  createdAt: string
  updatedAt: string
}

/**
 * Picklist override for a record type
 */
export interface RecordTypePicklistOverride {
  id: string
  fieldId: string
  fieldName: string
  availableValues: string
  defaultValue?: string
}
