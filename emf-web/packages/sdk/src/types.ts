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
  type: 'string' | 'number' | 'boolean' | 'date' | 'datetime' | 'json' | 'reference';
  displayName?: string;
  required?: boolean;
  unique?: boolean;
  validation?: ValidationRule[];
  defaultValue?: unknown;
  referenceTarget?: string;
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
