/**
 * Tests for CLI generate-types command
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { parseArgs, printHelp, generateTypesFromSpec } from './generate-types';

describe('CLI generate-types', () => {
  describe('parseArgs', () => {
    it('should parse valid arguments with short flags', () => {
      const result = parseArgs(['-u', 'http://example.com/openapi.json', '-o', './types.ts']);
      expect(result).toEqual({
        url: 'http://example.com/openapi.json',
        output: './types.ts',
        includeRequests: true,
        includeResponses: true,
      });
    });

    it('should parse valid arguments with long flags', () => {
      const result = parseArgs(['--url', 'http://example.com/openapi.json', '--output', './types.ts']);
      expect(result).toEqual({
        url: 'http://example.com/openapi.json',
        output: './types.ts',
        includeRequests: true,
        includeResponses: true,
      });
    });

    it('should parse --no-requests flag', () => {
      const result = parseArgs(['-u', 'http://example.com/openapi.json', '-o', './types.ts', '--no-requests']);
      expect(result).not.toHaveProperty('error');
      expect((result as any).includeRequests).toBe(false);
    });

    it('should parse --no-responses flag', () => {
      const result = parseArgs(['-u', 'http://example.com/openapi.json', '-o', './types.ts', '--no-responses']);
      expect(result).not.toHaveProperty('error');
      expect((result as any).includeResponses).toBe(false);
    });

    it('should return error for missing --url', () => {
      const result = parseArgs(['-o', './types.ts']);
      expect(result).toHaveProperty('error');
      expect((result as any).error).toContain('--url');
    });

    it('should return error for missing --output', () => {
      const result = parseArgs(['-u', 'http://example.com/openapi.json']);
      expect(result).toHaveProperty('error');
      expect((result as any).error).toContain('--output');
    });

    it('should return error for missing value after --url', () => {
      const result = parseArgs(['-u', '-o', './types.ts']);
      expect(result).toHaveProperty('error');
      expect((result as any).error).toContain('--url');
    });

    it('should return error for unknown option', () => {
      const result = parseArgs(['-u', 'http://example.com/openapi.json', '-o', './types.ts', '--unknown']);
      expect(result).toHaveProperty('error');
      expect((result as any).error).toContain('Unknown option');
    });

    it('should return help indicator for --help', () => {
      const result = parseArgs(['--help']);
      expect(result).toEqual({ error: 'help' });
    });

    it('should return help indicator for -h', () => {
      const result = parseArgs(['-h']);
      expect(result).toEqual({ error: 'help' });
    });
  });

  describe('printHelp', () => {
    it('should print help message without errors', () => {
      const consoleSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
      printHelp();
      expect(consoleSpy).toHaveBeenCalled();
      const output = consoleSpy.mock.calls[0][0];
      expect(output).toContain('EMF Type Generator');
      expect(output).toContain('--url');
      expect(output).toContain('--output');
      consoleSpy.mockRestore();
    });
  });

  describe('generateTypesFromSpec', () => {
    it('should generate types from valid spec', () => {
      const spec = {
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
            post: {
              requestBody: {
                content: {
                  'application/json': {
                    schema: {
                      type: 'object',
                      properties: {
                        name: { type: 'string' },
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

      const { content, result } = generateTypesFromSpec(spec, './output.ts');
      expect(result.success).toBe(true);
      expect(content).toContain('export interface Users');
      expect(content).toContain('export interface UsersCreateRequest');
      expect(content).toContain('export interface UsersListResponse');
    });

    it('should return error for invalid spec', () => {
      const { content, result } = generateTypesFromSpec(null, './output.ts');
      expect(result.success).toBe(false);
      expect(result.errors).toBeDefined();
      expect(content).toBe('');
    });

    it('should respect includeRequests option', () => {
      const spec = {
        openapi: '3.0.0',
        info: { title: 'Test API', version: '1.0.0' },
        paths: {
          '/api/users': {
            post: {
              requestBody: {
                content: {
                  'application/json': {
                    schema: {
                      type: 'object',
                      properties: { name: { type: 'string' } },
                    },
                  },
                },
              },
              responses: {},
            },
          },
        },
      };

      const { content } = generateTypesFromSpec(spec, './output.ts', { includeRequests: false });
      expect(content).not.toContain('CreateRequest');
    });

    it('should respect includeResponses option', () => {
      const spec = {
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
                              properties: { id: { type: 'string' } },
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

      const { content } = generateTypesFromSpec(spec, './output.ts', { includeResponses: false });
      // Should not contain collection-specific list response, but common ListResponse<T> is always included
      expect(content).not.toContain('UsersListResponse');
    });
  });
});
