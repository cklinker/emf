/**
 * useScriptExecution Hook
 *
 * Executes server-side scripts via the control plane API and handles
 * the result actions (refresh, redirect, toast, open record).
 *
 * Scripts are executed with a record context (collection, record ID,
 * record data) and optional user-provided parameters.
 *
 * Falls back gracefully when the backend endpoint is unavailable.
 */

import { useCallback } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { useApi } from '@/context/ApiContext'
import type { ScriptExecutionResult, QuickActionExecutionContext } from '@/types/quickActions'

export interface ExecuteScriptParams {
  /** Script ID to execute */
  scriptId: string
  /** Execution context (record, collection, etc.) */
  context: QuickActionExecutionContext
  /** Optional parameters from user input */
  parameters?: Record<string, unknown>
}

export interface UseScriptExecutionReturn {
  /** Execute a script */
  execute: (params: ExecuteScriptParams) => Promise<ScriptExecutionResult>
  /** Whether a script is currently running */
  isPending: boolean
  /** Error from the last execution, if any */
  error: Error | null
}

/**
 * Hook for executing server-side scripts and handling their return actions.
 *
 * After a script executes, the hook automatically handles the result:
 * - `refresh` → invalidates React Query caches to reload data
 * - `redirect` → navigates to the specified URL
 * - `toast` → shows a toast notification
 * - `open_record` → navigates to a record detail page
 * - `none` → does nothing
 *
 * @returns Script execution function and state
 */
export function useScriptExecution(): UseScriptExecutionReturn {
  const { apiClient } = useApi()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const handleResultAction = useCallback(
    (result: ScriptExecutionResult, context: QuickActionExecutionContext) => {
      if (!result.action) {
        // No action specified — show success/error toast based on result
        if (result.success) {
          toast.success(result.message || 'Action completed successfully')
        } else {
          toast.error(result.message || 'Action failed')
        }
        return
      }

      switch (result.action.type) {
        case 'refresh':
          // Invalidate relevant queries to refresh data
          queryClient.invalidateQueries({
            queryKey: ['collection-records', context.collectionName],
          })
          if (context.recordId) {
            queryClient.invalidateQueries({
              queryKey: ['record', context.collectionName, context.recordId],
            })
          }
          if (result.message) {
            toast.success(result.message)
          }
          break

        case 'redirect':
          navigate(result.action.url)
          break

        case 'toast': {
          const variant = result.action.variant || 'default'
          if (variant === 'success') {
            toast.success(result.action.message)
          } else if (variant === 'error') {
            toast.error(result.action.message)
          } else {
            toast(result.action.message)
          }
          break
        }

        case 'open_record': {
          const { collection, recordId } = result.action
          const basePath = `/${context.tenantSlug}/app`
          navigate(`${basePath}/o/${collection}/${recordId}`)
          break
        }

        case 'none':
          // Do nothing
          break
      }
    },
    [navigate, queryClient]
  )

  const mutation = useMutation({
    mutationFn: async (params: ExecuteScriptParams): Promise<ScriptExecutionResult> => {
      try {
        const response = await apiClient.post<ScriptExecutionResult>(
          `/control/scripts/${encodeURIComponent(params.scriptId)}/execute`,
          {
            collectionName: params.context.collectionName,
            recordId: params.context.recordId,
            recordData: params.context.record,
            selectedIds: params.context.selectedIds,
            parameters: params.parameters || {},
          }
        )
        return response
      } catch (error) {
        // If the endpoint doesn't exist yet, return a friendly error
        return {
          success: false,
          message:
            error instanceof Error
              ? error.message
              : 'Script execution failed. The scripts API may not be available.',
        }
      }
    },
  })

  const execute = useCallback(
    async (params: ExecuteScriptParams): Promise<ScriptExecutionResult> => {
      const result = await mutation.mutateAsync(params)
      handleResultAction(result, params.context)
      return result
    },
    [mutation, handleResultAction]
  )

  return {
    execute,
    isPending: mutation.isPending,
    error: mutation.error as Error | null,
  }
}
