/**
 * useQuickActions Hook
 *
 * Returns the active quick-action definitions configured for a collection, fetched from the
 * `quick-actions` system collection via JSON:API and mapped onto `QuickActionDefinition`.
 * (The stored `actionType` maps to the client `type` — the field is renamed server-side to
 * avoid clashing with the JSON:API resource `type`.)
 */

import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useApi } from '../context/ApiContext'
import type {
  QuickActionDefinition,
  QuickActionConfig,
  QuickActionContext,
  QuickActionType,
} from '@/types/quickActions'

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

/** A `quick-actions` row as returned by JSON:API (attributes flattened by apiClient.getList). */
interface QuickActionRow {
  id: string
  label: string
  icon?: string
  actionType: QuickActionType
  context: QuickActionContext
  sortOrder?: number
  requiresConfirmation?: boolean
  confirmationMessage?: string
  config?: QuickActionConfig | null
}

function toDefinition(row: QuickActionRow): QuickActionDefinition {
  return {
    id: row.id,
    label: row.label,
    icon: row.icon,
    type: row.actionType,
    context: row.context,
    sortOrder: row.sortOrder ?? 0,
    requiresConfirmation: row.requiresConfirmation,
    confirmationMessage: row.confirmationMessage,
    // `config` is a typed union keyed by its own `type`; default to a benign custom shape.
    config: row.config ?? ({ type: 'custom', componentName: '' } as QuickActionConfig),
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
    queryFn: async () => {
      const query = `filter[collectionName][eq]=${encodeURIComponent(
        collectionName ?? ''
      )}&filter[active][eq]=true`
      // The quick-actions collection may not be provisioned for every tenant yet — treat a
      // missing/empty result as "no actions" rather than surfacing an error in the record header.
      try {
        return await apiClient.getList<QuickActionRow>(`/api/quick-actions?${query}`)
      } catch {
        return [] as QuickActionRow[]
      }
    },
    enabled: !!collectionName,
    staleTime: 5 * 60 * 1000, // 5 minutes
    retry: false,
  })

  const actions = useMemo(() => {
    if (!data) return []
    const defs = data.map(toDefinition)
    const filtered = context
      ? defs.filter((a) => a.context === context || a.context === 'both')
      : defs
    return filtered.sort((a, b) => a.sortOrder - b.sortOrder)
  }, [data, context])

  return {
    actions,
    isLoading,
    error: error as Error | null,
  }
}
