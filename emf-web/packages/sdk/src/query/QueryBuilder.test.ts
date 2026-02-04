import { describe, it, expect, vi, beforeEach } from 'vitest';
import { QueryBuilder } from './QueryBuilder';
import type { ResourceClient } from '../resources/ResourceClient';
import type { ListResponse } from '../resources/types';

describe('QueryBuilder', () => {
  let mockResourceClient: ResourceClient<{ id: string; name: string }>;
  let queryBuilder: QueryBuilder<{ id: string; name: string }>;

  const mockListResponse: ListResponse<{ id: string; name: string }> = {
    data: [
      { id: '1', name: 'Alice' },
      { id: '2', name: 'Bob' },
    ],
    pagination: {
      page: 1,
      size: 10,
      total: 2,
      totalPages: 1,
    },
  };

  beforeEach(() => {
    mockResourceClient = {
      list: vi.fn().mockResolvedValue(mockListResponse),
    } as unknown as ResourceClient<{ id: string; name: string }>;

    queryBuilder = new QueryBuilder(mockResourceClient);
  });

  describe('paginate() - Requirement 5.1', () => {
    it('should store pagination parameters when paginate is called', () => {
      queryBuilder.paginate(2, 25);

      const options = queryBuilder.buildOptions();
      expect(options.page).toBe(2);
      expect(options.size).toBe(25);
    });

    it('should return the QueryBuilder instance for chaining', () => {
      const result = queryBuilder.paginate(1, 10);
      expect(result).toBe(queryBuilder);
    });

    it('should overwrite previous pagination when called multiple times', () => {
      queryBuilder.paginate(1, 10).paginate(3, 50);

      const options = queryBuilder.buildOptions();
      expect(options.page).toBe(3);
      expect(options.size).toBe(50);
    });
  });

  describe('sort() - Requirement 5.2', () => {
    it('should add sort criteria when sort is called', () => {
      queryBuilder.sort('name', 'asc');

      const options = queryBuilder.buildOptions();
      expect(options.sort).toEqual([{ field: 'name', direction: 'asc' }]);
    });

    it('should return the QueryBuilder instance for chaining', () => {
      const result = queryBuilder.sort('name', 'asc');
      expect(result).toBe(queryBuilder);
    });

    it('should accumulate multiple sort criteria (Requirement 5.7)', () => {
      queryBuilder.sort('name', 'asc').sort('createdAt', 'desc');

      const options = queryBuilder.buildOptions();
      expect(options.sort).toHaveLength(2);
      expect(options.sort![0]).toEqual({ field: 'name', direction: 'asc' });
      expect(options.sort![1]).toEqual({ field: 'createdAt', direction: 'desc' });
    });

    it('should preserve sort order when multiple criteria are added (Requirement 5.7)', () => {
      queryBuilder
        .sort('field1', 'asc')
        .sort('field2', 'desc')
        .sort('field3', 'asc');

      const options = queryBuilder.buildOptions();
      expect(options.sort).toEqual([
        { field: 'field1', direction: 'asc' },
        { field: 'field2', direction: 'desc' },
        { field: 'field3', direction: 'asc' },
      ]);
    });
  });

  describe('filter() - Requirement 5.3', () => {
    it('should add a filter condition when filter is called', () => {
      queryBuilder.filter('status', 'eq', 'active');

      const options = queryBuilder.buildOptions();
      expect(options.filters).toEqual([{ field: 'status', operator: 'eq', value: 'active' }]);
    });

    it('should return the QueryBuilder instance for chaining', () => {
      const result = queryBuilder.filter('status', 'eq', 'active');
      expect(result).toBe(queryBuilder);
    });

    it('should accumulate multiple filters with AND logic (Requirement 5.6)', () => {
      queryBuilder
        .filter('status', 'eq', 'active')
        .filter('age', 'gte', 18);

      const options = queryBuilder.buildOptions();
      expect(options.filters).toHaveLength(2);
      expect(options.filters![0]).toEqual({ field: 'status', operator: 'eq', value: 'active' });
      expect(options.filters![1]).toEqual({ field: 'age', operator: 'gte', value: 18 });
    });

    it('should support all filter operators', () => {
      const operators = ['eq', 'ne', 'gt', 'gte', 'lt', 'lte', 'contains', 'startsWith', 'endsWith', 'in'] as const;
      
      operators.forEach((op, index) => {
        queryBuilder.filter(`field${index}`, op, `value${index}`);
      });

      const options = queryBuilder.buildOptions();
      expect(options.filters).toHaveLength(operators.length);
      operators.forEach((op, index) => {
        expect(options.filters![index].operator).toBe(op);
      });
    });

    it('should support various value types', () => {
      queryBuilder
        .filter('stringField', 'eq', 'text')
        .filter('numberField', 'gt', 100)
        .filter('booleanField', 'eq', true)
        .filter('arrayField', 'in', ['a', 'b', 'c']);

      const options = queryBuilder.buildOptions();
      expect(options.filters![0].value).toBe('text');
      expect(options.filters![1].value).toBe(100);
      expect(options.filters![2].value).toBe(true);
      expect(options.filters![3].value).toEqual(['a', 'b', 'c']);
    });
  });

  describe('fields() - Requirement 5.4', () => {
    it('should store field selection when fields is called', () => {
      queryBuilder.fields('id', 'name', 'email');

      const options = queryBuilder.buildOptions();
      expect(options.fields).toEqual(['id', 'name', 'email']);
    });

    it('should return the QueryBuilder instance for chaining', () => {
      const result = queryBuilder.fields('id', 'name');
      expect(result).toBe(queryBuilder);
    });

    it('should accumulate fields when called multiple times', () => {
      queryBuilder.fields('id', 'name').fields('email', 'phone');

      const options = queryBuilder.buildOptions();
      expect(options.fields).toEqual(['id', 'name', 'email', 'phone']);
    });

    it('should handle single field selection', () => {
      queryBuilder.fields('id');

      const options = queryBuilder.buildOptions();
      expect(options.fields).toEqual(['id']);
    });
  });

  describe('execute() - Requirement 5.5', () => {
    it('should construct query parameters and execute the request', async () => {
      queryBuilder
        .paginate(1, 10)
        .sort('name', 'asc')
        .filter('status', 'eq', 'active')
        .fields('id', 'name');

      const result = await queryBuilder.execute();

      expect(mockResourceClient.list).toHaveBeenCalledWith({
        page: 1,
        size: 10,
        sort: [{ field: 'name', direction: 'asc' }],
        filters: [{ field: 'status', operator: 'eq', value: 'active' }],
        fields: ['id', 'name'],
      });
      expect(result).toEqual(mockListResponse);
    });

    it('should execute with empty options when no methods called', async () => {
      await queryBuilder.execute();

      expect(mockResourceClient.list).toHaveBeenCalledWith({});
    });

    it('should execute with only pagination', async () => {
      queryBuilder.paginate(2, 20);

      await queryBuilder.execute();

      expect(mockResourceClient.list).toHaveBeenCalledWith({
        page: 2,
        size: 20,
      });
    });

    it('should execute with only sort', async () => {
      queryBuilder.sort('name', 'desc');

      await queryBuilder.execute();

      expect(mockResourceClient.list).toHaveBeenCalledWith({
        sort: [{ field: 'name', direction: 'desc' }],
      });
    });

    it('should execute with only filters', async () => {
      queryBuilder.filter('active', 'eq', true);

      await queryBuilder.execute();

      expect(mockResourceClient.list).toHaveBeenCalledWith({
        filters: [{ field: 'active', operator: 'eq', value: true }],
      });
    });

    it('should execute with only fields', async () => {
      queryBuilder.fields('id', 'name');

      await queryBuilder.execute();

      expect(mockResourceClient.list).toHaveBeenCalledWith({
        fields: ['id', 'name'],
      });
    });
  });

  describe('Multiple filters with AND logic - Requirement 5.6', () => {
    it('should combine multiple filters in the order they were added', () => {
      queryBuilder
        .filter('status', 'eq', 'active')
        .filter('role', 'eq', 'admin')
        .filter('age', 'gte', 21);

      const options = queryBuilder.buildOptions();
      expect(options.filters).toHaveLength(3);
      // Filters should be in the order they were added (AND logic)
      expect(options.filters![0].field).toBe('status');
      expect(options.filters![1].field).toBe('role');
      expect(options.filters![2].field).toBe('age');
    });

    it('should allow multiple filters on the same field', () => {
      queryBuilder
        .filter('age', 'gte', 18)
        .filter('age', 'lte', 65);

      const options = queryBuilder.buildOptions();
      expect(options.filters).toHaveLength(2);
      expect(options.filters![0]).toEqual({ field: 'age', operator: 'gte', value: 18 });
      expect(options.filters![1]).toEqual({ field: 'age', operator: 'lte', value: 65 });
    });
  });

  describe('Sort order preservation - Requirement 5.7', () => {
    it('should apply sort criteria in the order they were added', () => {
      queryBuilder
        .sort('lastName', 'asc')
        .sort('firstName', 'asc')
        .sort('createdAt', 'desc');

      const options = queryBuilder.buildOptions();
      expect(options.sort).toEqual([
        { field: 'lastName', direction: 'asc' },
        { field: 'firstName', direction: 'asc' },
        { field: 'createdAt', direction: 'desc' },
      ]);
    });
  });

  describe('Fluent API chaining', () => {
    it('should support full fluent chaining of all methods', async () => {
      const result = await queryBuilder
        .paginate(1, 10)
        .sort('name', 'asc')
        .sort('createdAt', 'desc')
        .filter('status', 'eq', 'active')
        .filter('type', 'in', ['user', 'admin'])
        .fields('id', 'name', 'email')
        .execute();

      expect(mockResourceClient.list).toHaveBeenCalledWith({
        page: 1,
        size: 10,
        sort: [
          { field: 'name', direction: 'asc' },
          { field: 'createdAt', direction: 'desc' },
        ],
        filters: [
          { field: 'status', operator: 'eq', value: 'active' },
          { field: 'type', operator: 'in', value: ['user', 'admin'] },
        ],
        fields: ['id', 'name', 'email'],
      });
      expect(result).toEqual(mockListResponse);
    });

    it('should allow methods to be called in any order', async () => {
      await queryBuilder
        .fields('id')
        .filter('active', 'eq', true)
        .paginate(1, 5)
        .sort('name', 'asc')
        .execute();

      expect(mockResourceClient.list).toHaveBeenCalledWith({
        page: 1,
        size: 5,
        sort: [{ field: 'name', direction: 'asc' }],
        filters: [{ field: 'active', operator: 'eq', value: true }],
        fields: ['id'],
      });
    });
  });

  describe('buildOptions()', () => {
    it('should return empty object when no options set', () => {
      const options = queryBuilder.buildOptions();
      expect(options).toEqual({});
    });

    it('should return a copy of the options (not mutate internal state)', () => {
      queryBuilder.paginate(1, 10).sort('name', 'asc');

      const options1 = queryBuilder.buildOptions();
      const options2 = queryBuilder.buildOptions();

      // Should be equal but not the same reference
      expect(options1).toEqual(options2);
      expect(options1.sort).not.toBe(options2.sort);
    });
  });

  describe('buildQueryParams()', () => {
    it('should build query params string for pagination', () => {
      queryBuilder.paginate(2, 25);

      const params = queryBuilder.buildQueryParams();
      expect(params).toContain('page=2');
      expect(params).toContain('size=25');
    });

    it('should build query params string for sort', () => {
      queryBuilder.sort('name', 'asc').sort('date', 'desc');

      const params = queryBuilder.buildQueryParams();
      expect(params).toContain('sort=name,asc');
      expect(params).toContain('sort=date,desc');
    });

    it('should build query params string for filters', () => {
      queryBuilder.filter('status', 'eq', 'active');

      const params = queryBuilder.buildQueryParams();
      expect(params).toContain('status[eq]=active');
    });

    it('should build query params string for fields', () => {
      queryBuilder.fields('id', 'name', 'email');

      const params = queryBuilder.buildQueryParams();
      expect(params).toContain('fields=id,name,email');
    });

    it('should return empty string when no options set', () => {
      const params = queryBuilder.buildQueryParams();
      expect(params).toBe('');
    });
  });

  describe('reset()', () => {
    it('should clear all accumulated state', () => {
      queryBuilder
        .paginate(1, 10)
        .sort('name', 'asc')
        .filter('status', 'eq', 'active')
        .fields('id', 'name')
        .reset();

      const options = queryBuilder.buildOptions();
      expect(options).toEqual({});
    });

    it('should return the QueryBuilder instance for chaining', () => {
      const result = queryBuilder.reset();
      expect(result).toBe(queryBuilder);
    });

    it('should allow building new query after reset', async () => {
      queryBuilder
        .paginate(1, 10)
        .filter('old', 'eq', 'value')
        .reset()
        .paginate(2, 20)
        .filter('new', 'eq', 'value');

      await queryBuilder.execute();

      expect(mockResourceClient.list).toHaveBeenCalledWith({
        page: 2,
        size: 20,
        filters: [{ field: 'new', operator: 'eq', value: 'value' }],
      });
    });
  });
});
