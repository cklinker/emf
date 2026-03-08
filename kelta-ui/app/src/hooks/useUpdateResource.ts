/**
 * useUpdateResource Hook
 *
 * Generic hook for updating an existing resource in any collection.
 * Supports both full update (PUT) and partial update (PATCH).
 * Wraps data in JSON:API format and invalidates caches on success.
 */

import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useApi } from '../context/ApiContext'
import { wrapResource, unwrapResource } from '../utils/jsonapi'
import type { CollectionRecord } from './useCollectionRecords'

export interface UseUpdateResourceOptions {
  /** Collection name (URL slug) */
  resource: string
  /** Fields that are relationships (field name → target collection type) */
  relationshipFields?: Record<string, string>
  /** HTTP method: 'PATCH' (default, partial update) or 'PUT' (full replace) */
  method?: 'PATCH' | 'PUT'
  /** Callback on successful update */
  onSuccess?: (record: CollectionRecord) => void
  /** Callback on error */
  onError?: (error: Error) => void
}

export interface UpdateParams {
  /** Record ID to update */
  id: string
  /** Data fields to update */
  data: Record<string, unknown>
}

export interface UseUpdateResourceReturn {
  /** Trigger the update mutation (fire-and-forget) */
  mutate: (params: UpdateParams) => void
  /** Trigger the update mutation and await the result */
  mutateAsync: (params: UpdateParams) => Promise<CollectionRecord>
  /** True while the mutation is in progress */
  isPending: boolean
}

/**
 * Generic hook to update a resource in any collection.
 *
 * ```tsx
 * const { mutateAsync: updateUser } = useUpdateResource({ resource: 'users' })
 * await updateUser({ id: userId, data: { name: 'Alice Updated' } })
 * ```
 */
export function useUpdateResource(options: UseUpdateResourceOptions): UseUpdateResourceReturn {
  const { resource, relationshipFields, method = 'PATCH', onSuccess, onError } = options
  const { apiClient } = useApi()
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: async ({ id, data }: UpdateParams) => {
      const body = wrapResource(resource, data, id, relationshipFields)
      const response =
        method === 'PUT'
          ? await apiClient.put(`/api/${resource}/${id}`, body)
          : await apiClient.patch(`/api/${resource}/${id}`, body)
      return unwrapResource<CollectionRecord>(response)
    },
    onSuccess: (record) => {
      queryClient.invalidateQueries({ queryKey: ['resources', resource] })
      queryClient.invalidateQueries({ queryKey: ['collection-records', resource] })
      queryClient.invalidateQueries({ queryKey: ['resource', resource] })
      queryClient.invalidateQueries({ queryKey: ['record', resource] })
      onSuccess?.(record)
    },
    onError: (error: Error) => {
      onError?.(error)
    },
  })

  return {
    mutate: mutation.mutate,
    mutateAsync: mutation.mutateAsync,
    isPending: mutation.isPending,
  }
}
