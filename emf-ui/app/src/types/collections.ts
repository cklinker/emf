/**
 * Collection Types
 * 
 * Types related to collections, fields, and validation rules.
 */

/**
 * Collection definition
 */
export interface Collection {
  id: string;
  name: string;
  displayName: string;
  description?: string;
  storageMode: 'PHYSICAL_TABLE' | 'JSONB';
  active: boolean;
  currentVersion: number;
  fields?: FieldDefinition[];
  authz?: CollectionAuthz;
  createdAt: string;
  updatedAt: string;
}

/**
 * Field definition within a collection
 */
export interface FieldDefinition {
  id: string;
  collectionId?: string;
  name: string;
  displayName?: string;
  type: FieldType;
  required: boolean;
  unique: boolean;
  indexed: boolean;
  defaultValue?: string;
  referenceTarget?: string;
  order: number;
  active?: boolean;
  description?: string;
  constraints?: string;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * Supported field types
 */
export type FieldType = 'string' | 'number' | 'boolean' | 'date' | 'datetime' | 'json' | 'reference';

/**
 * Validation rule for a field
 */
export interface ValidationRule {
  type: 'min' | 'max' | 'pattern' | 'email' | 'url' | 'custom';
  value?: unknown;
  message?: string;
}

/**
 * Collection authorization configuration
 */
export interface CollectionAuthz {
  routePolicies: RoutePolicyConfig[];
  fieldPolicies: FieldPolicyConfig[];
}

/**
 * Route-level policy configuration
 */
export interface RoutePolicyConfig {
  operation: 'read' | 'create' | 'update' | 'delete';
  policyId: string;
}

/**
 * Field-level policy configuration
 */
export interface FieldPolicyConfig {
  fieldName: string;
  operation: 'read' | 'write';
  policyId: string;
}

/**
 * Collection version for history tracking
 */
export interface CollectionVersion {
  id: string;
  version: number;
  schema: string;
  createdAt: string;
}
