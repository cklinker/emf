/**
 * useRecordMutation Hook
 *
 * Provides create, update, patch, and delete mutations for collection records.
 * All mutations use JSON:API request wrapping and invalidate the collection
 * records cache on success.
 */

import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useApi } from '../context/ApiContext'
import { wrapResource, unwrapResource } from '../utils/jsonapi'
import type { CollectionRecord } from './useCollectionRecords'

export interface UseRecordMutationOptions {
  /** Collection name (URL slug) */
  collectionName: string
  /** Callback on successful mutation */
  onSuccess?: () => void
  /** Callback on mutation error */
  onError?: (error: Error) => void
}

export interface UseRecordMutationReturn {
  /** Create a new record */
  create: {
    mutate: (data: Record<string, unknown>) => void
    mutateAsync: (data: Record<string, unknown>) => Promise<CollectionRecord>
    isPending: boolean
  }
  /** Full update (PUT) of a record */
  update: {
    mutate: (params: { id: string; data: Record<string, unknown> }) => void
    mutateAsync: (params: {
      id: string
      data: Record<string, unknown>
    }) => Promise<CollectionRecord>
    isPending: boolean
  }
  /** Partial update (PATCH) of a record */
  patch: {
    mutate: (params: { id: string; data: Record<string, unknown> }) => void
    mutateAsync: (params: {
      id: string
      data: Record<string, unknown>
    }) => Promise<CollectionRecord>
    isPending: boolean
  }
  /** Delete a record by ID */
  remove: {
    mutate: (id: string) => void
    mutateAsync: (id: string) => Promise<void>
    isPending: boolean
  }
  /** Bulk delete records by IDs */
  bulkDelete: {
    mutate: (ids: string[]) => void
    mutateAsync: (ids: string[]) => Promise<void>
    isPending: boolean
  }
}

/**
 * Hook providing CRUD mutations for collection records.
 *
 * All write operations use JSON:API request/response wrapping.
 * On success, the collection records query cache is invalidated.
 */
export function useRecordMutation(options: UseRecordMutationOptions): UseRecordMutationReturn {
  const { collectionName, onSuccess, onError } = options
  const { apiClient } = useApi()
  const queryClient = useQueryClient()

  const invalidateRecords = () => {
    queryClient.invalidateQueries({ queryKey: ['collection-records', collectionName] })
  }

  const createMutation = useMutation({
    mutationFn: async (data: Record<string, unknown>) => {
      const body = wrapResource(collectionName, data)
      const response = await apiClient.post(`/api/${collectionName}`, body)
      return unwrapResource<CollectionRecord>(response)
    },
    onSuccess: () => {
      invalidateRecords()
      onSuccess?.()
    },
    onError: (error: Error) => {
      onError?.(error)
    },
  })

  const updateMutation = useMutation({
    mutationFn: async ({ id, data }: { id: string; data: Record<string, unknown> }) => {
      const body = wrapResource(collectionName, data, id)
      const response = await apiClient.put(`/api/${collectionName}/${id}`, body)
      return unwrapResource<CollectionRecord>(response)
    },
    onSuccess: () => {
      invalidateRecords()
      onSuccess?.()
    },
    onError: (error: Error) => {
      onError?.(error)
    },
  })

  const patchMutation = useMutation({
    mutationFn: async ({ id, data }: { id: string; data: Record<string, unknown> }) => {
      const body = wrapResource(collectionName, data, id)
      const response = await apiClient.patch(`/api/${collectionName}/${id}`, body)
      return unwrapResource<CollectionRecord>(response)
    },
    onSuccess: () => {
      invalidateRecords()
      onSuccess?.()
    },
    onError: (error: Error) => {
      onError?.(error)
    },
  })

  const removeMutation = useMutation({
    mutationFn: async (id: string) => {
      await apiClient.delete(`/api/${collectionName}/${id}`)
    },
    onSuccess: () => {
      invalidateRecords()
      onSuccess?.()
    },
    onError: (error: Error) => {
      onError?.(error)
    },
  })

  const bulkDeleteMutation = useMutation({
    mutationFn: async (ids: string[]) => {
      await apiClient.post(`/api/${collectionName}/bulk-delete`, { ids })
    },
    onSuccess: () => {
      invalidateRecords()
      onSuccess?.()
    },
    onError: (error: Error) => {
      onError?.(error)
    },
  })

  return {
    create: {
      mutate: createMutation.mutate,
      mutateAsync: createMutation.mutateAsync,
      isPending: createMutation.isPending,
    },
    update: {
      mutate: updateMutation.mutate,
      mutateAsync: updateMutation.mutateAsync,
      isPending: updateMutation.isPending,
    },
    patch: {
      mutate: patchMutation.mutate,
      mutateAsync: patchMutation.mutateAsync,
      isPending: patchMutation.isPending,
    },
    remove: {
      mutate: removeMutation.mutate,
      mutateAsync: removeMutation.mutateAsync,
      isPending: removeMutation.isPending,
    },
    bulkDelete: {
      mutate: bulkDeleteMutation.mutate,
      mutateAsync: bulkDeleteMutation.mutateAsync,
      isPending: bulkDeleteMutation.isPending,
    },
  }
}
