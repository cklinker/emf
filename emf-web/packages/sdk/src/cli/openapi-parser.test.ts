/**
 * Tests for OpenAPI parser
 */

import { describe, it, expect } from 'vitest';
import {
  isReferenceObject,
  resolveReference,
  getSchema,
  mapOpenAPITypeToTS,
  parseSchemaToField,
  extractCollectionName,
  getOperations,
  toDisplayName,
  parseCollections,
  validateOpenAPISpec,
} from './openapi-parser';
import type { OpenAPISpec, SchemaObject, PathItem } from './types';

describe('OpenAPI Parser', () => {
  describe('isReferenceObject', () => {
    it('should return true for reference objects', () => {
      expect(isReferenceObject({ $ref: '#/components/schemas/User' })).toBe(true);
    });

    it('should return false for non-reference objects', () => {
      expect(isReferenceObject({ type: 'string' })).toBe(false);
      expect(isReferenceObject(null)).toBe(false);
      expect(isReferenceObject(undefined)).toBe(false);
      expect(isReferenceObject('string')).toBe(false);
    });
  });

  describe('resolveReference', () => {
    const spec: OpenAPISpec = {
      openapi: '3.0.0',
      info: { title: 'Test', version: '1.0.0' },
      paths: {},
      components: {
        schemas: {
          User: { type: 'object', properties: { id: { type: 'string' } } },
        },
      },
    };

    it('should resolve valid schema references', () => {
      const result = resolveReference('#/components/schemas/User', spec);
      expect(result).toEqual({ type: 'object', properties: { id: { type: 'string' } } });
    });

    it('should return undefined for invalid references', () => {
      expect(resolveReference('#/invalid/path', spec)).toBeUndefined();
      expect(resolveReference('#/components/schemas/NonExistent', spec)).toBeUndefined();
    });
  });

  describe('getSchema', () => {
    const spec: OpenAPISpec = {
      openapi: '3.0.0',
      info: { title: 'Test', version: '1.0.0' },
      paths: {},
      components: {
        schemas: {
          User: { type: 'object', properties: { id: { type: 'string' } } },
        },
      },
    };

    it('should return schema directly if not a reference', () => {
      const schema: SchemaObject = { type: 'string' };
      expect(getSchema(schema, spec)).toEqual(schema);
    });

    it('should resolve reference and return schema', () => {
      const ref = { $ref: '#/components/schemas/User' };
      expect(getSchema(ref, spec)).toEqual({ type: 'object', properties: { id: { type: 'string' } } });
    });

    it('should return undefined for undefined input', () => {
      expect(getSchema(undefined, spec)).toBeUndefined();
    });
  });

  describe('mapOpenAPITypeToTS', () => {
    const spec: OpenAPISpec = {
      openapi: '3.0.0',
      info: { title: 'Test', version: '1.0.0' },
      paths: {},
    };

    it('should map string type', () => {
      expect(mapOpenAPITypeToTS({ type: 'string' }, spec)).toBe('string');
    });

    it('should map number types', () => {
      expect(mapOpenAPITypeToTS({ type: 'number' }, spec)).toBe('number');
      expect(mapOpenAPITypeToTS({ type: 'integer' }, spec)).toBe('number');
    });

    it('should map boolean type', () => {
      expect(mapOpenAPITypeToTS({ type: 'boolean' }, spec)).toBe('boolean');
    });

    it('should map array type', () => {
      expect(mapOpenAPITypeToTS({ type: 'array', items: { type: 'string' } }, spec)).toBe('string[]');
    });

    it('should map object type with additionalProperties', () => {
      expect(mapOpenAPITypeToTS({ type: 'object', additionalProperties: true }, spec)).toBe('Record<string, unknown>');
      expect(mapOpenAPITypeToTS({ type: 'object', additionalProperties: { type: 'string' } }, spec)).toBe('Record<string, string>');
    });

    it('should map enum type', () => {
      expect(mapOpenAPITypeToTS({ enum: ['active', 'inactive'] }, spec)).toBe("'active' | 'inactive'");
      expect(mapOpenAPITypeToTS({ enum: [1, 2, 3] }, spec)).toBe('1 | 2 | 3');
    });

    it('should return unknown for unrecognized types', () => {
      expect(mapOpenAPITypeToTS({}, spec)).toBe('unknown');
    });
  });

  describe('parseSchemaToField', () => {
    const spec: OpenAPISpec = {
      openapi: '3.0.0',
      info: { title: 'Test', version: '1.0.0' },
      paths: {},
    };

    it('should parse simple string field', () => {
      const field = parseSchemaToField('name', { type: 'string', description: 'User name' }, spec, true);
      expect(field).toEqual({
        name: 'name',
        type: 'string',
        required: true,
        description: 'User name',
        format: undefined,
      });
    });

    it('should parse field with enum', () => {
      const field = parseSchemaToField('status', { type: 'string', enum: ['active', 'inactive'] }, spec, false);
      expect(field.enum).toEqual(['active', 'inactive']);
    });

    it('should parse array field with items', () => {
      const field = parseSchemaToField('tags', { type: 'array', items: { type: 'string' } }, spec, false);
      expect(field.type).toBe('string[]');
      expect(field.items).toBeDefined();
    });

    it('should parse object field with properties', () => {
      const field = parseSchemaToField(
        'address',
        {
          type: 'object',
          properties: {
            street: { type: 'string' },
            city: { type: 'string' },
          },
          required: ['street'],
        },
        spec,
        true
      );
      expect(field.properties).toHaveLength(2);
      expect(field.properties?.[0].required).toBe(true);
      expect(field.properties?.[1].required).toBe(false);
    });
  });

  describe('extractCollectionName', () => {
    it('should extract collection name from simple path', () => {
      expect(extractCollectionName('/api/users')).toBe('users');
      expect(extractCollectionName('/api/orders')).toBe('orders');
    });

    it('should extract collection name from path with id parameter', () => {
      expect(extractCollectionName('/api/users/{id}')).toBe('users');
      expect(extractCollectionName('/api/orders/{orderId}')).toBe('orders');
    });

    it('should return null for non-matching paths', () => {
      expect(extractCollectionName('/api/_admin/collections')).toBeNull();
      expect(extractCollectionName('/health')).toBeNull();
      expect(extractCollectionName('/api')).toBeNull();
    });
  });

  describe('getOperations', () => {
    it('should return all operations from path item', () => {
      const pathItem: PathItem = {
        get: { responses: {} },
        post: { responses: {} },
        put: { responses: {} },
        patch: { responses: {} },
        delete: { responses: {} },
      };
      expect(getOperations(pathItem)).toEqual(['get', 'create', 'update', 'patch', 'delete']);
    });

    it('should return only defined operations', () => {
      const pathItem: PathItem = {
        get: { responses: {} },
      };
      expect(getOperations(pathItem)).toEqual(['get']);
    });

    it('should return empty array for empty path item', () => {
      expect(getOperations({})).toEqual([]);
    });
  });

  describe('toDisplayName', () => {
    it('should convert snake_case to Title Case', () => {
      expect(toDisplayName('user_accounts')).toBe('User Accounts');
    });

    it('should convert kebab-case to Title Case', () => {
      expect(toDisplayName('user-accounts')).toBe('User Accounts');
    });

    it('should convert camelCase to Title Case', () => {
      expect(toDisplayName('userAccounts')).toBe('User Accounts');
    });

    it('should handle single word', () => {
      expect(toDisplayName('users')).toBe('Users');
    });
  });

  describe('parseCollections', () => {
    it('should parse collections from paths', () => {
      const spec: OpenAPISpec = {
        openapi: '3.0.0',
        info: { title: 'Test', version: '1.0.0' },
        paths: {
          '/api/users': {
            get: {
              responses: {
                '200': {
                  description: 'Success',
                  content: {
                    'application/json': {
                      schema: {
                        type: 'object',
                        properties: {
                          data: {
                            type: 'array',
                            items: {
                              type: 'object',
                              properties: {
                                id: { type: 'string' },
                                name: { type: 'string' },
                              },
                              required: ['id'],
                            },
                          },
                        },
                      },
                    },
                  },
                },
              },
            },
            post: {
              requestBody: {
                content: {
                  'application/json': {
                    schema: {
                      type: 'object',
                      properties: {
                        name: { type: 'string' },
                        email: { type: 'string' },
                      },
                      required: ['name'],
                    },
                  },
                },
              },
              responses: {},
            },
          },
        },
      };

      const collections = parseCollections(spec);
      expect(collections).toHaveLength(1);
      expect(collections[0].name).toBe('users');
      expect(collections[0].displayName).toBe('Users');
      expect(collections[0].operations).toContain('get');
      expect(collections[0].operations).toContain('create');
    });

    it('should skip admin and meta endpoints', () => {
      const spec: OpenAPISpec = {
        openapi: '3.0.0',
        info: { title: 'Test', version: '1.0.0' },
        paths: {
          '/api/_admin/collections': { get: { responses: {} } },
          '/api/_meta/resources': { get: { responses: {} } },
          '/api/users': { get: { responses: {} } },
        },
      };

      const collections = parseCollections(spec);
      expect(collections).toHaveLength(1);
      expect(collections[0].name).toBe('users');
    });
  });

  describe('validateOpenAPISpec', () => {
    it('should validate a valid spec', () => {
      const spec = {
        openapi: '3.0.0',
        info: { title: 'Test API', version: '1.0.0' },
        paths: {},
      };
      const result = validateOpenAPISpec(spec);
      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('should reject non-object spec', () => {
      const result = validateOpenAPISpec(null);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain('OpenAPI spec must be an object');
    });

    it('should reject missing openapi version', () => {
      const spec = {
        info: { title: 'Test', version: '1.0.0' },
        paths: {},
      };
      const result = validateOpenAPISpec(spec);
      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('openapi'))).toBe(true);
    });

    it('should reject unsupported OpenAPI version', () => {
      const spec = {
        openapi: '2.0',
        info: { title: 'Test', version: '1.0.0' },
        paths: {},
      };
      const result = validateOpenAPISpec(spec);
      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('Unsupported OpenAPI version'))).toBe(true);
    });

    it('should reject missing info object', () => {
      const spec = {
        openapi: '3.0.0',
        paths: {},
      };
      const result = validateOpenAPISpec(spec);
      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('info'))).toBe(true);
    });

    it('should reject missing paths object', () => {
      const spec = {
        openapi: '3.0.0',
        info: { title: 'Test', version: '1.0.0' },
      };
      const result = validateOpenAPISpec(spec);
      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('paths'))).toBe(true);
    });
  });
});
