/**
 * useRecord Hook
 *
 * Fetches a single record by ID from a collection via JSON:API endpoint.
 * Automatically unwraps JSON:API response format to a flat object.
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
}

export interface UseRecordReturn {
  record: CollectionRecord | undefined
  isLoading: boolean
  error: Error | null
  refetch: () => void
}

/**
 * Hook to fetch a single record from a collection.
 *
 * @param options - Collection name, record ID, and enabled flag
 * @returns Record data, loading/error states, refetch function
 */
export function useRecord(options: UseRecordOptions): UseRecordReturn {
  const { apiClient } = useApi()
  const { collectionName, recordId, enabled = true } = options

  const {
    data: record,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['record', collectionName, recordId],
    queryFn: async () => {
      const response = await apiClient.get(`/api/${collectionName}/${recordId}`)
      return unwrapResource<CollectionRecord>(response)
    },
    enabled: !!collectionName && !!recordId && enabled,
    staleTime: 30 * 1000, // 30 seconds
  })

  return {
    record,
    isLoading,
    error: error as Error | null,
    refetch,
  }
}
