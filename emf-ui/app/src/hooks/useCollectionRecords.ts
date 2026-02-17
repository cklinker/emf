/**
 * useCollectionRecords Hook
 *
 * Fetches paginated records from a collection via JSON:API endpoints.
 * Supports sorting, filtering, and automatic JSON:API response unwrapping.
 */

import { useQuery } from '@tanstack/react-query'
import { useApi } from '../context/ApiContext'
import { unwrapCollection } from '../utils/jsonapi'
import type { ApiClient } from '../services/apiClient'

/**
 * A flat resource record (JSON:API attributes merged to top level).
 */
export interface CollectionRecord {
  id: string
  [key: string]: unknown
}

/**
 * Sort state for a single column.
 */
export interface SortState {
  field: string
  direction: 'asc' | 'desc'
}

/**
 * Filter operator type matching JSON:API backend operators.
 */
export type FilterOperator =
  | 'equals'
  | 'not_equals'
  | 'contains'
  | 'starts_with'
  | 'ends_with'
  | 'greater_than'
  | 'less_than'
  | 'greater_than_or_equal'
  | 'less_than_or_equal'

/**
 * A single filter condition.
 */
export interface FilterCondition {
  id: string
  field: string
  operator: FilterOperator
  value: string
}

/**
 * Paginated response from the API.
 */
export interface PaginatedResponse {
  data: CollectionRecord[]
  total: number
  page: number
  pageSize: number
}

/**
 * Map UI filter operators to JSON:API filter query parameter operators.
 */
const FILTER_OPERATOR_MAP: Record<FilterOperator, string> = {
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

interface FetchRecordsParams {
  collectionName: string
  page: number
  pageSize: number
  sort?: SortState
  filters?: FilterCondition[]
}

/**
 * Build JSON:API query parameters and fetch records.
 */
async function fetchRecords(
  apiClient: ApiClient,
  params: FetchRecordsParams
): Promise<PaginatedResponse> {
  const { collectionName, page, pageSize, sort, filters } = params

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

  const response = await apiClient.get(`/api/${collectionName}?${queryParams.toString()}`)
  return unwrapCollection<CollectionRecord>(response)
}

export interface UseCollectionRecordsOptions {
  /** Collection name (URL slug) */
  collectionName: string | undefined
  /** Current page (1-indexed) */
  page?: number
  /** Page size */
  pageSize?: number
  /** Sort state */
  sort?: SortState
  /** Active filters */
  filters?: FilterCondition[]
  /** Whether the query is enabled */
  enabled?: boolean
}

export interface UseCollectionRecordsReturn {
  data: CollectionRecord[]
  total: number
  page: number
  pageSize: number
  isLoading: boolean
  error: Error | null
  refetch: () => void
}

/**
 * Hook to fetch paginated records from a collection.
 *
 * Uses JSON:API query parameters for pagination, sorting, and filtering.
 * Responses are automatically unwrapped from JSON:API format to flat objects.
 *
 * @param options - Collection name, pagination, sort, and filter options
 * @returns Records array, pagination metadata, loading/error states
 */
export function useCollectionRecords(
  options: UseCollectionRecordsOptions
): UseCollectionRecordsReturn {
  const { apiClient } = useApi()
  const { collectionName, page = 1, pageSize = 25, sort, filters, enabled = true } = options

  const {
    data: response,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['collection-records', collectionName, page, pageSize, sort, filters],
    queryFn: () =>
      fetchRecords(apiClient, {
        collectionName: collectionName!,
        page,
        pageSize,
        sort,
        filters,
      }),
    enabled: !!collectionName && enabled,
    staleTime: 30 * 1000, // 30 seconds — record data changes more frequently
  })

  return {
    data: response?.data ?? [],
    total: response?.total ?? 0,
    page: response?.page ?? page,
    pageSize: response?.pageSize ?? pageSize,
    isLoading,
    error: error as Error | null,
    refetch,
  }
}
