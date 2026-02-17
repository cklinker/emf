/**
 * useQuickActions Hook
 *
 * Fetches quick action definitions for a collection from the control plane.
 * Returns actions filtered by context (record vs list) for display in
 * RecordHeader or ListViewToolbar.
 *
 * Falls back to an empty list when the endpoint is unavailable,
 * since the backend quick actions API may not yet be implemented.
 */

import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useApi } from '@/context/ApiContext'
import type { ApiClient } from '@/services/apiClient'
import type { QuickActionDefinition, QuickActionContext } from '@/types/quickActions'

export interface UseQuickActionsOptions {
  /** Collection name to fetch actions for */
  collectionName: string | undefined
  /** Filter by context (record, list, or both). Defaults to showing all. */
  context?: QuickActionContext
}

export interface UseQuickActionsReturn {
  /** Available quick actions, sorted by sortOrder */
  actions: QuickActionDefinition[]
  /** Whether actions are still loading */
  isLoading: boolean
  /** Error from fetching, if any */
  error: Error | null
}

/**
 * Fetch quick actions for a collection from the control plane.
 */
async function fetchQuickActions(
  apiClient: ApiClient,
  collectionName: string
): Promise<QuickActionDefinition[]> {
  try {
    const response = await apiClient.get<QuickActionDefinition[]>(
      `/control/quick-actions/${encodeURIComponent(collectionName)}`
    )
    return Array.isArray(response) ? response : []
  } catch {
    // Endpoint not yet implemented — return empty list
    return []
  }
}

/**
 * Hook to fetch quick actions for a collection.
 *
 * @param options - Collection name and optional context filter
 * @returns Quick action definitions and loading state
 */
export function useQuickActions(options: UseQuickActionsOptions): UseQuickActionsReturn {
  const { collectionName, context } = options
  const { apiClient } = useApi()

  const { data, isLoading, error } = useQuery({
    queryKey: ['quick-actions', collectionName],
    queryFn: () => fetchQuickActions(apiClient, collectionName!),
    enabled: !!collectionName,
    staleTime: 5 * 60 * 1000, // 5 minutes
    retry: false,
  })

  const actions = useMemo(() => {
    if (!data) return []
    const filtered = context
      ? data.filter((a) => a.context === context || a.context === 'both')
      : data
    return filtered.sort((a, b) => a.sortOrder - b.sortOrder)
  }, [data, context])

  return {
    actions,
    isLoading,
    error: error as Error | null,
  }
}
