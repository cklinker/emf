/**
 * OpenAPI specification parser for extracting collection schemas
 */

import type {
  OpenAPISpec,
  SchemaObject,
  ReferenceObject,
  ParsedCollection,
  ParsedField,
  PathItem,
} from './types';

/**
 * Check if an object is a reference object
 */
export function isReferenceObject(obj: unknown): obj is ReferenceObject {
  return typeof obj === 'object' && obj !== null && '$ref' in obj;
}

/**
 * Resolve a reference to its schema
 */
export function resolveReference(
  ref: string,
  spec: OpenAPISpec
): SchemaObject | undefined {
  // Reference format: #/components/schemas/SchemaName
  const parts = ref.split('/');
  if (parts[0] !== '#' || parts[1] !== 'components' || parts[2] !== 'schemas') {
    return undefined;
  }

  const schemaName = parts[3];
  return spec.components?.schemas?.[schemaName];
}

/**
 * Get the schema, resolving references if needed
 */
export function getSchema(
  schemaOrRef: SchemaObject | ReferenceObject | undefined,
  spec: OpenAPISpec
): SchemaObject | undefined {
  if (!schemaOrRef) {
    return undefined;
  }

  if (isReferenceObject(schemaOrRef)) {
    return resolveReference(schemaOrRef.$ref, spec);
  }

  return schemaOrRef;
}

/**
 * Map OpenAPI type to TypeScript type
 */
export function mapOpenAPITypeToTS(
  schema: SchemaObject,
  spec: OpenAPISpec
): string {
  if (schema.$ref) {
    const refParts = schema.$ref.split('/');
    return refParts[refParts.length - 1];
  }

  if (schema.enum) {
    return schema.enum.map((v) => (typeof v === 'string' ? `'${v}'` : String(v))).join(' | ');
  }

  if (schema.allOf || schema.oneOf || schema.anyOf) {
    const schemas = schema.allOf || schema.oneOf || schema.anyOf || [];
    const types = schemas.map((s) => {
      const resolved = getSchema(s, spec);
      return resolved ? mapOpenAPITypeToTS(resolved, spec) : 'unknown';
    });
    const operator = schema.allOf ? ' & ' : ' | ';
    return types.join(operator);
  }

  switch (schema.type) {
    case 'string':
      if (schema.format === 'date' || schema.format === 'date-time') {
        return 'string'; // Could be Date, but string is safer for JSON
      }
      return 'string';
    case 'number':
    case 'integer':
      return 'number';
    case 'boolean':
      return 'boolean';
    case 'array':
      if (schema.items) {
        const itemSchema = getSchema(schema.items, spec);
        const itemType = itemSchema ? mapOpenAPITypeToTS(itemSchema, spec) : 'unknown';
        return `${itemType}[]`;
      }
      return 'unknown[]';
    case 'object':
      if (schema.additionalProperties) {
        if (typeof schema.additionalProperties === 'boolean') {
          return 'Record<string, unknown>';
        }
        const valueSchema = getSchema(schema.additionalProperties, spec);
        const valueType = valueSchema ? mapOpenAPITypeToTS(valueSchema, spec) : 'unknown';
        return `Record<string, ${valueType}>`;
      }
      if (schema.properties) {
        return 'object'; // Will be expanded inline
      }
      return 'Record<string, unknown>';
    default:
      return 'unknown';
  }
}

/**
 * Parse a schema object into a parsed field
 */
export function parseSchemaToField(
  name: string,
  schema: SchemaObject | ReferenceObject,
  spec: OpenAPISpec,
  required: boolean
): ParsedField {
  const resolvedSchema = getSchema(schema, spec);

  if (!resolvedSchema) {
    return {
      name,
      type: 'unknown',
      required,
    };
  }

  const field: ParsedField = {
    name,
    type: mapOpenAPITypeToTS(resolvedSchema, spec),
    required,
    description: resolvedSchema.description,
    format: resolvedSchema.format,
  };

  if (resolvedSchema.enum) {
    field.enum = resolvedSchema.enum;
  }

  if (resolvedSchema.type === 'array' && resolvedSchema.items) {
    const itemSchema = getSchema(resolvedSchema.items, spec);
    if (itemSchema) {
      field.items = parseSchemaToField('item', itemSchema, spec, true);
    }
  }

  if (resolvedSchema.type === 'object' && resolvedSchema.properties) {
    field.properties = Object.entries(resolvedSchema.properties).map(([propName, propSchema]) =>
      parseSchemaToField(
        propName,
        propSchema,
        spec,
        resolvedSchema.required?.includes(propName) ?? false
      )
    );
  }

  return field;
}

/**
 * Extract collection name from path
 * e.g., /api/users -> users, /api/orders/{id} -> orders
 */
export function extractCollectionName(path: string): string | null {
  // Match /api/{collectionName} or /api/{collectionName}/{id}
  const match = path.match(/^\/api\/([a-zA-Z][a-zA-Z0-9_-]*)(?:\/\{[^}]+\})?$/);
  if (match) {
    return match[1];
  }
  return null;
}

/**
 * Determine operations available for a path item
 */
export function getOperations(pathItem: PathItem): string[] {
  const operations: string[] = [];
  if (pathItem.get) operations.push('get');
  if (pathItem.post) operations.push('create');
  if (pathItem.put) operations.push('update');
  if (pathItem.patch) operations.push('patch');
  if (pathItem.delete) operations.push('delete');
  return operations;
}

/**
 * Convert collection name to display name
 * e.g., user_accounts -> User Accounts
 */
export function toDisplayName(name: string): string {
  return name
    .replace(/[-_]/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .split(' ')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ');
}

/**
 * Parse OpenAPI spec to extract collection schemas
 */
export function parseCollections(spec: OpenAPISpec): ParsedCollection[] {
  const collections = new Map<string, ParsedCollection>();

  // First, extract collections from paths
  for (const [path, pathItem] of Object.entries(spec.paths)) {
    const collectionName = extractCollectionName(path);
    if (!collectionName) {
      continue;
    }

    // Skip admin and meta endpoints
    if (path.includes('/_admin') || path.includes('/_meta')) {
      continue;
    }

    if (!collections.has(collectionName)) {
      collections.set(collectionName, {
        name: collectionName,
        displayName: toDisplayName(collectionName),
        fields: [],
        operations: [],
      });
    }

    const collection = collections.get(collectionName)!;
    const operations = getOperations(pathItem);
    collection.operations = [...new Set([...collection.operations, ...operations])];

    // Try to extract schema from request body or response
    const postOp = pathItem.post;
    const getOp = pathItem.get;

    // Try to get schema from POST request body
    if (postOp?.requestBody && !isReferenceObject(postOp.requestBody)) {
      const content = postOp.requestBody.content?.['application/json'];
      if (content?.schema) {
        const schema = getSchema(content.schema, spec);
        if (schema?.properties) {
          collection.fields = Object.entries(schema.properties).map(([name, propSchema]) =>
            parseSchemaToField(name, propSchema, spec, schema.required?.includes(name) ?? false)
          );
        }
      }
    }

    // If no fields from POST, try GET response
    if (collection.fields.length === 0 && getOp?.responses?.['200']) {
      const response = getOp.responses['200'];
      if (!isReferenceObject(response)) {
        const content = response.content?.['application/json'];
        if (content?.schema) {
          const schema = getSchema(content.schema, spec);
          // Handle list response with data array
          if (schema?.properties?.data) {
            const dataSchema = getSchema(schema.properties.data, spec);
            if (dataSchema?.type === 'array' && dataSchema.items) {
              const itemSchema = getSchema(dataSchema.items, spec);
              if (itemSchema?.properties) {
                collection.fields = Object.entries(itemSchema.properties).map(
                  ([name, propSchema]) =>
                    parseSchemaToField(
                      name,
                      propSchema,
                      spec,
                      itemSchema.required?.includes(name) ?? false
                    )
                );
              }
            }
          }
          // Handle single item response
          else if (schema?.properties) {
            collection.fields = Object.entries(schema.properties).map(([name, propSchema]) =>
              parseSchemaToField(name, propSchema, spec, schema.required?.includes(name) ?? false)
            );
          }
        }
      }
    }
  }

  // Also extract from component schemas that look like collections
  if (spec.components?.schemas) {
    for (const [schemaName, schema] of Object.entries(spec.components.schemas)) {
      // Skip if already have this collection from paths
      const collectionName = schemaName.toLowerCase();
      if (collections.has(collectionName)) {
        continue;
      }

      // Skip common non-collection schemas
      if (
        schemaName.endsWith('Request') ||
        schemaName.endsWith('Response') ||
        schemaName.endsWith('Error') ||
        schemaName.endsWith('Dto') ||
        schemaName === 'Pagination'
      ) {
        continue;
      }

      if (schema.type === 'object' && schema.properties) {
        collections.set(collectionName, {
          name: collectionName,
          displayName: toDisplayName(schemaName),
          fields: Object.entries(schema.properties).map(([name, propSchema]) =>
            parseSchemaToField(name, propSchema, spec, schema.required?.includes(name) ?? false)
          ),
          operations: [],
        });
      }
    }
  }

  return Array.from(collections.values());
}

/**
 * Validate OpenAPI spec structure
 */
export function validateOpenAPISpec(spec: unknown): { valid: boolean; errors: string[] } {
  const errors: string[] = [];

  if (!spec || typeof spec !== 'object') {
    errors.push('OpenAPI spec must be an object');
    return { valid: false, errors };
  }

  const s = spec as Record<string, unknown>;

  if (!s.openapi || typeof s.openapi !== 'string') {
    errors.push('Missing or invalid "openapi" version field');
  } else if (!s.openapi.startsWith('3.')) {
    errors.push(`Unsupported OpenAPI version: ${s.openapi}. Only OpenAPI 3.x is supported.`);
  }

  if (!s.info || typeof s.info !== 'object') {
    errors.push('Missing or invalid "info" object');
  } else {
    const info = s.info as Record<string, unknown>;
    if (!info.title || typeof info.title !== 'string') {
      errors.push('Missing or invalid "info.title"');
    }
    if (!info.version || typeof info.version !== 'string') {
      errors.push('Missing or invalid "info.version"');
    }
  }

  if (!s.paths || typeof s.paths !== 'object') {
    errors.push('Missing or invalid "paths" object');
  }

  return { valid: errors.length === 0, errors };
}
