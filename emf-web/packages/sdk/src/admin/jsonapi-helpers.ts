/**
 * JSON:API helper utilities for wrapping/unwrapping requests and responses
 * when communicating with the DynamicCollectionRouter (worker).
 *
 * @since 1.0.0
 */

/**
 * Wraps a plain object into a JSON:API request body.
 *
 * @param type the resource type (collection name)
 * @param attributes the record attributes
 * @param id optional record ID (for updates)
 * @returns a JSON:API request body
 */
export function toJsonApiBody(
  type: string,
  attributes: Record<string, unknown>,
  id?: string
): { data: { type: string; id?: string; attributes: Record<string, unknown> } } {
  const cleaned = { ...attributes };
  // Remove system fields from attributes
  delete cleaned.id;
  delete cleaned.createdAt;
  delete cleaned.updatedAt;

  const data: { type: string; id?: string; attributes: Record<string, unknown> } = {
    type,
    attributes: cleaned,
  };

  if (id) {
    data.id = id;
  }

  return { data };
}

/**
 * JSON:API resource object shape from the DynamicCollectionRouter.
 */
interface JsonApiResourceObject {
  type: string;
  id: string;
  attributes: Record<string, unknown>;
  relationships?: Record<string, { data: { type: string; id: string } | null }>;
}

/**
 * JSON:API single-resource response.
 */
interface JsonApiSingleResponse {
  data: JsonApiResourceObject;
}

/**
 * JSON:API list response from the DynamicCollectionRouter.
 */
interface JsonApiListResponse {
  data: JsonApiResourceObject[];
  metadata: {
    totalCount: number;
    currentPage: number;
    pageSize: number;
    totalPages: number;
  };
}

/**
 * Unwraps a JSON:API single-resource response to a plain object.
 * Merges id + attributes + relationship IDs into a flat object.
 *
 * @param response the JSON:API response
 * @returns the unwrapped resource as a plain object
 */
export function unwrapJsonApiResource<T>(response: unknown): T {
  const typed = response as JsonApiSingleResponse;
  if (!typed?.data) {
    return response as T;
  }
  return flattenResourceObject<T>(typed.data);
}

/**
 * Unwraps a JSON:API list response to an array of plain objects.
 *
 * @param response the JSON:API list response
 * @returns the unwrapped array of resources
 */
export function unwrapJsonApiList<T>(response: unknown): T[] {
  const typed = response as JsonApiListResponse;
  if (!typed?.data || !Array.isArray(typed.data)) {
    // Fallback: if response is already a plain array
    if (Array.isArray(response)) {
      return response as T[];
    }
    return [];
  }
  return typed.data.map((item) => flattenResourceObject<T>(item));
}

/**
 * Extracts pagination metadata from a JSON:API list response.
 */
export function extractMetadata(response: unknown): {
  totalCount: number;
  currentPage: number;
  pageSize: number;
  totalPages: number;
} | null {
  const typed = response as JsonApiListResponse;
  return typed?.metadata ?? null;
}

/**
 * Flattens a JSON:API resource object into a plain object.
 */
function flattenResourceObject<T>(obj: JsonApiResourceObject): T {
  const result: Record<string, unknown> = {
    id: obj.id,
    ...obj.attributes,
  };

  // Flatten relationship IDs
  if (obj.relationships) {
    for (const [key, rel] of Object.entries(obj.relationships)) {
      result[key] = rel?.data?.id ?? null;
    }
  }

  return result as T;
}

/**
 * Builds JSON:API query parameters from simple options.
 *
 * @param options options with page, size, sort, filters
 * @returns URLSearchParams string
 */
export function buildJsonApiParams(options?: {
  page?: number;
  size?: number;
  sort?: string;
  filters?: Record<string, string>;
}): string {
  if (!options) return '';

  const params = new URLSearchParams();

  if (options.page !== undefined) {
    params.set('page[number]', String(options.page));
  }
  if (options.size !== undefined) {
    params.set('page[size]', String(options.size));
  }
  if (options.sort) {
    params.set('sort', options.sort);
  }
  if (options.filters) {
    for (const [key, value] of Object.entries(options.filters)) {
      params.set(key, value);
    }
  }

  const qs = params.toString();
  return qs ? `?${qs}` : '';
}
