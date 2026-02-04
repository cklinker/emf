/**
 * TypeScript type generator from OpenAPI specifications
 */

import type { OpenAPISpec, ParsedCollection, ParsedField, TypeGenerationResult } from './types';
import { parseCollections, validateOpenAPISpec } from './openapi-parser';

/**
 * Generate TypeScript interface name from collection name
 */
export function toInterfaceName(name: string): string {
  return name
    .split(/[-_]/)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join('');
}

/**
 * Generate TypeScript type for a parsed field
 */
export function generateFieldType(field: ParsedField, indent: string = ''): string {
  if (field.enum) {
    return field.enum.map((v) => (typeof v === 'string' ? `'${v}'` : String(v))).join(' | ');
  }

  if (field.properties && field.properties.length > 0) {
    const props = field.properties
      .map((prop) => {
        const optional = prop.required ? '' : '?';
        const propType = generateFieldType(prop, indent + '  ');
        const comment = prop.description ? `${indent}  /** ${prop.description} */\n` : '';
        return `${comment}${indent}  ${prop.name}${optional}: ${propType};`;
      })
      .join('\n');
    return `{\n${props}\n${indent}}`;
  }

  if (field.items) {
    const itemType = generateFieldType(field.items, indent);
    return `${itemType}[]`;
  }

  return field.type;
}

/**
 * Generate TypeScript interface for a collection
 */
export function generateCollectionInterface(collection: ParsedCollection): string {
  const interfaceName = toInterfaceName(collection.name);
  const lines: string[] = [];

  // Add JSDoc comment
  lines.push(`/**`);
  lines.push(` * ${collection.displayName} entity`);
  if (collection.operations.length > 0) {
    lines.push(` * Available operations: ${collection.operations.join(', ')}`);
  }
  lines.push(` */`);

  // Generate interface
  lines.push(`export interface ${interfaceName} {`);

  for (const field of collection.fields) {
    const optional = field.required ? '' : '?';
    const fieldType = generateFieldType(field, '');

    // Add field comment if description exists
    if (field.description) {
      lines.push(`  /** ${field.description} */`);
    }

    lines.push(`  ${field.name}${optional}: ${fieldType};`);
  }

  lines.push(`}`);

  return lines.join('\n');
}

/**
 * Generate request type for create/update operations
 */
export function generateRequestType(collection: ParsedCollection, operation: 'create' | 'update'): string {
  const interfaceName = toInterfaceName(collection.name);
  const typeName = `${interfaceName}${operation === 'create' ? 'Create' : 'Update'}Request`;
  const lines: string[] = [];

  lines.push(`/**`);
  lines.push(` * Request type for ${operation === 'create' ? 'creating' : 'updating'} a ${collection.displayName}`);
  lines.push(` */`);

  if (operation === 'create') {
    // For create, exclude id and make required fields required
    const fields = collection.fields.filter((f) => f.name !== 'id');
    lines.push(`export interface ${typeName} {`);
    for (const field of fields) {
      const optional = field.required ? '' : '?';
      const fieldType = generateFieldType(field, '');
      if (field.description) {
        lines.push(`  /** ${field.description} */`);
      }
      lines.push(`  ${field.name}${optional}: ${fieldType};`);
    }
    lines.push(`}`);
  } else {
    // For update, all fields are optional except id
    lines.push(`export interface ${typeName} {`);
    for (const field of collection.fields) {
      const optional = field.name === 'id' ? '' : '?';
      const fieldType = generateFieldType(field, '');
      if (field.description) {
        lines.push(`  /** ${field.description} */`);
      }
      lines.push(`  ${field.name}${optional}: ${fieldType};`);
    }
    lines.push(`}`);
  }

  return lines.join('\n');
}

/**
 * Generate list response type for a collection
 */
export function generateListResponseType(collection: ParsedCollection): string {
  const interfaceName = toInterfaceName(collection.name);
  const typeName = `${interfaceName}ListResponse`;

  return `/**
 * List response for ${collection.displayName}
 */
export interface ${typeName} {
  data: ${interfaceName}[];
  pagination: {
    page: number;
    size: number;
    total: number;
    totalPages: number;
  };
}`;
}

/**
 * Generate all types for a collection
 */
export function generateCollectionTypes(
  collection: ParsedCollection,
  includeRequests: boolean = true,
  includeResponses: boolean = true
): string {
  const types: string[] = [];

  // Main interface
  types.push(generateCollectionInterface(collection));

  // Request types
  if (includeRequests) {
    if (collection.operations.includes('create')) {
      types.push(generateRequestType(collection, 'create'));
    }
    if (collection.operations.includes('update') || collection.operations.includes('patch')) {
      types.push(generateRequestType(collection, 'update'));
    }
  }

  // Response types
  if (includeResponses) {
    if (collection.operations.includes('get') || collection.operations.length > 0) {
      types.push(generateListResponseType(collection));
    }
  }

  return types.join('\n\n');
}

/**
 * Generate file header with metadata
 */
export function generateFileHeader(spec: OpenAPISpec): string {
  return `/**
 * Auto-generated TypeScript types from OpenAPI specification
 * 
 * API: ${spec.info.title}
 * Version: ${spec.info.version}
 * ${spec.info.description ? `Description: ${spec.info.description}` : ''}
 * 
 * DO NOT EDIT - This file is auto-generated
 * Regenerate with: npx @emf/sdk generate-types
 */

/* eslint-disable */
/* tslint:disable */
`;
}

/**
 * Generate common utility types
 */
export function generateCommonTypes(): string {
  return `/**
 * Pagination metadata
 */
export interface PaginationMeta {
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

/**
 * Generic list response wrapper
 */
export interface ListResponse<T> {
  data: T[];
  pagination: PaginationMeta;
}

/**
 * Sort criteria for list operations
 */
export interface SortCriteria {
  field: string;
  direction: 'asc' | 'desc';
}

/**
 * Filter expression for list operations
 */
export interface FilterExpression {
  field: string;
  operator: 'eq' | 'ne' | 'gt' | 'gte' | 'lt' | 'lte' | 'contains' | 'startsWith' | 'endsWith' | 'in';
  value: unknown;
}
`;
}

/**
 * Generate all types from an OpenAPI spec
 */
export function generateTypes(
  spec: OpenAPISpec,
  options: { includeRequests?: boolean; includeResponses?: boolean } = {}
): string {
  const { includeRequests = true, includeResponses = true } = options;

  const collections = parseCollections(spec);
  const parts: string[] = [];

  // File header
  parts.push(generateFileHeader(spec));

  // Common types
  parts.push(generateCommonTypes());

  // Collection types
  for (const collection of collections) {
    if (collection.fields.length > 0) {
      parts.push(generateCollectionTypes(collection, includeRequests, includeResponses));
    }
  }

  return parts.join('\n\n');
}

/**
 * Generate types and return result with metadata
 */
export function generateTypesWithResult(
  spec: unknown,
  outputPath: string,
  options: { includeRequests?: boolean; includeResponses?: boolean } = {}
): { content: string; result: TypeGenerationResult } {
  // Validate spec
  const validation = validateOpenAPISpec(spec);
  if (!validation.valid) {
    return {
      content: '',
      result: {
        success: false,
        typesGenerated: 0,
        outputPath,
        errors: validation.errors,
      },
    };
  }

  const openAPISpec = spec as OpenAPISpec;
  const collections = parseCollections(openAPISpec);
  const content = generateTypes(openAPISpec, options);

  // Count generated types
  let typesGenerated = 0;
  for (const collection of collections) {
    if (collection.fields.length > 0) {
      typesGenerated++; // Main interface
      if (options.includeRequests !== false) {
        if (collection.operations.includes('create')) typesGenerated++;
        if (collection.operations.includes('update') || collection.operations.includes('patch')) typesGenerated++;
      }
      if (options.includeResponses !== false) {
        typesGenerated++; // List response
      }
    }
  }

  // Add common types count
  typesGenerated += 4; // PaginationMeta, ListResponse, SortCriteria, FilterExpression

  return {
    content,
    result: {
      success: true,
      typesGenerated,
      outputPath,
    },
  };
}
