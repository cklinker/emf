/**
 * useResources Hook
 *
 * Generic hook for fetching paginated resources from any collection via JSON:API.
 * Replaces the collection-specific pattern with a unified interface that works
 * identically for system collections (users, profiles, etc.) and user-defined
 * collections (products, orders, etc.).
 *
 * Supports pagination, sorting, filtering, includes, and sparse fieldsets.
 */

import { useQuery } from '@tanstack/react-query'
import { useApi } from '../context/ApiContext'
import { unwrapCollection } from '../utils/jsonapi'
import type { ApiClient } from '../services/apiClient'
import type { CollectionRecord, SortState, FilterCondition } from './useCollectionRecords'

export { type CollectionRecord, type SortState, type FilterCondition }

export interface UseResourcesOptions {
  /** Collection name (URL slug), e.g. "users", "products", "profiles" */
  resource: string | undefined
  /** Current page (1-indexed). Defaults to 1. */
  page?: number
  /** Page size. Defaults to 25. */
  pageSize?: number
  /** Sort state */
  sort?: SortState
  /** Active filters */
  filters?: FilterCondition[]
  /** Related resources to include (e.g. ["profile", "permissionSets"]) */
  include?: string[]
  /** Sparse fieldsets — fields to return per resource type (e.g. { users: ["name", "email"] }) */
  fields?: Record<string, string[]>
  /** Whether the query is enabled. Defaults to true. */
  enabled?: boolean
  /** Stale time in milliseconds. Defaults to 30000 (30s). */
  staleTime?: number
}

export interface UseResourcesReturn<T extends CollectionRecord = CollectionRecord> {
  /** Array of flattened resource records */
  data: T[]
  /** Total number of matching records across all pages */
  total: number
  /** Current page number */
  page: number
  /** Current page size */
  pageSize: number
  /** True while the initial fetch is loading */
  isLoading: boolean
  /** Error if the query failed, null otherwise */
  error: Error | null
  /** Manually refetch the data */
  refetch: () => void
}

/**
 * Map UI filter operators to JSON:API filter query parameter operators.
 */
const FILTER_OPERATOR_MAP: Record<string, string> = {
  equals: 'eq',
  not_equals: 'neq',
  contains: 'contains',
  starts_with: 'starts',
  ends_with: 'ends',
  greater_than: 'gt',
  less_than: 'lt',
  greater_than_or_equal: 'gte',
  less_than_or_equal: 'lte',
}

/**
 * Build URL with JSON:API query parameters and fetch resources.
 */
async function fetchResources(
  apiClient: ApiClient,
  resource: string,
  page: number,
  pageSize: number,
  sort?: SortState,
  filters?: FilterCondition[],
  include?: string[],
  fields?: Record<string, string[]>
): Promise<{ data: CollectionRecord[]; total: number; page: number; pageSize: number }> {
  const queryParams = new URLSearchParams()
  queryParams.set('page[number]', String(page))
  queryParams.set('page[size]', String(pageSize))

  if (sort) {
    const sortValue = sort.direction === 'desc' ? `-${sort.field}` : sort.field
    queryParams.set('sort', sortValue)
  }

  if (filters && filters.length > 0) {
    for (const filter of filters) {
      const op = FILTER_OPERATOR_MAP[filter.operator] || filter.operator
      queryParams.set(`filter[${filter.field}][${op}]`, filter.value)
    }
  }

  if (include && include.length > 0) {
    queryParams.set('include', include.join(','))
  }

  if (fields) {
    for (const [type, fieldList] of Object.entries(fields)) {
      queryParams.set(`fields[${type}]`, fieldList.join(','))
    }
  }

  const response = await apiClient.get(`/api/${resource}?${queryParams.toString()}`)
  return unwrapCollection<CollectionRecord>(response)
}

/**
 * Generic hook to fetch paginated resources from any collection.
 *
 * Works identically for system collections and user-defined collections:
 *
 * ```tsx
 * // System collection
 * const { data: users } = useResources({ resource: 'users', include: ['profile'] })
 *
 * // User collection
 * const { data: products } = useResources({ resource: 'products', sort: { field: 'name', direction: 'asc' } })
 *
 * // With filtering
 * const { data: activeUsers } = useResources({
 *   resource: 'users',
 *   filters: [{ id: '1', field: 'status', operator: 'equals', value: 'ACTIVE' }]
 * })
 * ```
 */
export function useResources<T extends CollectionRecord = CollectionRecord>(
  options: UseResourcesOptions
): UseResourcesReturn<T> {
  const { apiClient } = useApi()
  const {
    resource,
    page = 1,
    pageSize = 25,
    sort,
    filters,
    include,
    fields,
    enabled = true,
    staleTime = 30 * 1000,
  } = options

  const {
    data: response,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['resources', resource, page, pageSize, sort, filters, include, fields],
    queryFn: () =>
      fetchResources(apiClient, resource!, page, pageSize, sort, filters, include, fields),
    enabled: !!resource && enabled,
    staleTime,
  })

  return {
    data: (response?.data ?? []) as T[],
    total: response?.total ?? 0,
    page: response?.page ?? page,
    pageSize: response?.pageSize ?? pageSize,
    isLoading,
    error: error as Error | null,
    refetch,
  }
}
