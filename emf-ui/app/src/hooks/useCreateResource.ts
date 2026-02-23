/**
 * useCreateResource Hook
 *
 * Generic hook for creating a new resource in any collection.
 * Wraps the data in JSON:API format and invalidates the collection cache on success.
 */

import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useApi } from '../context/ApiContext'
import { wrapResource, unwrapResource } from '../utils/jsonapi'
import type { CollectionRecord } from './useCollectionRecords'

export interface UseCreateResourceOptions {
  /** Collection name (URL slug) */
  resource: string
  /** Fields that are relationships (field name → target collection type) */
  relationshipFields?: Record<string, string>
  /** Callback on successful creation */
  onSuccess?: (record: CollectionRecord) => void
  /** Callback on error */
  onError?: (error: Error) => void
}

export interface UseCreateResourceReturn {
  /** Trigger the create mutation (fire-and-forget) */
  mutate: (data: Record<string, unknown>) => void
  /** Trigger the create mutation and await the result */
  mutateAsync: (data: Record<string, unknown>) => Promise<CollectionRecord>
  /** True while the mutation is in progress */
  isPending: boolean
}

/**
 * Generic hook to create a new resource in any collection.
 *
 * ```tsx
 * const { mutateAsync: createUser, isPending } = useCreateResource({ resource: 'users' })
 * await createUser({ name: 'Alice', email: 'alice@example.com' })
 * ```
 */
export function useCreateResource(options: UseCreateResourceOptions): UseCreateResourceReturn {
  const { resource, relationshipFields, onSuccess, onError } = options
  const { apiClient } = useApi()
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: async (data: Record<string, unknown>) => {
      const body = wrapResource(resource, data, undefined, relationshipFields)
      const response = await apiClient.post(`/api/${resource}`, body)
      return unwrapResource<CollectionRecord>(response)
    },
    onSuccess: (record) => {
      queryClient.invalidateQueries({ queryKey: ['resources', resource] })
      queryClient.invalidateQueries({ queryKey: ['collection-records', resource] })
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
