import React, { useState, useCallback, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { getTenantSlug } from '../../context/TenantContext'
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

interface Flow {
  id: string
  name: string
  description: string | null
  flowType: string
  active: boolean
  definition: string | null
  createdBy: string
  createdAt: string
  updatedAt: string
}

interface FlowExecutionLog {
  id: string
  flowId: string
  flowName: string
  status: string
  startedBy: string | null
  triggerRecordId: string | null
  currentNodeId: string | null
  errorMessage: string | null
  startedAt: string
  completedAt: string | null
}

interface FlowFormData {
  name: string
  description: string
  flowType: string
  active: boolean
  definition: string
}

interface FormErrors {
  name?: string
  description?: string
  definition?: string
}

export interface FlowsPageProps {
  testId?: string
}

function validateForm(data: FlowFormData): FormErrors {
  const errors: FormErrors = {}
  if (!data.name.trim()) {
    errors.name = 'Name is required'
  } else if (data.name.length > 100) {
    errors.name = 'Name must be 100 characters or fewer'
  }
  if (data.description && data.description.length > 500) {
    errors.description = 'Description must be 500 characters or fewer'
  }
  if (data.definition) {
    try {
      JSON.parse(data.definition)
    } catch {
      errors.definition = 'Definition must be valid JSON'
    }
  }
  return errors
}

interface FlowFormProps {
  flow?: Flow
  onSubmit: (data: FlowFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function FlowForm({ flow, onSubmit, onCancel, isSubmitting }: FlowFormProps): React.ReactElement {
  const isEditing = !!flow
  const [formData, setFormData] = useState<FlowFormData>({
    name: flow?.name ?? '',
    description: flow?.description ?? '',
    flowType: flow?.flowType ?? 'AUTOLAUNCHED',
    active: flow?.active ?? false,
    definition: flow?.definition ?? '',
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const nameInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    (field: keyof FlowFormData, value: string | boolean) => {
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
      setTouched({ name: true, description: true, definition: true })
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

  const title = isEditing ? 'Edit Flow' : 'Create Flow'

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="flow-form-overlay"
      role="presentation"
    >
      <div
        className="w-full max-w-[600px] max-h-[90vh] overflow-y-auto rounded-lg bg-background shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="flow-form-title"
        data-testid="flow-form-modal"
      >
        <div className="flex items-center justify-between border-b border-border p-6">
          <h2 id="flow-form-title" className="text-lg font-semibold text-foreground">
            {title}
          </h2>
          <button
            type="button"
            className="rounded p-2 text-2xl leading-none text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            onClick={onCancel}
            aria-label="Close"
            data-testid="flow-form-close"
          >
            &times;
          </button>
        </div>
        <div className="p-6">
          <form className="flex flex-col gap-5" onSubmit={handleSubmit} noValidate>
            <div className="flex flex-col gap-2">
              <label htmlFor="flow-name" className="text-sm font-medium text-foreground">
                Name
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="flow-name"
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
                placeholder="Enter flow name"
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                disabled={isSubmitting}
                data-testid="flow-name-input"
              />
              {touched.name && errors.name && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label htmlFor="flow-description" className="text-sm font-medium text-foreground">
                Description
              </label>
              <textarea
                id="flow-description"
                className={cn(
                  'min-h-[80px] resize-y rounded-md border border-border bg-background px-3 py-2.5 font-[inherit] text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.description && errors.description && 'border-destructive'
                )}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                onBlur={() => handleBlur('description')}
                placeholder="Enter flow description"
                disabled={isSubmitting}
                rows={3}
                data-testid="flow-description-input"
              />
              {touched.description && errors.description && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.description}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label htmlFor="flow-type" className="text-sm font-medium text-foreground">
                Flow Type
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <select
                id="flow-type"
                className="rounded-md border border-border bg-background px-3 py-2.5 text-sm text-foreground transition-colors focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground"
                value={formData.flowType}
                onChange={(e) => handleChange('flowType', e.target.value)}
                disabled={isSubmitting}
                data-testid="flow-type-input"
              >
                <option value="RECORD_TRIGGERED">Record Triggered</option>
                <option value="SCHEDULED">Scheduled</option>
                <option value="AUTOLAUNCHED">Autolaunched</option>
                <option value="SCREEN">Screen</option>
              </select>
            </div>

            <div className="flex flex-col gap-2">
              <label htmlFor="flow-definition" className="text-sm font-medium text-foreground">
                Definition
              </label>
              <textarea
                id="flow-definition"
                className={cn(
                  'min-h-[80px] resize-y rounded-md border border-border bg-background px-3 py-2.5 font-[inherit] text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.definition && errors.definition && 'border-destructive'
                )}
                value={formData.definition}
                onChange={(e) => handleChange('definition', e.target.value)}
                onBlur={() => handleBlur('definition')}
                placeholder="Enter flow definition (JSON)"
                disabled={isSubmitting}
                rows={6}
                data-testid="flow-definition-input"
              />
              {touched.definition && errors.definition && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.definition}
                </span>
              )}
            </div>

            <div className="flex items-center gap-2">
              <input
                id="flow-active"
                type="checkbox"
                className="h-4 w-4 accent-primary"
                checked={formData.active}
                onChange={(e) => handleChange('active', e.target.checked)}
                disabled={isSubmitting}
                data-testid="flow-active-input"
              />
              <label htmlFor="flow-active" className="text-sm font-medium text-foreground">
                Active
              </label>
            </div>

            <div className="mt-2 flex justify-end gap-3 border-t border-border pt-4">
              <Button
                type="button"
                variant="outline"
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="flow-form-cancel"
              >
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting} data-testid="flow-form-submit">
                {isSubmitting ? 'Saving...' : 'Save'}
              </Button>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}

export function FlowsPage({ testId = 'flows-page' }: FlowsPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { t, formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingFlow, setEditingFlow] = useState<Flow | undefined>(undefined)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [flowToDelete, setFlowToDelete] = useState<Flow | null>(null)
  const [execItemId, setExecItemId] = useState<string | null>(null)
  const [execItemName, setExecItemName] = useState('')

  const {
    data: flows,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['flows'],
    queryFn: () => apiClient.getList<Flow>(`/api/flows`),
  })

  const flowList: Flow[] = flows ?? []

  const createMutation = useMutation({
    mutationFn: (data: FlowFormData) => apiClient.postResource<Flow>(`/api/flows`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['flows'] })
      showToast('Flow created successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: FlowFormData }) =>
      apiClient.putResource<Flow>(`/api/flows/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['flows'] })
      showToast('Flow updated successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/flows/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['flows'] })
      showToast('Flow deleted successfully', 'success')
      setDeleteDialogOpen(false)
      setFlowToDelete(null)
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const {
    data: executions,
    isLoading: execLoading,
    error: execError,
  } = useQuery({
    queryKey: ['flow-executions', execItemId],
    queryFn: () => apiClient.getList<FlowExecutionLog>(`/api/flows/${execItemId}/flow-executions`),
    enabled: !!execItemId,
  })

  const executeMutation = useMutation({
    mutationFn: async (flowId: string) => {
      const resp = await apiClient.fetch(
        `/api/flows/${flowId}/execute?${new URLSearchParams({ userId: 'system' }).toString()}`,
        { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' }
      )
      if (!resp.ok) {
        const errBody = await resp.json().catch(() => ({}))
        throw new Error(
          (errBody as Record<string, string>).message || `Execute failed: ${resp.statusText}`
        )
      }
    },
    onSuccess: () => {
      showToast(t('flows.executionStarted'), 'success')
      if (execItemId) {
        queryClient.invalidateQueries({ queryKey: ['flow-executions', execItemId] })
      }
    },
    onError: (err: Error) => showToast(err.message, 'error'),
  })

  const cancelMutation = useMutation({
    mutationFn: async (executionId: string) => {
      const resp = await apiClient.fetch(`/api/flows/executions/${executionId}/cancel`, {
        method: 'POST',
      })
      if (!resp.ok) throw new Error(`Cancel failed: ${resp.statusText}`)
    },
    onSuccess: () => {
      showToast(t('flows.executionCancelled'), 'success')
      queryClient.invalidateQueries({ queryKey: ['flow-executions', execItemId] })
    },
    onError: (err: Error) => showToast(err.message, 'error'),
  })

  const execColumns: LogColumn<FlowExecutionLog>[] = [
    {
      key: 'status',
      header: t('logs.status'),
      render: (v) => {
        const status = v as string
        const colorMap: Record<string, { color: string; bg: string }> = {
          SUCCESS: { color: '#065f46', bg: '#d1fae5' },
          FAILED: { color: '#991b1b', bg: '#fee2e2' },
          RUNNING: { color: '#1e40af', bg: '#dbeafe' },
          CANCELLED: { color: '#6b7280', bg: '#f3f4f6' },
        }
        const style = colorMap[status] ?? { color: '#6b7280', bg: '#f3f4f6' }
        return (
          <span
            style={{
              display: 'inline-block',
              padding: '0.125rem 0.5rem',
              borderRadius: '9999px',
              fontSize: '0.75rem',
              fontWeight: 600,
              color: style.color,
              backgroundColor: style.bg,
            }}
          >
            {status}
          </span>
        )
      },
    },
    { key: 'startedBy', header: t('flows.startedBy') },
    { key: 'triggerRecordId', header: t('flows.triggerRecord') },
    { key: 'currentNodeId', header: t('flows.currentNode') },
    { key: 'errorMessage', header: t('logs.errorMessage') },
    {
      key: 'startedAt',
      header: t('logs.startedAt'),
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
    {
      key: 'completedAt',
      header: t('logs.completedAt'),
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
    {
      key: 'actions',
      header: '',
      render: (_v: unknown, row: FlowExecutionLog) =>
        row.status === 'RUNNING' ? (
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="h-auto border-destructive/30 px-2 py-1 text-xs text-destructive hover:bg-destructive/10"
            onClick={() => cancelMutation.mutate(row.id)}
            disabled={cancelMutation.isPending}
          >
            {t('flows.cancelExecution')}
          </Button>
        ) : null,
    },
  ]

  const handleCreate = useCallback(() => {
    setEditingFlow(undefined)
    setIsFormOpen(true)
  }, [])

  const handleEdit = useCallback((flow: Flow) => {
    setEditingFlow(flow)
    setIsFormOpen(true)
  }, [])

  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingFlow(undefined)
  }, [])

  const handleFormSubmit = useCallback(
    (data: FlowFormData) => {
      if (editingFlow) {
        updateMutation.mutate({ id: editingFlow.id, data })
      } else {
        createMutation.mutate(data)
      }
    },
    [editingFlow, createMutation, updateMutation]
  )

  const handleDeleteClick = useCallback((flow: Flow) => {
    setFlowToDelete(flow)
    setDeleteDialogOpen(true)
  }, [])

  const handleDeleteConfirm = useCallback(() => {
    if (flowToDelete) {
      deleteMutation.mutate(flowToDelete.id)
    }
  }, [flowToDelete, deleteMutation])

  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setFlowToDelete(null)
  }, [])

  if (isLoading) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <div className="flex min-h-[400px] items-center justify-center">
          <LoadingSpinner size="large" label="Loading flows..." />
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
        <h1 className="text-2xl font-semibold text-foreground">Flows</h1>
        <Button
          type="button"
          onClick={handleCreate}
          aria-label="Create Flow"
          data-testid="add-flow-button"
        >
          Create Flow
        </Button>
      </header>

      {flowList.length === 0 ? (
        <div
          className="rounded-lg border border-border bg-card py-16 text-center text-muted-foreground"
          data-testid="empty-state"
        >
          <p>No flows found.</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border bg-card">
          <table
            className="w-full border-collapse"
            role="grid"
            aria-label="Flows"
            data-testid="flows-table"
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
                  Flow Type
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
              {flowList.map((flow, index) => (
                <tr
                  key={flow.id}
                  role="row"
                  className="border-b border-border transition-colors last:border-b-0 hover:bg-muted/50"
                  data-testid={`flow-row-${index}`}
                >
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    {flow.name}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    <span className="inline-block rounded-full bg-muted px-3 py-1 text-xs font-semibold uppercase tracking-wider text-primary">
                      {flow.flowType}
                    </span>
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    <span
                      className={cn(
                        'inline-block rounded-full px-3 py-1 text-xs font-semibold',
                        flow.active
                          ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                          : 'bg-muted text-muted-foreground'
                      )}
                    >
                      {flow.active ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    {formatDate(new Date(flow.createdAt), {
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
                        onClick={() => navigate(`/${getTenantSlug()}/flows/${flow.id}/design`)}
                        aria-label={`Design ${flow.name}`}
                        data-testid={`design-button-${index}`}
                      >
                        Design
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => {
                          setExecItemId(flow.id)
                          setExecItemName(flow.name)
                        }}
                        aria-label={`View executions for ${flow.name}`}
                        data-testid={`executions-button-${index}`}
                      >
                        {t('flows.executions')}
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => executeMutation.mutate(flow.id)}
                        disabled={!flow.active || executeMutation.isPending}
                        aria-label={`Run ${flow.name}`}
                        data-testid={`run-button-${index}`}
                      >
                        {t('flows.runFlow')}
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => handleEdit(flow)}
                        aria-label={`Edit ${flow.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        Edit
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        className="border-destructive/30 text-destructive hover:bg-destructive/10"
                        onClick={() => handleDeleteClick(flow)}
                        aria-label={`Delete ${flow.name}`}
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

      {isFormOpen && (
        <FlowForm
          flow={editingFlow}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={isSubmitting}
        />
      )}

      <ConfirmDialog
        open={deleteDialogOpen}
        title="Delete Flow"
        message="Are you sure you want to delete this flow? This action cannot be undone."
        confirmLabel="Delete"
        cancelLabel="Cancel"
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />

      {execItemId && (
        <ExecutionLogModal<FlowExecutionLog>
          title={t('flows.flowExecutionHistory')}
          subtitle={execItemName}
          columns={execColumns}
          data={executions ?? []}
          isLoading={execLoading}
          error={execError instanceof Error ? execError : null}
          onClose={() => setExecItemId(null)}
          emptyMessage={t('flows.noExecutions')}
        />
      )}
    </div>
  )
}

export default FlowsPage
