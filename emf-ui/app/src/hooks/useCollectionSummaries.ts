/**
 * useCollectionSummaries Hook
 *
 * Fetches a lightweight list of all active collections with only
 * id, name, and displayName from the /control/collections/summary
 * endpoint. This is the preferred way to get collection metadata
 * for sidebars, dropdowns, ID→name maps, and other UI components
 * that don't need full field definitions.
 *
 * Uses a shared React Query key so all consumers share one cache
 * entry with a 5-minute stale time.
 */

import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useApi } from '@/context/ApiContext'

/** Lightweight collection metadata */
export interface CollectionSummary {
  id: string
  name: string
  displayName: string
}

export interface UseCollectionSummariesReturn {
  /** Flat list of collection summaries */
  summaries: CollectionSummary[]
  /** Map of collection ID → summary for quick lookups */
  summaryMap: Record<string, CollectionSummary>
  /** Map of collection name → summary for quick lookups */
  summaryByName: Record<string, CollectionSummary>
  /** Whether the data is still loading */
  isLoading: boolean
  error: Error | null
}

/**
 * Hook to fetch lightweight collection summaries.
 * Shared across all consumers via a single React Query key.
 *
 * @returns Collection summaries, lookup maps, and loading state
 */
export function useCollectionSummaries(): UseCollectionSummariesReturn {
  const { apiClient } = useApi()

  const { data, isLoading, error } = useQuery({
    queryKey: ['collection-summaries'],
    queryFn: () => apiClient.get<CollectionSummary[]>('/control/collections/summary'),
    staleTime: 5 * 60 * 1000, // 5 minutes — collection metadata rarely changes
  })

  const summaries = useMemo(() => (Array.isArray(data) ? data : []), [data])

  const summaryMap = useMemo(() => {
    const map: Record<string, CollectionSummary> = {}
    for (const s of summaries) {
      map[s.id] = s
    }
    return map
  }, [summaries])

  const summaryByName = useMemo(() => {
    const map: Record<string, CollectionSummary> = {}
    for (const s of summaries) {
      map[s.name] = s
    }
    return map
  }, [summaries])

  return {
    summaries,
    summaryMap,
    summaryByName,
    isLoading,
    error: error as Error | null,
  }
}
