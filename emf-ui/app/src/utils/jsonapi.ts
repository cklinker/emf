/**
 * JSON:API Response Utilities
 *
 * The EMF gateway returns resources in JSON:API format:
 *   { data: { id, type, attributes: { ... }, relationships: { ... } } }
 *
 * The UI components expect flat resource objects:
 *   { id, name, price, ... }
 *
 * These utilities convert between the two formats.
 */

/**
 * JSON:API resource object as returned by the gateway
 */
export interface JsonApiResource {
  id: string
  type: string
  attributes?: Record<string, unknown>
  relationships?: Record<string, { data: { id: string; type: string } | null }>
}

/**
 * JSON:API single-resource response envelope
 */
export interface JsonApiResponse {
  data: JsonApiResource
}

/**
 * JSON:API collection response envelope
 */
export interface JsonApiCollectionResponse {
  data: JsonApiResource[]
  total?: number
  page?: number
  pageSize?: number
}

/**
 * Flatten a single JSON:API resource object into a plain object.
 *
 * Input:  { id: "1", type: "product", attributes: { name: "Test", price: 10 } }
 * Output: { id: "1", name: "Test", price: 10 }
 */
export function flattenResource<T extends Record<string, unknown> = Record<string, unknown>>(
  resource: JsonApiResource
): T {
  const result: Record<string, unknown> = {
    id: resource.id,
  }

  // Merge attributes into flat object
  if (resource.attributes) {
    Object.assign(result, resource.attributes)
  }

  // Extract relationship IDs as flat fields (e.g., owner -> ownerId or owner)
  if (resource.relationships) {
    for (const [key, rel] of Object.entries(resource.relationships)) {
      if (rel.data) {
        result[key] = rel.data.id
      }
    }
  }

  return result as T
}

/**
 * Unwrap a single-resource JSON:API response into a flat object.
 *
 * Handles both JSON:API envelope format and already-flat objects gracefully.
 *
 * Input:  { data: { id: "1", type: "product", attributes: { name: "Test" } } }
 * Output: { id: "1", name: "Test" }
 */
export function unwrapResource<T extends Record<string, unknown> = Record<string, unknown>>(
  response: unknown
): T {
  // If the response has a `data` property with `type` and `attributes`, it's JSON:API
  if (
    response &&
    typeof response === 'object' &&
    'data' in response &&
    (response as JsonApiResponse).data &&
    typeof (response as JsonApiResponse).data === 'object' &&
    'type' in (response as JsonApiResponse).data
  ) {
    return flattenResource<T>((response as JsonApiResponse).data)
  }

  // Already a flat object, return as-is
  return response as T
}

/**
 * Unwrap a collection JSON:API response into an array of flat objects.
 *
 * Input:  { data: [{ id: "1", type: "product", attributes: { name: "Test" } }], total: 1 }
 * Output: { data: [{ id: "1", name: "Test" }], total: 1, page: 1, pageSize: 20 }
 */
export function unwrapCollection<T extends Record<string, unknown> = Record<string, unknown>>(
  response: unknown
): { data: T[]; total: number; page: number; pageSize: number } {
  if (
    response &&
    typeof response === 'object' &&
    'data' in response &&
    Array.isArray((response as JsonApiCollectionResponse).data)
  ) {
    const jsonApiResponse = response as JsonApiCollectionResponse
    const items = jsonApiResponse.data

    // Check if items are JSON:API resources (have `type` and `attributes`)
    const flatItems =
      items.length > 0 && 'type' in items[0] && 'attributes' in items[0]
        ? items.map((item) => flattenResource<T>(item))
        : (items as T[])

    return {
      data: flatItems,
      total: jsonApiResponse.total ?? flatItems.length,
      page: jsonApiResponse.page ?? 1,
      pageSize: jsonApiResponse.pageSize ?? flatItems.length,
    }
  }

  // Already in expected format
  return response as { data: T[]; total: number; page: number; pageSize: number }
}
