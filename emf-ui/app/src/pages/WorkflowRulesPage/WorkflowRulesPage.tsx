import React, { useState, useCallback, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import {
  useToast,
  ConfirmDialog,
  LoadingSpinner,
  ErrorMessage,
  ExecutionLogModal,
} from '../../components'
import type { LogColumn } from '../../components'

import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'

interface WorkflowRule {
  id: string
  name: string
  description: string | null
  collectionId: string | null
  triggerType: 'ON_CREATE' | 'ON_UPDATE' | 'ON_CREATE_OR_UPDATE' | 'ON_DELETE'
  active: boolean
  filterFormula: string | null
  executionOrder: number
  createdBy: string
  createdAt: string
  updatedAt: string
}

interface WorkflowRuleFormData {
  name: string
  description: string
  collectionId: string
  triggerType: 'ON_CREATE' | 'ON_UPDATE' | 'ON_CREATE_OR_UPDATE' | 'ON_DELETE'
  active: boolean
  filterFormula: string
  executionOrder: number
}

interface FormErrors {
  name?: string
  description?: string
  collectionId?: string
  executionOrder?: string
}

interface WorkflowExecutionLog {
  id: string
  recordId: string
  triggerType: string
  status: string
  actionsExecuted: number
  errorMessage: string | null
  executedAt: string
  durationMs: number | null
}

export interface WorkflowRulesPageProps {
  testId?: string
}

function validateForm(data: WorkflowRuleFormData): FormErrors {
  const errors: FormErrors = {}
  if (!data.name.trim()) {
    errors.name = 'Name is required'
  } else if (data.name.length > 100) {
    errors.name = 'Name must be 100 characters or fewer'
  }
  if (data.description && data.description.length > 500) {
    errors.description = 'Description must be 500 characters or fewer'
  }
  if (data.collectionId && data.collectionId.length > 100) {
    errors.collectionId = 'Collection ID must be 100 characters or fewer'
  }
  if (data.executionOrder < 0) {
    errors.executionOrder = 'Execution order must be 0 or greater'
  }
  return errors
}

interface WorkflowRuleFormProps {
  workflowRule?: WorkflowRule
  onSubmit: (data: WorkflowRuleFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function WorkflowRuleForm({
  workflowRule,
  onSubmit,
  onCancel,
  isSubmitting,
}: WorkflowRuleFormProps): React.ReactElement {
  const isEditing = !!workflowRule
  const [formData, setFormData] = useState<WorkflowRuleFormData>({
    name: workflowRule?.name ?? '',
    description: workflowRule?.description ?? '',
    collectionId: workflowRule?.collectionId ?? '',
    triggerType: workflowRule?.triggerType ?? 'ON_CREATE',
    active: workflowRule?.active ?? true,
    filterFormula: workflowRule?.filterFormula ?? '',
    executionOrder: workflowRule?.executionOrder ?? 0,
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const nameInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    (field: keyof WorkflowRuleFormData, value: string | boolean | number) => {
      setFormData((prev) => ({ ...prev, [field]: value }))
      if (errors[field as keyof FormErrors]) {
        setErrors((prev) => ({ ...prev, [field]: undefined }))
      }
    },
    [errors]
  )

  const handleBlur = useCallback(
    (field: keyof FormErrors) => {
      setTouched((prev) => ({ ...prev, [field]: true }))
      const validationErrors = validateForm(formData)
      if (validationErrors[field]) {
        setErrors((prev) => ({ ...prev, [field]: validationErrors[field] }))
      }
    },
    [formData]
  )

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault()
      const validationErrors = validateForm(formData)
      setErrors(validationErrors)
      setTouched({ name: true, description: true, collectionId: true, executionOrder: true })
      if (Object.keys(validationErrors).length === 0) {
        onSubmit(formData)
      }
    },
    [formData, onSubmit]
  )

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Escape') {
        onCancel()
      }
    },
    [onCancel]
  )

  const title = isEditing ? 'Edit Workflow Rule' : 'Create Workflow Rule'

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="workflow-rule-form-overlay"
      role="presentation"
    >
      <div
        className="w-full max-w-[600px] max-h-[90vh] overflow-y-auto rounded-lg bg-background shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="workflow-rule-form-title"
        data-testid="workflow-rule-form-modal"
      >
        <div className="flex items-center justify-between border-b border-border p-6">
          <h2 id="workflow-rule-form-title" className="text-lg font-semibold text-foreground">
            {title}
          </h2>
          <button
            type="button"
            className="rounded p-2 text-2xl leading-none text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            onClick={onCancel}
            aria-label="Close"
            data-testid="workflow-rule-form-close"
          >
            &times;
          </button>
        </div>
        <div className="p-6">
          <form className="flex flex-col gap-5" onSubmit={handleSubmit} noValidate>
            <div className="flex flex-col gap-2">
              <label htmlFor="workflow-rule-name" className="text-sm font-medium text-foreground">
                Name
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="workflow-rule-name"
                type="text"
                className={cn(
                  'rounded-md border border-border bg-background px-3 py-2.5 text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.name && errors.name && 'border-destructive'
                )}
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                onBlur={() => handleBlur('name')}
                placeholder="Enter workflow rule name"
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                disabled={isSubmitting}
                data-testid="workflow-rule-name-input"
              />
              {touched.name && errors.name && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="workflow-rule-description"
                className="text-sm font-medium text-foreground"
              >
                Description
              </label>
              <textarea
                id="workflow-rule-description"
                className={cn(
                  'min-h-[80px] resize-y rounded-md border border-border bg-background px-3 py-2.5 font-[inherit] text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.description && errors.description && 'border-destructive'
                )}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                onBlur={() => handleBlur('description')}
                placeholder="Enter workflow rule description"
                disabled={isSubmitting}
                rows={3}
                data-testid="workflow-rule-description-input"
              />
              {touched.description && errors.description && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.description}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="workflow-rule-collection-id"
                className="text-sm font-medium text-foreground"
              >
                Collection ID
              </label>
              <input
                id="workflow-rule-collection-id"
                type="text"
                className={cn(
                  'rounded-md border border-border bg-background px-3 py-2.5 text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.collectionId && errors.collectionId && 'border-destructive'
                )}
                value={formData.collectionId}
                onChange={(e) => handleChange('collectionId', e.target.value)}
                onBlur={() => handleBlur('collectionId')}
                placeholder="Enter collection ID"
                disabled={isSubmitting}
                data-testid="workflow-rule-collection-id-input"
              />
              {touched.collectionId && errors.collectionId && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.collectionId}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="workflow-rule-trigger-type"
                className="text-sm font-medium text-foreground"
              >
                Trigger Type
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <select
                id="workflow-rule-trigger-type"
                className={cn(
                  'rounded-md border border-border bg-background px-3 py-2.5 text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground'
                )}
                value={formData.triggerType}
                onChange={(e) =>
                  handleChange(
                    'triggerType',
                    e.target.value as
                      | 'ON_CREATE'
                      | 'ON_UPDATE'
                      | 'ON_CREATE_OR_UPDATE'
                      | 'ON_DELETE'
                  )
                }
                disabled={isSubmitting}
                data-testid="workflow-rule-trigger-type-input"
              >
                <option value="ON_CREATE">ON_CREATE</option>
                <option value="ON_UPDATE">ON_UPDATE</option>
                <option value="ON_CREATE_OR_UPDATE">ON_CREATE_OR_UPDATE</option>
                <option value="ON_DELETE">ON_DELETE</option>
              </select>
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="workflow-rule-filter-formula"
                className="text-sm font-medium text-foreground"
              >
                Filter Formula
              </label>
              <textarea
                id="workflow-rule-filter-formula"
                className={cn(
                  'min-h-[80px] resize-y rounded-md border border-border bg-background px-3 py-2.5 font-[inherit] text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground'
                )}
                value={formData.filterFormula}
                onChange={(e) => handleChange('filterFormula', e.target.value)}
                placeholder="Enter filter formula"
                disabled={isSubmitting}
                rows={3}
                data-testid="workflow-rule-filter-formula-input"
              />
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="workflow-rule-execution-order"
                className="text-sm font-medium text-foreground"
              >
                Execution Order
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                id="workflow-rule-execution-order"
                type="number"
                className={cn(
                  'rounded-md border border-border bg-background px-3 py-2.5 text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.executionOrder && errors.executionOrder && 'border-destructive'
                )}
                value={formData.executionOrder}
                onChange={(e) => handleChange('executionOrder', parseInt(e.target.value, 10) || 0)}
                onBlur={() => handleBlur('executionOrder')}
                min={0}
                disabled={isSubmitting}
                data-testid="workflow-rule-execution-order-input"
              />
              {touched.executionOrder && errors.executionOrder && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.executionOrder}
                </span>
              )}
            </div>

            <div className="flex items-center gap-2">
              <input
                id="workflow-rule-active"
                type="checkbox"
                className="h-4 w-4 accent-primary"
                checked={formData.active}
                onChange={(e) => handleChange('active', e.target.checked)}
                disabled={isSubmitting}
                data-testid="workflow-rule-active-input"
              />
              <label htmlFor="workflow-rule-active" className="text-sm font-medium text-foreground">
                Active
              </label>
            </div>

            <div className="mt-2 flex justify-end gap-3 border-t border-border pt-4">
              <Button
                type="button"
                variant="outline"
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="workflow-rule-form-cancel"
              >
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting} data-testid="workflow-rule-form-submit">
                {isSubmitting ? 'Saving...' : 'Save'}
              </Button>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}

export function WorkflowRulesPage({
  testId = 'workflow-rules-page',
}: WorkflowRulesPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingWorkflowRule, setEditingWorkflowRule] = useState<WorkflowRule | undefined>(
    undefined
  )
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [workflowRuleToDelete, setWorkflowRuleToDelete] = useState<WorkflowRule | null>(null)
  const [logsItemId, setLogsItemId] = useState<string | null>(null)
  const [logsItemName, setLogsItemName] = useState('')

  const {
    data: logs,
    isLoading: logsLoading,
    error: logsError,
  } = useQuery({
    queryKey: ['workflow-rule-logs', logsItemId],
    queryFn: () =>
      apiClient.get<WorkflowExecutionLog[]>(`/control/workflow-rules/${logsItemId}/logs`),
    enabled: !!logsItemId,
  })

  const logColumns: LogColumn<WorkflowExecutionLog>[] = [
    { key: 'status', header: 'Status' },
    { key: 'triggerType', header: 'Trigger Type' },
    { key: 'recordId', header: 'Record ID' },
    { key: 'actionsExecuted', header: 'Actions' },
    {
      key: 'durationMs',
      header: 'Duration',
      render: (v) => (v != null ? `${v}ms` : '-'),
    },
    { key: 'errorMessage', header: 'Error' },
    {
      key: 'executedAt',
      header: 'Executed At',
      render: (v) =>
        v
          ? formatDate(new Date(v as string), {
              year: 'numeric',
              month: 'short',
              day: 'numeric',
              hour: '2-digit',
              minute: '2-digit',
            })
          : '-',
    },
  ]

  const {
    data: workflowRules,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['workflow-rules'],
    queryFn: () => apiClient.get<WorkflowRule[]>(`/control/workflow-rules`),
  })

  const workflowRuleList: WorkflowRule[] = workflowRules ?? []

  const createMutation = useMutation({
    mutationFn: (data: WorkflowRuleFormData) =>
      apiClient.post<WorkflowRule>(`/control/workflow-rules?userId=system`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workflow-rules'] })
      showToast('Workflow rule created successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: WorkflowRuleFormData }) =>
      apiClient.put<WorkflowRule>(`/control/workflow-rules/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workflow-rules'] })
      showToast('Workflow rule updated successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/control/workflow-rules/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workflow-rules'] })
      showToast('Workflow rule deleted successfully', 'success')
      setDeleteDialogOpen(false)
      setWorkflowRuleToDelete(null)
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const handleCreate = useCallback(() => {
    setEditingWorkflowRule(undefined)
    setIsFormOpen(true)
  }, [])

  const handleEdit = useCallback((workflowRule: WorkflowRule) => {
    setEditingWorkflowRule(workflowRule)
    setIsFormOpen(true)
  }, [])

  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingWorkflowRule(undefined)
  }, [])

  const handleFormSubmit = useCallback(
    (data: WorkflowRuleFormData) => {
      if (editingWorkflowRule) {
        updateMutation.mutate({ id: editingWorkflowRule.id, data })
      } else {
        createMutation.mutate(data)
      }
    },
    [editingWorkflowRule, createMutation, updateMutation]
  )

  const handleDeleteClick = useCallback((workflowRule: WorkflowRule) => {
    setWorkflowRuleToDelete(workflowRule)
    setDeleteDialogOpen(true)
  }, [])

  const handleDeleteConfirm = useCallback(() => {
    if (workflowRuleToDelete) {
      deleteMutation.mutate(workflowRuleToDelete.id)
    }
  }, [workflowRuleToDelete, deleteMutation])

  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setWorkflowRuleToDelete(null)
  }, [])

  if (isLoading) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <div className="flex min-h-[400px] items-center justify-center">
          <LoadingSpinner size="large" label="Loading workflow rules..." />
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

  const isSubmitting = createMutation.isPending || updateMutation.isPending

  return (
    <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-foreground">Workflow Rules</h1>
        <Button
          type="button"
          onClick={handleCreate}
          aria-label="Create Workflow Rule"
          data-testid="add-workflow-rule-button"
        >
          Create Workflow Rule
        </Button>
      </header>

      {workflowRuleList.length === 0 ? (
        <div
          className="rounded-lg border border-border bg-card py-16 text-center text-muted-foreground"
          data-testid="empty-state"
        >
          <p>No workflow rules found.</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border bg-card">
          <table
            className="w-full border-collapse"
            role="grid"
            aria-label="Workflow Rules"
            data-testid="workflow-rules-table"
          >
            <thead>
              <tr role="row" className="bg-muted">
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
                  Collection ID
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Trigger Type
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Execution Order
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Active
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Created
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
              {workflowRuleList.map((workflowRule, index) => (
                <tr
                  key={workflowRule.id}
                  role="row"
                  className="border-b border-border transition-colors last:border-b-0 hover:bg-muted/50"
                  data-testid={`workflow-rule-row-${index}`}
                >
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    {workflowRule.name}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    <span className="max-w-[200px] truncate">
                      {workflowRule.collectionId || '-'}
                    </span>
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    <span className="inline-block rounded-full bg-muted px-3 py-1 text-xs font-semibold uppercase tracking-wider text-primary">
                      {workflowRule.triggerType}
                    </span>
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    {workflowRule.executionOrder}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    <span
                      className={cn(
                        'inline-block rounded-full px-3 py-1 text-xs font-semibold',
                        workflowRule.active
                          ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                          : 'bg-muted text-muted-foreground'
                      )}
                    >
                      {workflowRule.active ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    {formatDate(new Date(workflowRule.createdAt), {
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric',
                    })}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-right text-sm">
                    <div className="flex justify-end gap-2">
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => {
                          setLogsItemId(workflowRule.id)
                          setLogsItemName(workflowRule.name)
                        }}
                        aria-label={`View logs for ${workflowRule.name}`}
                        data-testid={`logs-button-${index}`}
                      >
                        Logs
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => handleEdit(workflowRule)}
                        aria-label={`Edit ${workflowRule.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        Edit
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        className="border-destructive/30 text-destructive hover:bg-destructive/10"
                        onClick={() => handleDeleteClick(workflowRule)}
                        aria-label={`Delete ${workflowRule.name}`}
                        data-testid={`delete-button-${index}`}
                      >
                        Delete
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {logsItemId && (
        <ExecutionLogModal<WorkflowExecutionLog>
          title="Workflow Execution Logs"
          subtitle={logsItemName}
          columns={logColumns}
          data={logs ?? []}
          isLoading={logsLoading}
          error={logsError instanceof Error ? logsError : null}
          onClose={() => setLogsItemId(null)}
          emptyMessage="No execution logs found."
        />
      )}

      {isFormOpen && (
        <WorkflowRuleForm
          workflowRule={editingWorkflowRule}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={isSubmitting}
        />
      )}

      <ConfirmDialog
        open={deleteDialogOpen}
        title="Delete Workflow Rule"
        message="Are you sure you want to delete this workflow rule? This action cannot be undone."
        confirmLabel="Delete"
        cancelLabel="Cancel"
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  )
}

export default WorkflowRulesPage
