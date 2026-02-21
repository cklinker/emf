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

interface Script {
  id: string
  name: string
  description: string | null
  scriptType: string
  language: string
  sourceCode: string
  active: boolean
  version: number
  createdBy: string
  createdAt: string
  updatedAt: string
}

interface ScriptFormData {
  name: string
  description: string
  scriptType: string
  language: string
  sourceCode: string
  active: boolean
}

interface FormErrors {
  name?: string
  description?: string
  sourceCode?: string
}

interface ScriptExecutionLog {
  id: string
  scriptId: string
  status: string
  triggerType: string | null
  recordId: string | null
  durationMs: number | null
  cpuMs: number | null
  queriesExecuted: number | null
  dmlRows: number | null
  callouts: number | null
  errorMessage: string | null
  logOutput: string | null
  executedAt: string
}

export interface ScriptsPageProps {
  testId?: string
}

function validateForm(data: ScriptFormData): FormErrors {
  const errors: FormErrors = {}
  if (!data.name.trim()) {
    errors.name = 'Name is required'
  } else if (data.name.length > 100) {
    errors.name = 'Name must be 100 characters or fewer'
  }
  if (data.description && data.description.length > 500) {
    errors.description = 'Description must be 500 characters or fewer'
  }
  if (!data.sourceCode.trim()) {
    errors.sourceCode = 'Source code is required'
  }
  return errors
}

interface ScriptFormProps {
  script?: Script
  onSubmit: (data: ScriptFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function ScriptForm({
  script,
  onSubmit,
  onCancel,
  isSubmitting,
}: ScriptFormProps): React.ReactElement {
  const isEditing = !!script
  const [formData, setFormData] = useState<ScriptFormData>({
    name: script?.name ?? '',
    description: script?.description ?? '',
    scriptType: script?.scriptType ?? 'BEFORE_TRIGGER',
    language: script?.language ?? 'javascript',
    sourceCode: script?.sourceCode ?? '',
    active: script?.active ?? true,
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const nameInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    (field: keyof ScriptFormData, value: string | boolean) => {
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
      setTouched({ name: true, description: true, sourceCode: true })
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

  const title = isEditing ? 'Edit Script' : 'Create Script'

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="script-form-overlay"
      role="presentation"
    >
      <div
        className="w-full max-w-[600px] max-h-[90vh] overflow-y-auto rounded-lg bg-background shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="script-form-title"
        data-testid="script-form-modal"
      >
        <div className="flex items-center justify-between border-b border-border p-6">
          <h2 id="script-form-title" className="text-lg font-semibold text-foreground">
            {title}
          </h2>
          <button
            type="button"
            className="rounded p-2 text-2xl leading-none text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            onClick={onCancel}
            aria-label="Close"
            data-testid="script-form-close"
          >
            &times;
          </button>
        </div>
        <div className="p-6">
          <form className="flex flex-col gap-5" onSubmit={handleSubmit} noValidate>
            <div className="flex flex-col gap-2">
              <label htmlFor="script-name" className="text-sm font-medium text-foreground">
                Name
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="script-name"
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
                placeholder="Enter script name"
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                disabled={isSubmitting}
                data-testid="script-name-input"
              />
              {touched.name && errors.name && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label htmlFor="script-description" className="text-sm font-medium text-foreground">
                Description
              </label>
              <textarea
                id="script-description"
                className={cn(
                  'min-h-[80px] resize-y rounded-md border border-border bg-background px-3 py-2.5 font-[inherit] text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.description && errors.description && 'border-destructive'
                )}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                onBlur={() => handleBlur('description')}
                placeholder="Enter script description"
                disabled={isSubmitting}
                rows={3}
                data-testid="script-description-input"
              />
              {touched.description && errors.description && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.description}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label htmlFor="script-type" className="text-sm font-medium text-foreground">
                Script Type
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <select
                id="script-type"
                className="rounded-md border border-border bg-background px-3 py-2.5 text-sm text-foreground transition-colors focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground"
                value={formData.scriptType}
                onChange={(e) => handleChange('scriptType', e.target.value)}
                disabled={isSubmitting}
                data-testid="script-type-input"
              >
                <option value="BEFORE_TRIGGER">Before Trigger</option>
                <option value="AFTER_TRIGGER">After Trigger</option>
                <option value="SCHEDULED">Scheduled</option>
                <option value="API_ENDPOINT">API Endpoint</option>
                <option value="VALIDATION">Validation</option>
                <option value="EVENT_HANDLER">Event Handler</option>
                <option value="EMAIL_HANDLER">Email Handler</option>
              </select>
            </div>

            <div className="flex flex-col gap-2">
              <label htmlFor="script-language" className="text-sm font-medium text-foreground">
                Language
              </label>
              <input
                id="script-language"
                type="text"
                className="rounded-md border border-border bg-background px-3 py-2.5 text-sm text-foreground transition-colors focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground"
                value={formData.language}
                onChange={(e) => handleChange('language', e.target.value)}
                placeholder="Enter language"
                disabled={isSubmitting}
                data-testid="script-language-input"
              />
            </div>

            <div className="flex flex-col gap-2">
              <label htmlFor="script-source-code" className="text-sm font-medium text-foreground">
                Source Code
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <textarea
                id="script-source-code"
                className={cn(
                  'min-h-[80px] resize-y rounded-md border border-border bg-background px-3 py-2.5 font-[inherit] text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.sourceCode && errors.sourceCode && 'border-destructive'
                )}
                value={formData.sourceCode}
                onChange={(e) => handleChange('sourceCode', e.target.value)}
                onBlur={() => handleBlur('sourceCode')}
                placeholder="Enter source code"
                disabled={isSubmitting}
                rows={10}
                data-testid="script-source-code-input"
              />
              {touched.sourceCode && errors.sourceCode && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.sourceCode}
                </span>
              )}
            </div>

            <div className="flex items-center gap-2">
              <input
                id="script-active"
                type="checkbox"
                className="h-4 w-4 accent-primary"
                checked={formData.active}
                onChange={(e) => handleChange('active', e.target.checked)}
                disabled={isSubmitting}
                data-testid="script-active-input"
              />
              <label htmlFor="script-active" className="text-sm font-medium text-foreground">
                Active
              </label>
            </div>

            <div className="mt-2 flex justify-end gap-3 border-t border-border pt-4">
              <Button
                type="button"
                variant="outline"
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="script-form-cancel"
              >
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting} data-testid="script-form-submit">
                {isSubmitting ? 'Saving...' : 'Save'}
              </Button>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}

export function ScriptsPage({ testId = 'scripts-page' }: ScriptsPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingScript, setEditingScript] = useState<Script | undefined>(undefined)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [scriptToDelete, setScriptToDelete] = useState<Script | null>(null)
  const [logsItemId, setLogsItemId] = useState<string | null>(null)
  const [logsItemName, setLogsItemName] = useState('')

  const {
    data: logs,
    isLoading: logsLoading,
    error: logsError,
  } = useQuery({
    queryKey: ['script-logs', logsItemId],
    queryFn: () => apiClient.get<ScriptExecutionLog[]>(`/control/scripts/${logsItemId}/logs`),
    enabled: !!logsItemId,
  })

  const logColumns: LogColumn<ScriptExecutionLog>[] = [
    { key: 'status', header: 'Status' },
    { key: 'triggerType', header: 'Trigger' },
    {
      key: 'durationMs',
      header: 'Duration',
      render: (v) => (v != null ? `${v}ms` : '-'),
    },
    {
      key: 'cpuMs',
      header: 'CPU (ms)',
      render: (v) => (v != null ? String(v) : '-'),
    },
    {
      key: 'queriesExecuted',
      header: 'Queries',
      render: (v) => (v != null ? String(v) : '-'),
    },
    {
      key: 'dmlRows',
      header: 'DML Rows',
      render: (v) => (v != null ? String(v) : '-'),
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
    data: scripts,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['scripts'],
    queryFn: () => apiClient.get<Script[]>(`/control/scripts`),
  })

  const scriptList: Script[] = scripts ?? []

  const createMutation = useMutation({
    mutationFn: (data: ScriptFormData) =>
      apiClient.post<Script>(`/control/scripts?userId=system`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scripts'] })
      showToast('Script created successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: ScriptFormData }) =>
      apiClient.put<Script>(`/control/scripts/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scripts'] })
      showToast('Script updated successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/control/scripts/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scripts'] })
      showToast('Script deleted successfully', 'success')
      setDeleteDialogOpen(false)
      setScriptToDelete(null)
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const handleCreate = useCallback(() => {
    setEditingScript(undefined)
    setIsFormOpen(true)
  }, [])

  const handleEdit = useCallback((script: Script) => {
    setEditingScript(script)
    setIsFormOpen(true)
  }, [])

  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingScript(undefined)
  }, [])

  const handleFormSubmit = useCallback(
    (data: ScriptFormData) => {
      if (editingScript) {
        updateMutation.mutate({ id: editingScript.id, data })
      } else {
        createMutation.mutate(data)
      }
    },
    [editingScript, createMutation, updateMutation]
  )

  const handleDeleteClick = useCallback((script: Script) => {
    setScriptToDelete(script)
    setDeleteDialogOpen(true)
  }, [])

  const handleDeleteConfirm = useCallback(() => {
    if (scriptToDelete) {
      deleteMutation.mutate(scriptToDelete.id)
    }
  }, [scriptToDelete, deleteMutation])

  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setScriptToDelete(null)
  }, [])

  if (isLoading) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <div className="flex min-h-[400px] items-center justify-center">
          <LoadingSpinner size="large" label="Loading scripts..." />
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
        <h1 className="text-2xl font-semibold text-foreground">Scripts</h1>
        <Button
          type="button"
          onClick={handleCreate}
          aria-label="Create Script"
          data-testid="add-script-button"
        >
          Create Script
        </Button>
      </header>

      {scriptList.length === 0 ? (
        <div
          className="rounded-lg border border-border bg-card py-16 text-center text-muted-foreground"
          data-testid="empty-state"
        >
          <p>No scripts found.</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border bg-card">
          <table
            className="w-full border-collapse"
            role="grid"
            aria-label="Scripts"
            data-testid="scripts-table"
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
                  Script Type
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Language
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Version
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
              {scriptList.map((script, index) => (
                <tr
                  key={script.id}
                  role="row"
                  className="border-b border-border transition-colors last:border-b-0 hover:bg-muted/50"
                  data-testid={`script-row-${index}`}
                >
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    {script.name}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    <span className="inline-block rounded-full bg-muted px-3 py-1 text-xs font-semibold uppercase tracking-wider text-primary">
                      {script.scriptType}
                    </span>
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    {script.language}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    {script.version}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    <span
                      className={cn(
                        'inline-block rounded-full px-3 py-1 text-xs font-semibold',
                        script.active
                          ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                          : 'bg-muted text-muted-foreground'
                      )}
                    >
                      {script.active ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    {formatDate(new Date(script.createdAt), {
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
                          setLogsItemId(script.id)
                          setLogsItemName(script.name)
                        }}
                        aria-label={`View logs for ${script.name}`}
                        data-testid={`logs-button-${index}`}
                      >
                        Logs
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => handleEdit(script)}
                        aria-label={`Edit ${script.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        Edit
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        className="border-destructive/30 text-destructive hover:bg-destructive/10"
                        onClick={() => handleDeleteClick(script)}
                        aria-label={`Delete ${script.name}`}
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
        <ExecutionLogModal<ScriptExecutionLog>
          title="Script Execution Logs"
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
        <ScriptForm
          script={editingScript}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={isSubmitting}
        />
      )}

      <ConfirmDialog
        open={deleteDialogOpen}
        title="Delete Script"
        message="Are you sure you want to delete this script? This action cannot be undone."
        confirmLabel="Delete"
        cancelLabel="Cancel"
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  )
}

export default ScriptsPage
