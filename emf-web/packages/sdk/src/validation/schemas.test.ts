import { describe, it, expect } from 'vitest';
import { z } from 'zod';
import {
  FieldDefinitionSchema,
  AuthzConfigSchema,
  ResourceMetadataSchema,
  PaginationSchema,
  ListResponseSchema,
  ErrorResponseSchema,
  DiscoveryResponseSchema,
} from './schemas';

describe('Validation Schemas - Requirement 10.1', () => {
  describe('FieldDefinitionSchema', () => {
    it('should validate a minimal field definition', () => {
      const field = {
        name: 'email',
        type: 'string',
      };

      const result = FieldDefinitionSchema.safeParse(field);

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.name).toBe('email');
        expect(result.data.type).toBe('string');
      }
    });

    it('should validate a complete field definition with all optional fields', () => {
      const field = {
        name: 'age',
        type: 'number',
        displayName: 'User Age',
        required: true,
        unique: false,
        validation: [
          { type: 'min', value: 0, message: 'Age must be positive' },
          { type: 'max', value: 150, message: 'Age must be realistic' },
        ],
        defaultValue: 18,
        referenceTarget: undefined,
      };

      const result = FieldDefinitionSchema.safeParse(field);

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.displayName).toBe('User Age');
        expect(result.data.required).toBe(true);
        expect(result.data.validation).toHaveLength(2);
      }
    });

    it('should validate all supported field types', () => {
      const fieldTypes = ['string', 'number', 'boolean', 'date', 'datetime', 'json', 'reference'];

      for (const type of fieldTypes) {
        const field = { name: 'testField', type };
        const result = FieldDefinitionSchema.safeParse(field);

        expect(result.success).toBe(true);
        if (result.success) {
          expect(result.data.type).toBe(type);
        }
      }
    });

    it('should reject invalid field types', () => {
      const field = {
        name: 'invalid',
        type: 'unsupported',
      };

      const result = FieldDefinitionSchema.safeParse(field);

      expect(result.success).toBe(false);
    });

    it('should reject missing required fields', () => {
      const fieldWithoutName = { type: 'string' };
      const fieldWithoutType = { name: 'test' };

      expect(FieldDefinitionSchema.safeParse(fieldWithoutName).success).toBe(false);
      expect(FieldDefinitionSchema.safeParse(fieldWithoutType).success).toBe(false);
    });

    it('should validate all supported validation rule types', () => {
      const validationTypes = ['min', 'max', 'pattern', 'email', 'url', 'custom'];

      for (const validationType of validationTypes) {
        const field = {
          name: 'test',
          type: 'string',
          validation: [{ type: validationType }],
        };
        const result = FieldDefinitionSchema.safeParse(field);

        expect(result.success).toBe(true);
      }
    });

    it('should validate reference field with referenceTarget', () => {
      const field = {
        name: 'userId',
        type: 'reference',
        referenceTarget: 'users',
      };

      const result = FieldDefinitionSchema.safeParse(field);

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.referenceTarget).toBe('users');
      }
    });
  });

  describe('AuthzConfigSchema', () => {
    it('should validate an empty authz config', () => {
      const authz = {};

      const result = AuthzConfigSchema.safeParse(authz);

      expect(result.success).toBe(true);
    });

    it('should validate a complete authz config', () => {
      const authz = {
        read: ['user', 'admin'],
        create: ['admin'],
        update: ['admin'],
        delete: ['admin'],
        fieldLevel: {
          salary: ['hr', 'admin'],
          ssn: ['admin'],
        },
      };

      const result = AuthzConfigSchema.safeParse(authz);

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.read).toEqual(['user', 'admin']);
        expect(result.data.fieldLevel?.salary).toEqual(['hr', 'admin']);
      }
    });

    it('should validate partial authz config', () => {
      const authz = {
        read: ['public'],
      };

      const result = AuthzConfigSchema.safeParse(authz);

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.read).toEqual(['public']);
        expect(result.data.create).toBeUndefined();
      }
    });

    it('should reject invalid role arrays', () => {
      const authz = {
        read: 'admin', // Should be an array
      };

      const result = AuthzConfigSchema.safeParse(authz);

      expect(result.success).toBe(false);
    });
  });

  describe('ResourceMetadataSchema', () => {
    it('should validate a minimal resource metadata', () => {
      const metadata = {
        name: 'users',
        displayName: 'Users',
        fields: [],
        operations: ['list', 'get'],
      };

      const result = ResourceMetadataSchema.safeParse(metadata);

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.name).toBe('users');
        expect(result.data.displayName).toBe('Users');
      }
    });

    it('should validate a complete resource metadata with fields and authz', () => {
      const metadata = {
        name: 'orders',
        displayName: 'Orders',
        fields: [
          { name: 'id', type: 'string', required: true, unique: true },
          { name: 'total', type: 'number', required: true },
          { name: 'createdAt', type: 'datetime' },
        ],
        operations: ['list', 'get', 'create', 'update', 'delete'],
        authz: {
          read: ['user', 'admin'],
          create: ['user', 'admin'],
          update: ['admin'],
          delete: ['admin'],
        },
      };

      const result = ResourceMetadataSchema.safeParse(metadata);

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.fields).toHaveLength(3);
        expect(result.data.operations).toContain('create');
        expect(result.data.authz?.read).toContain('user');
      }
    });

    it('should reject missing required fields', () => {
      const missingName = { displayName: 'Test', fields: [], operations: [] };
      const missingDisplayName = { name: 'test', fields: [], operations: [] };
      const missingFields = { name: 'test', displayName: 'Test', operations: [] };
      const missingOperations = { name: 'test', displayName: 'Test', fields: [] };

      expect(ResourceMetadataSchema.safeParse(missingName).success).toBe(false);
      expect(ResourceMetadataSchema.safeParse(missingDisplayName).success).toBe(false);
      expect(ResourceMetadataSchema.safeParse(missingFields).success).toBe(false);
      expect(ResourceMetadataSchema.safeParse(missingOperations).success).toBe(false);
    });

    it('should reject invalid field definitions within metadata', () => {
      const metadata = {
        name: 'test',
        displayName: 'Test',
        fields: [{ name: 'invalid', type: 'unsupported' }],
        operations: [],
      };

      const result = ResourceMetadataSchema.safeParse(metadata);

      expect(result.success).toBe(false);
    });
  });

  describe('PaginationSchema', () => {
    it('should validate valid pagination metadata', () => {
      const pagination = {
        page: 1,
        size: 20,
        total: 100,
        totalPages: 5,
      };

      const result = PaginationSchema.safeParse(pagination);

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.page).toBe(1);
        expect(result.data.size).toBe(20);
        expect(result.data.total).toBe(100);
        expect(result.data.totalPages).toBe(5);
      }
    });

    it('should validate zero values for empty results', () => {
      const pagination = {
        page: 0,
        size: 10,
        total: 0,
        totalPages: 0,
      };

      const result = PaginationSchema.safeParse(pagination);

      expect(result.success).toBe(true);
    });

    it('should reject missing pagination fields', () => {
      const missingPage = { size: 10, total: 100, totalPages: 10 };
      const missingSize = { page: 1, total: 100, totalPages: 10 };
      const missingTotal = { page: 1, size: 10, totalPages: 10 };
      const missingTotalPages = { page: 1, size: 10, total: 100 };

      expect(PaginationSchema.safeParse(missingPage).success).toBe(false);
      expect(PaginationSchema.safeParse(missingSize).success).toBe(false);
      expect(PaginationSchema.safeParse(missingTotal).success).toBe(false);
      expect(PaginationSchema.safeParse(missingTotalPages).success).toBe(false);
    });

    it('should reject non-numeric values', () => {
      const pagination = {
        page: '1',
        size: 10,
        total: 100,
        totalPages: 10,
      };

      const result = PaginationSchema.safeParse(pagination);

      expect(result.success).toBe(false);
    });
  });

  describe('ListResponseSchema', () => {
    it('should validate a list response with string items', () => {
      const response = {
        data: ['item1', 'item2', 'item3'],
        pagination: {
          page: 1,
          size: 10,
          total: 3,
          totalPages: 1,
        },
      };

      const schema = ListResponseSchema(z.string());
      const result = schema.safeParse(response);

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.data).toHaveLength(3);
        expect(result.data.pagination.total).toBe(3);
      }
    });

    it('should validate a list response with object items', () => {
      const userSchema = z.object({
        id: z.string(),
        name: z.string(),
        email: z.string().email(),
      });

      const response = {
        data: [
          { id: '1', name: 'Alice', email: 'alice@example.com' },
          { id: '2', name: 'Bob', email: 'bob@example.com' },
        ],
        pagination: {
          page: 1,
          size: 10,
          total: 2,
          totalPages: 1,
        },
      };

      const schema = ListResponseSchema(userSchema);
      const result = schema.safeParse(response);

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.data[0].name).toBe('Alice');
      }
    });

    it('should validate an empty list response', () => {
      const response = {
        data: [],
        pagination: {
          page: 1,
          size: 10,
          total: 0,
          totalPages: 0,
        },
      };

      const schema = ListResponseSchema(z.any());
      const result = schema.safeParse(response);

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.data).toHaveLength(0);
      }
    });

    it('should reject invalid items in data array', () => {
      const response = {
        data: [1, 2, 'invalid'],
        pagination: {
          page: 1,
          size: 10,
          total: 3,
          totalPages: 1,
        },
      };

      const schema = ListResponseSchema(z.number());
      const result = schema.safeParse(response);

      expect(result.success).toBe(false);
    });

    it('should reject missing pagination', () => {
      const response = {
        data: ['item1', 'item2'],
      };

      const schema = ListResponseSchema(z.string());
      const result = schema.safeParse(response);

      expect(result.success).toBe(false);
    });

    it('should reject missing data array', () => {
      const response = {
        pagination: {
          page: 1,
          size: 10,
          total: 0,
          totalPages: 0,
        },
      };

      const schema = ListResponseSchema(z.string());
      const result = schema.safeParse(response);

      expect(result.success).toBe(false);
    });
  });

  describe('ErrorResponseSchema', () => {
    it('should validate a minimal error response', () => {
      const errorResponse = {
        message: 'Something went wrong',
      };

      const result = ErrorResponseSchema.safeParse(errorResponse);

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.message).toBe('Something went wrong');
      }
    });

    it('should validate a complete error response', () => {
      const errorResponse = {
        error: 'ValidationError',
        message: 'Validation failed',
        details: { reason: 'Invalid input format' },
        fieldErrors: {
          email: ['Invalid email format', 'Email is required'],
          password: ['Password must be at least 8 characters'],
        },
      };

      const result = ErrorResponseSchema.safeParse(errorResponse);

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.error).toBe('ValidationError');
        expect(result.data.fieldErrors?.email).toHaveLength(2);
        expect(result.data.details).toEqual({ reason: 'Invalid input format' });
      }
    });

    it('should validate error response with only error and message', () => {
      const errorResponse = {
        error: 'NotFoundError',
        message: 'Resource not found',
      };

      const result = ErrorResponseSchema.safeParse(errorResponse);

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.error).toBe('NotFoundError');
        expect(result.data.fieldErrors).toBeUndefined();
      }
    });

    it('should validate error response with empty field errors', () => {
      const errorResponse = {
        message: 'Error occurred',
        fieldErrors: {},
      };

      const result = ErrorResponseSchema.safeParse(errorResponse);

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.fieldErrors).toEqual({});
      }
    });

    it('should reject missing message field', () => {
      const errorResponse = {
        error: 'SomeError',
      };

      const result = ErrorResponseSchema.safeParse(errorResponse);

      expect(result.success).toBe(false);
    });

    it('should reject invalid fieldErrors structure', () => {
      const errorResponse = {
        message: 'Error',
        fieldErrors: {
          email: 'Invalid', // Should be an array
        },
      };

      const result = ErrorResponseSchema.safeParse(errorResponse);

      expect(result.success).toBe(false);
    });

    it('should accept any type for details field', () => {
      const responses = [
        { message: 'Error', details: 'string details' },
        { message: 'Error', details: 123 },
        { message: 'Error', details: { nested: { deep: 'value' } } },
        { message: 'Error', details: ['array', 'details'] },
        { message: 'Error', details: null },
      ];

      for (const response of responses) {
        const result = ErrorResponseSchema.safeParse(response);
        expect(result.success).toBe(true);
      }
    });
  });

  describe('DiscoveryResponseSchema', () => {
    it('should validate a discovery response with multiple resources', () => {
      const response = {
        resources: [
          {
            name: 'users',
            displayName: 'Users',
            fields: [
              { name: 'id', type: 'string' },
              { name: 'email', type: 'string' },
            ],
            operations: ['list', 'get', 'create', 'update', 'delete'],
          },
          {
            name: 'orders',
            displayName: 'Orders',
            fields: [
              { name: 'id', type: 'string' },
              { name: 'total', type: 'number' },
            ],
            operations: ['list', 'get'],
          },
        ],
      };

      const result = DiscoveryResponseSchema.safeParse(response);

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.resources).toHaveLength(2);
        expect(result.data.resources[0].name).toBe('users');
        expect(result.data.resources[1].name).toBe('orders');
      }
    });

    it('should validate an empty discovery response', () => {
      const response = {
        resources: [],
      };

      const result = DiscoveryResponseSchema.safeParse(response);

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.resources).toHaveLength(0);
      }
    });

    it('should reject missing resources field', () => {
      const response = {};

      const result = DiscoveryResponseSchema.safeParse(response);

      expect(result.success).toBe(false);
    });

    it('should reject invalid resource metadata in array', () => {
      const response = {
        resources: [
          { name: 'valid', displayName: 'Valid', fields: [], operations: [] },
          { name: 'invalid' }, // Missing required fields
        ],
      };

      const result = DiscoveryResponseSchema.safeParse(response);

      expect(result.success).toBe(false);
    });
  });

  describe('Type inference', () => {
    it('should correctly infer types from schemas', () => {
      // This test verifies that TypeScript type inference works correctly
      // by using the inferred types in type-safe operations

      const validField = FieldDefinitionSchema.parse({
        name: 'test',
        type: 'string',
        required: true,
      });

      // TypeScript should know these properties exist
      const fieldName: string = validField.name;
      const fieldType: string = validField.type;
      const isRequired: boolean | undefined = validField.required;

      expect(fieldName).toBe('test');
      expect(fieldType).toBe('string');
      expect(isRequired).toBe(true);
    });

    it('should throw ZodError for invalid data when using parse()', () => {
      expect(() => {
        FieldDefinitionSchema.parse({ name: 'test' }); // Missing type
      }).toThrow(z.ZodError);
    });
  });
});
