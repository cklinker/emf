/**
 * useResourceAction Hook
 *
 * Generic hook for executing custom actions on a resource.
 * Actions are dispatched via POST /api/{collection}/{id}/actions/{action}.
 *
 * Used for operations like activate, deactivate, clone, execute, etc.
 */

import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useApi } from '../context/ApiContext'

export interface UseResourceActionOptions {
  /** Collection name (URL slug) */
  resource: string
  /** Callback on successful action */
  onSuccess?: (result: unknown) => void
  /** Callback on error */
  onError?: (error: Error) => void
}

export interface ActionParams {
  /** Record ID to perform the action on */
  id: string
  /** Action name (e.g. "activate", "deactivate", "clone") */
  action: string
  /** Optional payload for the action */
  data?: Record<string, unknown>
}

export interface UseResourceActionReturn {
  /** Execute an action (fire-and-forget) */
  mutate: (params: ActionParams) => void
  /** Execute an action and await the result */
  mutateAsync: (params: ActionParams) => Promise<unknown>
  /** True while the action is in progress */
  isPending: boolean
}

/**
 * Generic hook to execute custom actions on resources.
 *
 * ```tsx
 * const { mutateAsync: executeAction } = useResourceAction({ resource: 'users' })
 *
 * // Activate a user
 * await executeAction({ id: userId, action: 'activate' })
 *
 * // Clone a profile with custom data
 * await executeAction({ id: profileId, action: 'clone', data: { newName: 'Copy of Admin' } })
 * ```
 */
export function useResourceAction(options: UseResourceActionOptions): UseResourceActionReturn {
  const { resource, onSuccess, onError } = options
  const { apiClient } = useApi()
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: async ({ id, action, data }: ActionParams) => {
      return apiClient.post(`/api/${resource}/${id}/actions/${action}`, data ?? {})
    },
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['resources', resource] })
      queryClient.invalidateQueries({ queryKey: ['collection-records', resource] })
      queryClient.invalidateQueries({ queryKey: ['resource', resource] })
      queryClient.invalidateQueries({ queryKey: ['record', resource] })
      onSuccess?.(result)
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
