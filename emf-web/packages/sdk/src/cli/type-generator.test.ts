/**
 * Tests for TypeScript type generator
 */

import { describe, it, expect } from 'vitest';
import {
  toInterfaceName,
  generateFieldType,
  generateCollectionInterface,
  generateRequestType,
  generateListResponseType,
  generateCollectionTypes,
  generateFileHeader,
  generateCommonTypes,
  generateTypes,
  generateTypesWithResult,
} from './type-generator';
import type { OpenAPISpec, ParsedCollection, ParsedField } from './types';

describe('Type Generator', () => {
  describe('toInterfaceName', () => {
    it('should convert snake_case to PascalCase', () => {
      expect(toInterfaceName('user_accounts')).toBe('UserAccounts');
    });

    it('should convert kebab-case to PascalCase', () => {
      expect(toInterfaceName('user-accounts')).toBe('UserAccounts');
    });

    it('should capitalize single word', () => {
      expect(toInterfaceName('users')).toBe('Users');
    });

    it('should handle already capitalized names', () => {
      expect(toInterfaceName('User')).toBe('User');
    });
  });

  describe('generateFieldType', () => {
    it('should generate simple types', () => {
      expect(generateFieldType({ name: 'id', type: 'string', required: true })).toBe('string');
      expect(generateFieldType({ name: 'count', type: 'number', required: true })).toBe('number');
      expect(generateFieldType({ name: 'active', type: 'boolean', required: true })).toBe('boolean');
    });

    it('should generate enum types', () => {
      const field: ParsedField = {
        name: 'status',
        type: 'string',
        required: true,
        enum: ['active', 'inactive', 'pending'],
      };
      expect(generateFieldType(field)).toBe("'active' | 'inactive' | 'pending'");
    });

    it('should generate array types', () => {
      const field: ParsedField = {
        name: 'tags',
        type: 'string[]',
        required: true,
        items: { name: 'item', type: 'string', required: true },
      };
      expect(generateFieldType(field)).toBe('string[]');
    });

    it('should generate nested object types', () => {
      const field: ParsedField = {
        name: 'address',
        type: 'object',
        required: true,
        properties: [
          { name: 'street', type: 'string', required: true },
          { name: 'city', type: 'string', required: false },
        ],
      };
      const result = generateFieldType(field);
      expect(result).toContain('street: string;');
      expect(result).toContain('city?: string;');
    });
  });

  describe('generateCollectionInterface', () => {
    it('should generate interface with all fields', () => {
      const collection: ParsedCollection = {
        name: 'users',
        displayName: 'Users',
        fields: [
          { name: 'id', type: 'string', required: true },
          { name: 'name', type: 'string', required: true },
          { name: 'email', type: 'string', required: false },
        ],
        operations: ['get', 'create', 'update', 'delete'],
      };

      const result = generateCollectionInterface(collection);
      expect(result).toContain('export interface Users {');
      expect(result).toContain('id: string;');
      expect(result).toContain('name: string;');
      expect(result).toContain('email?: string;');
      expect(result).toContain('Available operations: get, create, update, delete');
    });

    it('should include field descriptions as JSDoc comments', () => {
      const collection: ParsedCollection = {
        name: 'users',
        displayName: 'Users',
        fields: [
          { name: 'id', type: 'string', required: true, description: 'Unique identifier' },
        ],
        operations: [],
      };

      const result = generateCollectionInterface(collection);
      expect(result).toContain('/** Unique identifier */');
    });
  });

  describe('generateRequestType', () => {
    const collection: ParsedCollection = {
      name: 'users',
      displayName: 'Users',
      fields: [
        { name: 'id', type: 'string', required: true },
        { name: 'name', type: 'string', required: true },
        { name: 'email', type: 'string', required: false },
      ],
      operations: ['create', 'update'],
    };

    it('should generate create request type without id', () => {
      const result = generateRequestType(collection, 'create');
      expect(result).toContain('export interface UsersCreateRequest {');
      expect(result).not.toContain('id:');
      expect(result).toContain('name: string;');
      expect(result).toContain('email?: string;');
    });

    it('should generate update request type with all fields optional except id', () => {
      const result = generateRequestType(collection, 'update');
      expect(result).toContain('export interface UsersUpdateRequest {');
      expect(result).toContain('id: string;');
      expect(result).toContain('name?: string;');
      expect(result).toContain('email?: string;');
    });
  });

  describe('generateListResponseType', () => {
    it('should generate list response type with pagination', () => {
      const collection: ParsedCollection = {
        name: 'users',
        displayName: 'Users',
        fields: [{ name: 'id', type: 'string', required: true }],
        operations: ['get'],
      };

      const result = generateListResponseType(collection);
      expect(result).toContain('export interface UsersListResponse {');
      expect(result).toContain('data: Users[];');
      expect(result).toContain('pagination: {');
      expect(result).toContain('page: number;');
      expect(result).toContain('size: number;');
      expect(result).toContain('total: number;');
      expect(result).toContain('totalPages: number;');
    });
  });

  describe('generateCollectionTypes', () => {
    it('should generate all types for a collection', () => {
      const collection: ParsedCollection = {
        name: 'users',
        displayName: 'Users',
        fields: [
          { name: 'id', type: 'string', required: true },
          { name: 'name', type: 'string', required: true },
        ],
        operations: ['get', 'create', 'update'],
      };

      const result = generateCollectionTypes(collection);
      expect(result).toContain('export interface Users {');
      expect(result).toContain('export interface UsersCreateRequest {');
      expect(result).toContain('export interface UsersUpdateRequest {');
      expect(result).toContain('export interface UsersListResponse {');
    });

    it('should skip request types when includeRequests is false', () => {
      const collection: ParsedCollection = {
        name: 'users',
        displayName: 'Users',
        fields: [{ name: 'id', type: 'string', required: true }],
        operations: ['create'],
      };

      const result = generateCollectionTypes(collection, false, true);
      expect(result).toContain('export interface Users {');
      expect(result).not.toContain('CreateRequest');
    });

    it('should skip response types when includeResponses is false', () => {
      const collection: ParsedCollection = {
        name: 'users',
        displayName: 'Users',
        fields: [{ name: 'id', type: 'string', required: true }],
        operations: ['get'],
      };

      const result = generateCollectionTypes(collection, true, false);
      expect(result).toContain('export interface Users {');
      expect(result).not.toContain('ListResponse');
    });
  });

  describe('generateFileHeader', () => {
    it('should generate header with API info', () => {
      const spec: OpenAPISpec = {
        openapi: '3.0.0',
        info: {
          title: 'Test API',
          version: '1.0.0',
          description: 'A test API',
        },
        paths: {},
      };

      const result = generateFileHeader(spec);
      expect(result).toContain('Auto-generated TypeScript types');
      expect(result).toContain('API: Test API');
      expect(result).toContain('Version: 1.0.0');
      expect(result).toContain('Description: A test API');
      expect(result).toContain('DO NOT EDIT');
    });
  });

  describe('generateCommonTypes', () => {
    it('should generate common utility types', () => {
      const result = generateCommonTypes();
      expect(result).toContain('export interface PaginationMeta {');
      expect(result).toContain('export interface ListResponse<T> {');
      expect(result).toContain('export interface SortCriteria {');
      expect(result).toContain('export interface FilterExpression {');
    });
  });

  describe('generateTypes', () => {
    it('should generate complete types file from OpenAPI spec', () => {
      const spec: OpenAPISpec = {
        openapi: '3.0.0',
        info: { title: 'Test API', version: '1.0.0' },
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
          },
        },
      };

      const result = generateTypes(spec);
      expect(result).toContain('Auto-generated TypeScript types');
      expect(result).toContain('export interface PaginationMeta');
      expect(result).toContain('export interface Users {');
    });
  });

  describe('generateTypesWithResult', () => {
    it('should return success result for valid spec', () => {
      const spec: OpenAPISpec = {
        openapi: '3.0.0',
        info: { title: 'Test API', version: '1.0.0' },
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
                              },
                            },
                          },
                        },
                      },
                    },
                  },
                },
              },
            },
          },
        },
      };

      const { content, result } = generateTypesWithResult(spec, './output.ts');
      expect(result.success).toBe(true);
      expect(result.typesGenerated).toBeGreaterThan(0);
      expect(result.outputPath).toBe('./output.ts');
      expect(content).toContain('export interface Users');
    });

    it('should return error result for invalid spec', () => {
      const { content, result } = generateTypesWithResult(null, './output.ts');
      expect(result.success).toBe(false);
      expect(result.errors).toBeDefined();
      expect(result.errors!.length).toBeGreaterThan(0);
      expect(content).toBe('');
    });

    it('should return error result for unsupported OpenAPI version', () => {
      const spec = {
        openapi: '2.0',
        info: { title: 'Test', version: '1.0.0' },
        paths: {},
      };

      const { result } = generateTypesWithResult(spec, './output.ts');
      expect(result.success).toBe(false);
      expect(result.errors?.some((e) => e.includes('Unsupported OpenAPI version'))).toBe(true);
    });
  });
});
