/**
 * useQuickActions Hook
 *
 * Returns quick action definitions for a collection.
 * Returns an empty list — quick actions endpoint is not yet
 * available via JSON:API.
 */

import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
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
 * Return empty quick actions list.
 * Quick actions endpoint is not yet available via JSON:API.
 */
async function fetchQuickActions(): Promise<QuickActionDefinition[]> {
  // Quick actions are not yet available via JSON:API — return empty list
  return []
}

/**
 * Hook to fetch quick actions for a collection.
 *
 * @param options - Collection name and optional context filter
 * @returns Quick action definitions and loading state
 */
export function useQuickActions(options: UseQuickActionsOptions): UseQuickActionsReturn {
  const { collectionName, context } = options

  const { data, isLoading, error } = useQuery({
    queryKey: ['quick-actions', collectionName],
    queryFn: () => fetchQuickActions(),
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
