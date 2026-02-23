/**
 * useResource Hook
 *
 * Generic hook for fetching a single resource by ID from any collection.
 * Supports JSON:API includes for loading related resources in a single request.
 *
 * Works identically for system collections (users, profiles, etc.) and
 * user-defined collections (products, orders, etc.).
 */

import { useQuery } from '@tanstack/react-query'
import { useApi } from '../context/ApiContext'
import { unwrapResource } from '../utils/jsonapi'
import type { CollectionRecord } from './useCollectionRecords'

export interface UseResourceOptions {
  /** Collection name (URL slug), e.g. "users", "products" */
  resource: string | undefined
  /** Record ID */
  id: string | undefined
  /** Related resources to include (e.g. ["profile", "permissionSets"]) */
  include?: string[]
  /** Sparse fieldsets — fields to return per resource type */
  fields?: Record<string, string[]>
  /** Whether the query is enabled. Defaults to true. */
  enabled?: boolean
  /** Stale time in milliseconds. Defaults to 30000 (30s). */
  staleTime?: number
}

export interface UseResourceReturn<T extends CollectionRecord = CollectionRecord> {
  /** The flattened resource record, undefined while loading */
  data: T | undefined
  /** True while the initial fetch is loading */
  isLoading: boolean
  /** Error if the query failed, null otherwise */
  error: Error | null
  /** Manually refetch the data */
  refetch: () => void
}

/**
 * Generic hook to fetch a single resource from any collection.
 *
 * ```tsx
 * // Fetch a user with their profile included
 * const { data: user } = useResource({
 *   resource: 'users',
 *   id: userId,
 *   include: ['profile']
 * })
 *
 * // Fetch a product
 * const { data: product } = useResource({ resource: 'products', id: productId })
 * ```
 */
export function useResource<T extends CollectionRecord = CollectionRecord>(
  options: UseResourceOptions
): UseResourceReturn<T> {
  const { apiClient } = useApi()
  const { resource, id, include, fields, enabled = true, staleTime = 30 * 1000 } = options

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['resource', resource, id, include, fields],
    queryFn: async () => {
      const queryParams = new URLSearchParams()

      if (include && include.length > 0) {
        queryParams.set('include', include.join(','))
      }

      if (fields) {
        for (const [type, fieldList] of Object.entries(fields)) {
          queryParams.set(`fields[${type}]`, fieldList.join(','))
        }
      }

      const queryString = queryParams.toString()
      const url = queryString ? `/api/${resource}/${id}?${queryString}` : `/api/${resource}/${id}`

      const response = await apiClient.get(url)
      return unwrapResource<T>(response)
    },
    enabled: !!resource && !!id && enabled,
    staleTime,
  })

  return {
    data,
    isLoading,
    error: error as Error | null,
    refetch,
  }
}
