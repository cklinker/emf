/**
 * @emf/sdk - Type-safe TypeScript client for EMF APIs
 *
 * This package provides a comprehensive client for interacting with EMF services,
 * including resource discovery, CRUD operations, query building, and control plane
 * administration.
 */

// Client
export { EMFClient } from './client/EMFClient';
export type { EMFClientConfig, TokenProvider, CacheConfig, RetryConfig } from './client/types';

// Resources
export { ResourceClient } from './resources/ResourceClient';
export type {
  ListOptions,
  ListResponse,
  SortCriteria,
  FilterExpression,
  PaginationMeta,
} from './resources/types';

// Query Builder
export { QueryBuilder } from './query/QueryBuilder';

// Admin Client
export { AdminClient } from './admin/AdminClient';
export type {
  CollectionDefinition,
  FieldDefinition,
  Role,
  Policy,
  OIDCProvider,
  UIConfig,
  PackageData,
  ExportOptions,
  ImportResult,
  Migration,
  MigrationResult,
} from './admin/types';

// Authentication
export { TokenManager } from './auth/TokenManager';

// Errors
export {
  EMFError,
  ValidationError,
  AuthenticationError,
  AuthorizationError,
  NotFoundError,
  ServerError,
  NetworkError,
} from './errors';

// Validation Schemas
export {
  ResourceMetadataSchema,
  ListResponseSchema,
  ErrorResponseSchema,
} from './validation/schemas';

// Types
export type { ResourceMetadata, AuthzConfig, ValidationRule, User } from './types';

// CLI - Type Generation
export {
  generateTypesFromUrl,
  generateTypesFromSpec,
  parseCollections,
  validateOpenAPISpec,
  generateTypes,
} from './cli';
export type {
  OpenAPISpec,
  ParsedCollection,
  ParsedField,
  TypeGenerationOptions,
  TypeGenerationResult,
} from './cli';
