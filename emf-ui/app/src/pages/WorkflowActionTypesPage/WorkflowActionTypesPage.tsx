import React, { useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useApi } from '../../context/ApiContext'
import { useToast, LoadingSpinner, ErrorMessage } from '../../components'

import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'

interface WorkflowActionType {
  id: string
  key: string
  name: string
  description: string | null
  category: string
  configSchema: string | null
  icon: string | null
  handlerClass: string
  active: boolean
  builtIn: boolean
  handlerAvailable: boolean
  createdAt: string
  updatedAt: string
}

export interface WorkflowActionTypesPageProps {
  testId?: string
}

const CATEGORY_COLORS: Record<string, string> = {
  DATA: 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300',
  COMMUNICATION: 'bg-purple-100 text-purple-800 dark:bg-purple-950 dark:text-purple-300',
  INTEGRATION: 'bg-orange-100 text-orange-800 dark:bg-orange-950 dark:text-orange-300',
  FLOW_CONTROL: 'bg-cyan-100 text-cyan-800 dark:bg-cyan-950 dark:text-cyan-300',
}

export function WorkflowActionTypesPage({
  testId = 'workflow-action-types-page',
}: WorkflowActionTypesPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  const {
    data: actionTypes,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['workflow-action-types'],
    queryFn: () => apiClient.getList<WorkflowActionType>('/api/workflow-action-types'),
  })

  const toggleMutation = useMutation({
    mutationFn: (actionType: WorkflowActionType) =>
      apiClient.patchResource<WorkflowActionType>(`/api/workflow-action-types/${actionType.id}`, {
        active: !actionType.active,
      }),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['workflow-action-types'] })
      const status = data?.active ? 'activated' : 'deactivated'
      showToast(`Action type ${status} successfully`, 'success')
    },
    onError: (err: Error) => {
      showToast(err.message || 'Failed to toggle action type', 'error')
    },
  })

  const handleToggle = useCallback(
    (actionType: WorkflowActionType) => {
      toggleMutation.mutate(actionType)
    },
    [toggleMutation]
  )

  const actionTypeList: WorkflowActionType[] = actionTypes ?? []

  // Group by category
  const grouped = actionTypeList.reduce<Record<string, WorkflowActionType[]>>((acc, at) => {
    const cat = at.category || 'OTHER'
    if (!acc[cat]) acc[cat] = []
    acc[cat].push(at)
    return acc
  }, {})

  const categoryOrder = ['DATA', 'COMMUNICATION', 'INTEGRATION', 'FLOW_CONTROL', 'OTHER']
  const sortedCategories = categoryOrder.filter((c) => grouped[c] && grouped[c].length > 0)

  if (isLoading) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <div className="flex min-h-[400px] items-center justify-center">
          <LoadingSpinner size="large" label="Loading action types..." />
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <ErrorMessage
          error={error instanceof Error ? error : new Error('An error occurred')}
          onRetry={() => refetch()}
        />
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-foreground">Workflow Action Types</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            View and manage available workflow action types. Toggle action types to enable or
            disable them for workflow rules.
          </p>
        </div>
      </header>

      {actionTypeList.length === 0 ? (
        <div
          className="rounded-lg border border-border bg-card py-16 text-center text-muted-foreground"
          data-testid="empty-state"
        >
          <p>No action types found.</p>
        </div>
      ) : (
        <div className="space-y-6">
          {sortedCategories.map((category) => (
            <div key={category}>
              <h2 className="mb-3 text-sm font-semibold uppercase tracking-wider text-muted-foreground">
                {category.replace(/_/g, ' ')}
              </h2>
              <div className="overflow-x-auto rounded-lg border border-border bg-card">
                <table
                  className="w-full border-collapse"
                  role="grid"
                  aria-label={`${category} Action Types`}
                  data-testid={`action-types-table-${category.toLowerCase()}`}
                >
                  <thead>
                    <tr role="row" className="bg-muted">
                      <th
                        role="columnheader"
                        scope="col"
                        className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                      >
                        Key
                      </th>
                      <th
                        role="columnheader"
                        scope="col"
                        className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                      >
                        Name
                      </th>
                      <th
                        role="columnheader"
                        scope="col"
                        className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                      >
                        Description
                      </th>
                      <th
                        role="columnheader"
                        scope="col"
                        className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                      >
                        Category
                      </th>
                      <th
                        role="columnheader"
                        scope="col"
                        className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                      >
                        Handler
                      </th>
                      <th
                        role="columnheader"
                        scope="col"
                        className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                      >
                        Status
                      </th>
                      <th
                        role="columnheader"
                        scope="col"
                        className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                      >
                        Actions
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {grouped[category].map((actionType, index) => (
                      <tr
                        key={actionType.id}
                        role="row"
                        className="border-b border-border transition-colors last:border-b-0 hover:bg-muted/50"
                        data-testid={`action-type-row-${actionType.key}`}
                      >
                        <td role="gridcell" className="px-4 py-3 text-sm font-mono text-foreground">
                          {actionType.key}
                        </td>
                        <td
                          role="gridcell"
                          className="px-4 py-3 text-sm font-medium text-foreground"
                        >
                          {actionType.name}
                        </td>
                        <td
                          role="gridcell"
                          className="px-4 py-3 text-sm text-muted-foreground max-w-[300px] truncate"
                        >
                          {actionType.description || '-'}
                        </td>
                        <td role="gridcell" className="px-4 py-3 text-sm">
                          <span
                            className={cn(
                              'inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold',
                              CATEGORY_COLORS[actionType.category] ||
                                'bg-muted text-muted-foreground'
                            )}
                          >
                            {actionType.category}
                          </span>
                        </td>
                        <td role="gridcell" className="px-4 py-3 text-sm">
                          <span
                            className={cn(
                              'inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold',
                              actionType.handlerAvailable
                                ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                                : 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300'
                            )}
                          >
                            {actionType.handlerAvailable ? 'Available' : 'Missing'}
                          </span>
                        </td>
                        <td role="gridcell" className="px-4 py-3 text-sm">
                          <span
                            className={cn(
                              'inline-block rounded-full px-3 py-1 text-xs font-semibold',
                              actionType.active
                                ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                                : 'bg-muted text-muted-foreground'
                            )}
                          >
                            {actionType.active ? 'Active' : 'Inactive'}
                          </span>
                        </td>
                        <td role="gridcell" className="px-4 py-3 text-right text-sm">
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => handleToggle(actionType)}
                            disabled={toggleMutation.isPending}
                            aria-label={`${actionType.active ? 'Deactivate' : 'Activate'} ${actionType.name}`}
                            data-testid={`toggle-button-${index}`}
                          >
                            {actionType.active ? 'Deactivate' : 'Activate'}
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default WorkflowActionTypesPage
