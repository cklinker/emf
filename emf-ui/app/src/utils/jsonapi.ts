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
  metadata?: {
    totalCount?: number
    currentPage?: number
    pageSize?: number
    totalPages?: number
  }
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

  // Extract relationship IDs as flat fields.
  // The raw ID goes into result[key] for form binding.
  // The full { type, id } goes into result[`_rel_${key}`] for display/linking.
  if (resource.relationships) {
    for (const [key, rel] of Object.entries(resource.relationships)) {
      if (rel.data) {
        result[key] = rel.data.id
        result[`_rel_${key}`] = rel.data // { type, id } — target collection + record id
      } else {
        result[key] = null
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
 * Wrap a flat resource object into JSON:API request format.
 *
 * Input:  "products", { name: "Test", price: 10 }
 * Output: { data: { type: "products", attributes: { name: "Test", price: 10 } } }
 *
 * Input:  "products", { name: "Test", price: 10 }, "123"
 * Output: { data: { type: "products", id: "123", attributes: { name: "Test", price: 10 } } }
 *
 * When relationshipFields is provided, those fields are emitted in the
 * `relationships` section instead of `attributes`.  Each entry maps a field
 * name to the target collection type (from the JSON:API relationship `type`).
 *
 * Input:  "products", { name: "Test", category: "uuid-1" }, undefined,
 *         { category: "categories" }
 * Output: { data: { type: "products",
 *                    attributes: { name: "Test" },
 *                    relationships: { category: { data: { type: "categories", id: "uuid-1" } } } } }
 */
export function wrapResource(
  type: string,
  attributes: Record<string, unknown>,
  id?: string,
  relationshipFields?: Record<string, string>
): {
  data: {
    type: string
    id?: string
    attributes: Record<string, unknown>
    relationships?: Record<string, { data: { type: string; id: string } | null }>
  }
} {
  const data: {
    type: string
    id?: string
    attributes: Record<string, unknown>
    relationships?: Record<string, { data: { type: string; id: string } | null }>
  } = {
    type,
    attributes: { ...attributes },
  }

  if (id) {
    data.id = id
  }

  // Remove system fields from attributes — they belong at the top level or are server-managed
  delete data.attributes.id
  delete data.attributes.createdAt
  delete data.attributes.updatedAt
  delete data.attributes.created_at
  delete data.attributes.updated_at
  delete data.attributes.createdBy
  delete data.attributes.updatedBy
  delete data.attributes.created_by
  delete data.attributes.updated_by

  // Remove internal relationship metadata fields (prefixed with _rel_)
  for (const key of Object.keys(data.attributes)) {
    if (key.startsWith('_rel_')) {
      delete data.attributes[key]
    }
  }

  // Move relationship fields from attributes to relationships section
  if (relationshipFields) {
    const relationships: Record<string, { data: { type: string; id: string } | null }> = {}
    for (const [fieldName, relType] of Object.entries(relationshipFields)) {
      const value = data.attributes[fieldName]
      delete data.attributes[fieldName]
      if (value != null && value !== '') {
        relationships[fieldName] = { data: { type: relType, id: String(value) } }
      } else {
        relationships[fieldName] = { data: null }
      }
    }
    if (Object.keys(relationships).length > 0) {
      data.relationships = relationships
    }
  }

  return { data }
}

/**
 * Unwrap a collection response into an array of flat objects.
 *
 * Handles multiple response formats:
 * 1. Runtime format:   { data: [...], metadata: { totalCount, currentPage, pageSize, totalPages } }
 * 2. JSON:API format:  { data: [{ id, type, attributes }], total, page, pageSize }
 * 3. Already-flat:     { data: [...], total, page, pageSize }
 *
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

    // Extract pagination from metadata envelope (runtime format) or top-level fields
    const meta = jsonApiResponse.metadata
    const total = meta?.totalCount ?? jsonApiResponse.total ?? flatItems.length
    const page = meta?.currentPage ?? jsonApiResponse.page ?? 1
    const pageSize = meta?.pageSize ?? jsonApiResponse.pageSize ?? flatItems.length

    return {
      data: flatItems,
      total,
      page,
      pageSize,
    }
  }

  // Already in expected format
  return response as { data: T[]; total: number; page: number; pageSize: number }
}
