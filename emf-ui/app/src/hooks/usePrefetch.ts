/**
 * usePrefetch Hook
 *
 * Provides functions to prefetch record data on hover for instant navigation.
 * When a user hovers over a row in the data table, the record's detail
 * and schema are prefetched so the detail page loads instantly.
 */

import { useCallback, useRef } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useApi } from '../context/ApiContext'
import { unwrapResource } from '../utils/jsonapi'
import type { CollectionRecord } from './useCollectionRecords'

export interface UsePrefetchOptions {
  /** Collection name for building API paths */
  collectionName: string | undefined
  /** Debounce time in ms before prefetch fires (default: 150) */
  debounceMs?: number
}

export interface UsePrefetchReturn {
  /** Call this on mouseenter of a record row to prefetch its detail */
  prefetchRecord: (recordId: string) => void
  /** Call this on mouseleave to cancel pending prefetch */
  cancelPrefetch: () => void
}

/**
 * Hook for prefetching record data on hover.
 *
 * Uses a debounce timer to avoid unnecessary API calls when the user
 * quickly moves the mouse across many rows. The prefetched data is
 * stored in the React Query cache, so navigating to the detail page
 * shows the data instantly.
 */
export function usePrefetch(options: UsePrefetchOptions): UsePrefetchReturn {
  const { collectionName, debounceMs = 150 } = options
  const { apiClient } = useApi()
  const queryClient = useQueryClient()
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const prefetchRecord = useCallback(
    (recordId: string) => {
      if (!collectionName) return

      // Clear any pending prefetch
      if (timerRef.current) {
        clearTimeout(timerRef.current)
      }

      timerRef.current = setTimeout(() => {
        // Prefetch the single record
        queryClient.prefetchQuery({
          queryKey: ['record', collectionName, recordId],
          queryFn: async () => {
            const response = await apiClient.get(`/api/${collectionName}/${recordId}`)
            return unwrapResource<CollectionRecord>(response)
          },
          staleTime: 30 * 1000,
        })
      }, debounceMs)
    },
    [collectionName, apiClient, queryClient, debounceMs]
  )

  const cancelPrefetch = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current)
      timerRef.current = null
    }
  }, [])

  return { prefetchRecord, cancelPrefetch }
}
