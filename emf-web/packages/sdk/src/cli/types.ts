/**
 * Types for the OpenAPI type generation CLI
 */

/**
 * OpenAPI specification structure (simplified for EMF use case)
 */
export interface OpenAPISpec {
  openapi: string;
  info: {
    title: string;
    version: string;
    description?: string;
  };
  paths: Record<string, PathItem>;
  components?: {
    schemas?: Record<string, SchemaObject>;
    responses?: Record<string, ResponseObject>;
    requestBodies?: Record<string, RequestBodyObject>;
  };
}

/**
 * OpenAPI path item
 */
export interface PathItem {
  get?: OperationObject;
  post?: OperationObject;
  put?: OperationObject;
  patch?: OperationObject;
  delete?: OperationObject;
  parameters?: ParameterObject[];
}

/**
 * OpenAPI operation object
 */
export interface OperationObject {
  operationId?: string;
  summary?: string;
  description?: string;
  tags?: string[];
  parameters?: ParameterObject[];
  requestBody?: RequestBodyObject | ReferenceObject;
  responses: Record<string, ResponseObject | ReferenceObject>;
}

/**
 * OpenAPI parameter object
 */
export interface ParameterObject {
  name: string;
  in: 'query' | 'header' | 'path' | 'cookie';
  description?: string;
  required?: boolean;
  schema?: SchemaObject | ReferenceObject;
}

/**
 * OpenAPI schema object
 */
export interface SchemaObject {
  type?: string;
  format?: string;
  properties?: Record<string, SchemaObject | ReferenceObject>;
  items?: SchemaObject | ReferenceObject;
  required?: string[];
  enum?: (string | number | boolean)[];
  description?: string;
  nullable?: boolean;
  allOf?: (SchemaObject | ReferenceObject)[];
  oneOf?: (SchemaObject | ReferenceObject)[];
  anyOf?: (SchemaObject | ReferenceObject)[];
  $ref?: string;
  additionalProperties?: boolean | SchemaObject | ReferenceObject;
}

/**
 * OpenAPI reference object
 */
export interface ReferenceObject {
  $ref: string;
}

/**
 * OpenAPI response object
 */
export interface ResponseObject {
  description: string;
  content?: Record<string, MediaTypeObject>;
}

/**
 * OpenAPI request body object
 */
export interface RequestBodyObject {
  description?: string;
  content: Record<string, MediaTypeObject>;
  required?: boolean;
}

/**
 * OpenAPI media type object
 */
export interface MediaTypeObject {
  schema?: SchemaObject | ReferenceObject;
}

/**
 * CLI options for type generation
 */
export interface TypeGenerationOptions {
  /** URL to fetch OpenAPI spec from */
  url: string;
  /** Output file path */
  output: string;
  /** Whether to include request types */
  includeRequests?: boolean;
  /** Whether to include response types */
  includeResponses?: boolean;
}

/**
 * Parsed collection schema from OpenAPI
 */
export interface ParsedCollection {
  name: string;
  displayName: string;
  fields: ParsedField[];
  operations: string[];
}

/**
 * Parsed field from OpenAPI schema
 */
export interface ParsedField {
  name: string;
  type: string;
  required: boolean;
  description?: string;
  format?: string;
  enum?: (string | number | boolean)[];
  items?: ParsedField;
  properties?: ParsedField[];
}

/**
 * Type generation result
 */
export interface TypeGenerationResult {
  success: boolean;
  typesGenerated: number;
  outputPath: string;
  errors?: string[];
}
