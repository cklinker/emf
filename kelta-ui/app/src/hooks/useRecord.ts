/**
 * useRecord Hook
 *
 * Fetches a single record by ID from a collection via JSON:API endpoint.
 * Automatically unwraps JSON:API response format to a flat object.
 * Supports ?include= for fetching related resources in a single request.
 */

import { useQuery } from '@tanstack/react-query'
import { useApi } from '../context/ApiContext'
import { unwrapResource } from '../utils/jsonapi'
import type { CollectionRecord } from './useCollectionRecords'

export interface UseRecordOptions {
  /** Collection name (URL slug) */
  collectionName: string | undefined
  /** Record ID */
  recordId: string | undefined
  /** Whether the query is enabled */
  enabled?: boolean
  /** Comma-separated list of relationship names to include via JSON:API ?include= */
  include?: string
}

export interface UseRecordReturn {
  record: CollectionRecord | undefined
  isLoading: boolean
  error: Error | null
  refetch: () => void
  /** Raw JSON:API response for building display maps from included resources */
  rawResponse: unknown
}

/**
 * Hook to fetch a single record from a collection.
 *
 * @param options - Collection name, record ID, and enabled flag
 * @returns Record data, loading/error states, refetch function
 */
export function useRecord(options: UseRecordOptions): UseRecordReturn {
  const { apiClient } = useApi()
  const { collectionName, recordId, enabled = true, include } = options

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['record', collectionName, recordId, include],
    queryFn: async () => {
      const url = include
        ? `/api/${collectionName}/${recordId}?include=${encodeURIComponent(include)}`
        : `/api/${collectionName}/${recordId}`
      const response = await apiClient.get(url)
      const record = unwrapResource<CollectionRecord>(response)
      return { record, rawResponse: response }
    },
    enabled: !!collectionName && !!recordId && enabled,
    staleTime: 30 * 1000, // 30 seconds
  })

  return {
    record: data?.record,
    isLoading,
    error: error as Error | null,
    refetch,
    rawResponse: data?.rawResponse,
  }
}
