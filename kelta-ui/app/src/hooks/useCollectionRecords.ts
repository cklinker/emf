/**
 * useCollectionRecords Hook
 *
 * Fetches paginated records from a collection via JSON:API endpoints.
 * Supports sorting, filtering, and automatic JSON:API response unwrapping.
 */

import { useQuery } from '@tanstack/react-query'
import { useApi } from '../context/ApiContext'
import { useOffline } from '../offline'
import type { ReplicaRecord } from '../offline'
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
  rawResponse?: unknown
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
  /** Single sort or an ordered multi-sort list (serialized as `sort=a,-b`). */
  sort?: SortState | SortState[]
  filters?: FilterCondition[]
  include?: string
}

/**
 * Evaluate a single UI filter condition against a record value (best-effort,
 * used only for the offline replica path — the server is authoritative online).
 */
function matchesFilter(value: unknown, filter: FilterCondition): boolean {
  const target = filter.value
  const asStr = value == null ? '' : String(value)
  const numA = Number(value)
  const numB = Number(target)
  const bothNumeric = !Number.isNaN(numA) && !Number.isNaN(numB) && asStr.trim() !== ''
  switch (filter.operator) {
    case 'equals':
      return asStr === target
    case 'not_equals':
      return asStr !== target
    case 'contains':
      return asStr.toLowerCase().includes(target.toLowerCase())
    case 'starts_with':
      return asStr.toLowerCase().startsWith(target.toLowerCase())
    case 'ends_with':
      return asStr.toLowerCase().endsWith(target.toLowerCase())
    case 'greater_than':
      return bothNumeric ? numA > numB : asStr > target
    case 'less_than':
      return bothNumeric ? numA < numB : asStr < target
    case 'greater_than_or_equal':
      return bothNumeric ? numA >= numB : asStr >= target
    case 'less_than_or_equal':
      return bothNumeric ? numA <= numB : asStr <= target
    default:
      return true
  }
}

/**
 * Apply sort/filter/paginate over the offline replica's cached rows.
 * Best-effort parity with the server query; `total` reflects the cached subset.
 */
function queryReplica(rows: ReplicaRecord[], params: FetchRecordsParams): PaginatedResponse {
  const { page, pageSize, sort, filters } = params

  let result = rows
  if (filters && filters.length > 0) {
    result = result.filter((row) => filters.every((f) => matchesFilter(row[f.field], f)))
  }
  const sortLevels = sort ? (Array.isArray(sort) ? sort : [sort]) : []
  if (sortLevels.length > 0) {
    result = [...result].sort((a, b) => {
      for (const level of sortLevels) {
        const dir = level.direction === 'desc' ? -1 : 1
        const av = a[level.field]
        const bv = b[level.field]
        if (av == null && bv == null) continue
        if (av == null) return -dir
        if (bv == null) return dir
        if (av < bv) return -dir
        if (av > bv) return dir
      }
      return 0
    })
  }

  const total = result.length
  const start = (page - 1) * pageSize
  const pageRows = result.slice(start, start + pageSize)
  return { data: pageRows as CollectionRecord[], total, page, pageSize }
}

/**
 * Build JSON:API query parameters and fetch records.
 */
async function fetchRecords(
  apiClient: ApiClient,
  params: FetchRecordsParams
): Promise<PaginatedResponse> {
  const { collectionName, page, pageSize, sort, filters, include } = params

  const queryParams = new URLSearchParams()
  queryParams.set('page[number]', String(page))
  queryParams.set('page[size]', String(pageSize))

  if (sort) {
    const list = Array.isArray(sort) ? sort : [sort]
    const sortValue = list.map((s) => (s.direction === 'desc' ? `-${s.field}` : s.field)).join(',')
    if (sortValue) queryParams.set('sort', sortValue)
  }

  if (filters && filters.length > 0) {
    for (const filter of filters) {
      const op = FILTER_OPERATOR_MAP[filter.operator] || filter.operator
      queryParams.set(`filter[${filter.field}][${op}]`, filter.value)
    }
  }

  if (include) {
    queryParams.set('include', include)
  }

  const response = await apiClient.get(`/api/${collectionName}?${queryParams.toString()}`)
  const unwrapped = unwrapCollection<CollectionRecord>(response)
  return { ...unwrapped, rawResponse: response }
}

export interface UseCollectionRecordsOptions {
  /** Collection name (URL slug) */
  collectionName: string | undefined
  /** Current page (1-indexed) */
  page?: number
  /** Page size */
  pageSize?: number
  /** Sort state — single or an ordered multi-sort list (`sort=a,-b` server grammar) */
  sort?: SortState | SortState[]
  /** Active filters */
  filters?: FilterCondition[]
  /** Whether the query is enabled */
  enabled?: boolean
  /** Comma-separated list of relationship names to include via JSON:API ?include= */
  include?: string
}

export interface UseCollectionRecordsReturn {
  data: CollectionRecord[]
  total: number
  page: number
  pageSize: number
  isLoading: boolean
  error: Error | null
  refetch: () => void
  /** Raw JSON:API response for building display maps from included resources */
  rawResponse: unknown
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
  const offline = useOffline()
  const {
    collectionName,
    page = 1,
    pageSize = 25,
    sort,
    filters,
    enabled = true,
    include,
  } = options

  // undefined offline context (admin pages) ⇒ always treat as online.
  const isOnline = offline?.online !== false

  const {
    data: response,
    isLoading,
    error,
    refetch,
  } = useQuery({
    // `isOnline` is part of the key so a reconnect naturally re-runs the online path.
    queryKey: [
      'collection-records',
      collectionName,
      page,
      pageSize,
      sort,
      filters,
      include,
      isOnline,
    ],
    queryFn: async () => {
      const params: FetchRecordsParams = {
        collectionName: collectionName!,
        page,
        pageSize,
        sort,
        filters,
        include,
      }
      // Offline: serve the local replica.
      if (offline && !isOnline) {
        const rows = await offline.store.getAll(collectionName!)
        return queryReplica(rows, params)
      }
      // Online: fetch, then write-through into the replica for later offline reads.
      const result = await fetchRecords(apiClient, params)
      if (offline) {
        offline.registerCollection(collectionName!)
        await offline.store.putRecords(collectionName!, result.data as ReplicaRecord[])
      }
      return result
    },
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
    rawResponse: response?.rawResponse,
  }
}
