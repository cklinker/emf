/**
 * Core types used throughout the SDK
 */

// Re-export the admin FieldDefinition so consumers and ResourceMetadata
// share a single shape. Type-only import — no runtime cycle.
import type { FieldDefinition } from './admin/types';
export type { FieldDefinition };

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
