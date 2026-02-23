/**
 * useDeleteResource Hook
 *
 * Generic hook for deleting a resource from any collection.
 * Supports single and bulk delete operations.
 * Invalidates collection caches on success.
 */

import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useApi } from '../context/ApiContext'

export interface UseDeleteResourceOptions {
  /** Collection name (URL slug) */
  resource: string
  /** Callback on successful deletion */
  onSuccess?: () => void
  /** Callback on error */
  onError?: (error: Error) => void
}

export interface UseDeleteResourceReturn {
  /** Delete a single resource by ID */
  mutate: (id: string) => void
  /** Delete a single resource by ID and await completion */
  mutateAsync: (id: string) => Promise<void>
  /** True while the delete is in progress */
  isPending: boolean
  /** Bulk delete multiple resources by ID */
  bulkDelete: {
    mutate: (ids: string[]) => void
    mutateAsync: (ids: string[]) => Promise<void>
    isPending: boolean
  }
}

/**
 * Generic hook to delete resources from any collection.
 *
 * ```tsx
 * const { mutateAsync: deleteUser, bulkDelete } = useDeleteResource({ resource: 'users' })
 *
 * // Single delete
 * await deleteUser(userId)
 *
 * // Bulk delete
 * await bulkDelete.mutateAsync([id1, id2, id3])
 * ```
 */
export function useDeleteResource(options: UseDeleteResourceOptions): UseDeleteResourceReturn {
  const { resource, onSuccess, onError } = options
  const { apiClient } = useApi()
  const queryClient = useQueryClient()

  const invalidateCaches = () => {
    queryClient.invalidateQueries({ queryKey: ['resources', resource] })
    queryClient.invalidateQueries({ queryKey: ['collection-records', resource] })
    queryClient.invalidateQueries({ queryKey: ['resource', resource] })
    queryClient.invalidateQueries({ queryKey: ['record', resource] })
  }

  const deleteMutation = useMutation({
    mutationFn: async (id: string) => {
      await apiClient.delete(`/api/${resource}/${id}`)
    },
    onSuccess: () => {
      invalidateCaches()
      onSuccess?.()
    },
    onError: (error: Error) => {
      onError?.(error)
    },
  })

  const bulkDeleteMutation = useMutation({
    mutationFn: async (ids: string[]) => {
      await apiClient.post(`/api/${resource}/bulk-delete`, { ids })
    },
    onSuccess: () => {
      invalidateCaches()
      onSuccess?.()
    },
    onError: (error: Error) => {
      onError?.(error)
    },
  })

  return {
    mutate: deleteMutation.mutate,
    mutateAsync: deleteMutation.mutateAsync,
    isPending: deleteMutation.isPending,
    bulkDelete: {
      mutate: bulkDeleteMutation.mutate,
      mutateAsync: bulkDeleteMutation.mutateAsync,
      isPending: bulkDeleteMutation.isPending,
    },
  }
}
