/**
 * useRelatedRecords Hook
 *
 * Fetches related child records for a parent record by filtering on
 * a foreign key field. Used by RelatedList component on the detail page
 * to show master-detail relationships.
 */

import { useQuery } from '@tanstack/react-query'
import { useApi } from '../context/ApiContext'
import { unwrapCollection } from '../utils/jsonapi'
import type { ApiClient } from '../services/apiClient'
import type { CollectionRecord, PaginatedResponse } from './useCollectionRecords'

export interface RelatedRecordsOptions {
  /** Collection name of the related records */
  collectionName: string | undefined
  /** Foreign key field name on the related collection */
  foreignKeyField: string | undefined
  /** Parent record ID to filter by */
  parentRecordId: string | undefined
  /** Maximum number of related records to fetch (default: 5) */
  limit?: number
  /** Whether the query is enabled */
  enabled?: boolean
  /** Comma-separated list of collection names to include via JSON:API ?include= */
  include?: string
}

export interface UseRelatedRecordsReturn {
  /** Related records */
  data: CollectionRecord[]
  /** Total count of related records */
  total: number
  /** Whether the query is loading */
  isLoading: boolean
  /** Error from the query */
  error: Error | null
  /** Refetch the data */
  refetch: () => void
  /** Raw JSON:API response for building display maps from included resources */
  rawResponse: unknown
}

/**
 * Fetch related records filtered by a foreign key.
 */
async function fetchRelatedRecords(
  apiClient: ApiClient,
  collectionName: string,
  foreignKeyField: string,
  parentRecordId: string,
  limit: number,
  include?: string
): Promise<{ paginated: PaginatedResponse; rawResponse: unknown }> {
  const queryParams = new URLSearchParams()
  queryParams.set('page[number]', '1')
  queryParams.set('page[size]', String(limit))
  queryParams.set(`filter[${foreignKeyField}][eq]`, parentRecordId)
  if (include) {
    queryParams.set('include', include)
  }

  const rawResponse = await apiClient.get(`/api/${collectionName}?${queryParams.toString()}`)
  const paginated = unwrapCollection<CollectionRecord>(rawResponse)
  return { paginated, rawResponse }
}

/**
 * Hook to fetch related (child) records for a parent record.
 *
 * Filters the related collection by a foreign key field matching
 * the parent record's ID. Used in master-detail relationship views.
 *
 * @param options - Related records fetch options
 * @returns Related records, total count, loading/error states
 */
export function useRelatedRecords(options: RelatedRecordsOptions): UseRelatedRecordsReturn {
  const { apiClient } = useApi()
  const {
    collectionName,
    foreignKeyField,
    parentRecordId,
    limit = 5,
    enabled = true,
    include,
  } = options

  const isEnabled = !!collectionName && !!foreignKeyField && !!parentRecordId && enabled

  const {
    data: response,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['related-records', collectionName, foreignKeyField, parentRecordId, limit, include],
    queryFn: () =>
      fetchRelatedRecords(
        apiClient,
        collectionName!,
        foreignKeyField!,
        parentRecordId!,
        limit,
        include
      ),
    enabled: isEnabled,
    staleTime: 30 * 1000, // 30 seconds
  })

  return {
    data: response?.paginated.data ?? [],
    total: response?.paginated.total ?? 0,
    isLoading: isEnabled && isLoading,
    error: error as Error | null,
    refetch,
    rawResponse: response?.rawResponse,
  }
}
