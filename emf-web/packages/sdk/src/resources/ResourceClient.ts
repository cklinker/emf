import type { EMFClient } from '../client/EMFClient';
import type { ListOptions, ListResponse } from './types';
import { QueryBuilder } from '../query/QueryBuilder';
import { ListResponseSchema } from '../validation/schemas';
import { ValidationError } from '../errors';
import { z } from 'zod';

/**
 * Client for performing CRUD operations on a specific resource
 */
export class ResourceClient<T = unknown> {
  constructor(
    private readonly client: EMFClient,
    private readonly resourceName: string
  ) {}

  /**
   * Get the resource name
   */
  getName(): string {
    return this.resourceName;
  }

  /**
   * List resources with optional pagination, sorting, and filtering
   */
  async list(options?: ListOptions): Promise<ListResponse<T>> {
    const params = this.buildQueryParams(options);
    const response = await this.client.getAxiosInstance().get<ListResponse<T>>(
      `/api/${this.resourceName}`,
      { params }
    );

    // Validate response if validation is enabled
    if (this.client.isValidationEnabled()) {
      const listSchema = ListResponseSchema(z.unknown());
      const parseResult = listSchema.safeParse(response.data);
      if (!parseResult.success) {
        const errorMessages = parseResult.error.errors.map(
          (e) => `${e.path.join('.')}: ${e.message}`
        );
        throw new ValidationError(
          `Invalid list response for ${this.resourceName}: ${errorMessages.join(', ')}`,
          { schema: errorMessages }
        );
      }
    }

    return response.data;
  }

  /**
   * Get a single resource by ID
   */
  async get(id: string): Promise<T> {
    const response = await this.client.getAxiosInstance().get<T>(
      `/api/${this.resourceName}/${id}`
    );

    // Validate response if validation is enabled
    if (this.client.isValidationEnabled()) {
      // For single resource responses, we validate that it's an object (not null/undefined)
      if (response.data === null || response.data === undefined) {
        throw new ValidationError(
          `Invalid get response for ${this.resourceName}/${id}: expected object, got ${response.data}`,
          { schema: ['Response must be an object'] }
        );
      }
    }

    return response.data;
  }

  /**
   * Create a new resource
   */
  async create(data: Partial<T>): Promise<T> {
    const response = await this.client.getAxiosInstance().post<T>(
      `/api/${this.resourceName}`,
      data
    );

    // Validate response if validation is enabled
    if (this.client.isValidationEnabled()) {
      if (response.data === null || response.data === undefined) {
        throw new ValidationError(
          `Invalid create response for ${this.resourceName}: expected object, got ${response.data}`,
          { schema: ['Response must be an object'] }
        );
      }
    }

    return response.data;
  }

  /**
   * Update a resource (full replacement)
   */
  async update(id: string, data: T): Promise<T> {
    const response = await this.client.getAxiosInstance().put<T>(
      `/api/${this.resourceName}/${id}`,
      data
    );

    // Validate response if validation is enabled
    if (this.client.isValidationEnabled()) {
      if (response.data === null || response.data === undefined) {
        throw new ValidationError(
          `Invalid update response for ${this.resourceName}/${id}: expected object, got ${response.data}`,
          { schema: ['Response must be an object'] }
        );
      }
    }

    return response.data;
  }

  /**
   * Patch a resource (partial update)
   */
  async patch(id: string, data: Partial<T>): Promise<T> {
    const response = await this.client.getAxiosInstance().patch<T>(
      `/api/${this.resourceName}/${id}`,
      data
    );

    // Validate response if validation is enabled
    if (this.client.isValidationEnabled()) {
      if (response.data === null || response.data === undefined) {
        throw new ValidationError(
          `Invalid patch response for ${this.resourceName}/${id}: expected object, got ${response.data}`,
          { schema: ['Response must be an object'] }
        );
      }
    }

    return response.data;
  }

  /**
   * Delete a resource
   */
  async delete(id: string): Promise<void> {
    await this.client.getAxiosInstance().delete(`/api/${this.resourceName}/${id}`);
  }

  /**
   * Create a query builder for fluent query construction
   */
  query(): QueryBuilder<T> {
    return new QueryBuilder<T>(this);
  }

  /**
   * Build query parameters from list options
   */
  buildQueryParams(options?: ListOptions): Record<string, string | string[]> {
    const params: Record<string, string | string[]> = {};

    if (!options) {
      return params;
    }

    if (options.page !== undefined) {
      params.page = String(options.page);
    }

    if (options.size !== undefined) {
      params.size = String(options.size);
    }

    if (options.sort && options.sort.length > 0) {
      params.sort = options.sort.map((s) => `${s.field},${s.direction}`);
    }

    if (options.filters && options.filters.length > 0) {
      options.filters.forEach((filter) => {
        params[`${filter.field}[${filter.operator}]`] = String(filter.value);
      });
    }

    if (options.fields && options.fields.length > 0) {
      params.fields = options.fields.join(',');
    }

    return params;
  }

  /**
   * Build the list URL with query parameters (for testing)
   */
  buildListUrl(options?: ListOptions): string {
    const params = this.buildQueryParams(options);
    const queryString = Object.entries(params)
      .map(([key, value]) => {
        if (Array.isArray(value)) {
          return value.map((v) => `${encodeURIComponent(key)}=${encodeURIComponent(v)}`).join('&');
        }
        return `${encodeURIComponent(key)}=${encodeURIComponent(value)}`;
      })
      .join('&');

    const baseUrl = `/api/${this.resourceName}`;
    return queryString ? `${baseUrl}?${queryString}` : baseUrl;
  }
}
