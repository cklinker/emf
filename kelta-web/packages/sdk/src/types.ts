/**
 * Core types used throughout the SDK
 */

/**
 * Resource metadata from discovery endpoint
 */
export interface ResourceMetadata {
  name: string;
  displayName: string;
  fields: FieldDefinition[];
  operations: string[];
  authz?: AuthzConfig;
}

/**
 * Field definition within a resource
 */
export interface FieldDefinition {
  name: string;
  type:
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
  displayName?: string;
  required?: boolean;
  unique?: boolean;
  validation?: ValidationRule[];
  defaultValue?: unknown;
  referenceTarget?: string;
  fieldTypeConfig?: Record<string, unknown>;
}

/**
 * Validation rule for a field
 */
export interface ValidationRule {
  type: 'min' | 'max' | 'pattern' | 'email' | 'url' | 'custom';
  value?: unknown;
  message?: string;
}

/**
 * Authorization configuration for a resource
 */
export interface AuthzConfig {
  read?: string[];
  create?: string[];
  update?: string[];
  delete?: string[];
  fieldLevel?: Record<string, string[]>;
}

/**
 * User information
 */
export interface User {
  id: string;
  username: string;
  email?: string;
  roles: string[];
  attributes?: Record<string, unknown>;
}
