import React, { useState, useCallback, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import { cn } from '@/lib/utils'

interface ConnectedApp {
  id: string
  name: string
  description: string | null
  clientId: string
  active: boolean
  rateLimitPerHour: number
  lastUsedAt: string | null
  createdBy: string
  createdAt: string
  updatedAt: string
}

interface ConnectedAppCreatedResponse {
  id: string
  name: string
  clientId: string
  clientSecret: string
  active: boolean
  rateLimitPerHour: number
  createdBy: string
  createdAt: string
  updatedAt: string
}

interface ConnectedAppFormData {
  name: string
  description: string
  scopes: string
  rateLimitPerHour: number
  active: boolean
}

interface FormErrors {
  name?: string
  description?: string
  scopes?: string
  rateLimitPerHour?: string
}

export interface ConnectedAppsPageProps {
  testId?: string
}

function validateForm(data: ConnectedAppFormData): FormErrors {
  const errors: FormErrors = {}
  if (!data.name.trim()) {
    errors.name = 'Name is required'
  } else if (data.name.length > 100) {
    errors.name = 'Name must be 100 characters or fewer'
  }
  if (data.description && data.description.length > 500) {
    errors.description = 'Description must be 500 characters or fewer'
  }
  if (data.scopes.trim()) {
    try {
      const parsed = JSON.parse(data.scopes)
      if (!Array.isArray(parsed)) {
        errors.scopes = 'Scopes must be a JSON array (e.g. ["api"])'
      }
    } catch {
      errors.scopes = 'Scopes must be valid JSON (e.g. ["api"])'
    }
  }
  if (data.rateLimitPerHour < 1 || data.rateLimitPerHour > 1000000) {
    errors.rateLimitPerHour = 'Rate limit must be between 1 and 1,000,000'
  }
  return errors
}

interface ConnectedAppFormProps {
  connectedApp?: ConnectedApp
  onSubmit: (data: ConnectedAppFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function ConnectedAppForm({
  connectedApp,
  onSubmit,
  onCancel,
  isSubmitting,
}: ConnectedAppFormProps): React.ReactElement {
  const isEditing = !!connectedApp
  const [formData, setFormData] = useState<ConnectedAppFormData>({
    name: connectedApp?.name ?? '',
    description: connectedApp?.description ?? '',
    scopes: '',
    rateLimitPerHour: connectedApp?.rateLimitPerHour ?? 10000,
    active: connectedApp?.active ?? true,
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const nameInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    (field: keyof ConnectedAppFormData, value: string | boolean | number) => {
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
      setTouched({ name: true, description: true, scopes: true, rateLimitPerHour: true })
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

  const title = isEditing ? 'Edit Connected App' : 'Create Connected App'

  return (
    <div
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/50 p-4"
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="connected-app-form-overlay"
      role="presentation"
    >
      <div
        className="w-full max-w-[700px] max-h-[90vh] overflow-y-auto rounded-lg bg-card shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="connected-app-form-title"
        data-testid="connected-app-form-modal"
      >
        <div className="flex items-center justify-between border-b border-border p-6">
          <h2 id="connected-app-form-title" className="m-0 text-xl font-semibold text-foreground">
            {title}
          </h2>
          <button
            type="button"
            className="rounded p-2 text-2xl leading-none text-muted-foreground hover:bg-muted hover:text-foreground"
            onClick={onCancel}
            aria-label="Close"
            data-testid="connected-app-form-close"
          >
            &times;
          </button>
        </div>
        <div className="p-6">
          <form className="space-y-4" onSubmit={handleSubmit} noValidate>
            <div>
              <label
                htmlFor="connected-app-name"
                className="mb-1 block text-sm font-medium text-foreground"
              >
                Name
                <span className="ml-0.5 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="connected-app-name"
                type="text"
                className={cn(
                  'w-full rounded-md border px-3 py-2 text-sm text-foreground bg-background focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary',
                  touched.name && errors.name ? 'border-destructive' : 'border-border'
                )}
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                onBlur={() => handleBlur('name')}
                placeholder="Enter connected app name"
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                disabled={isSubmitting}
                data-testid="connected-app-name-input"
              />
              {touched.name && errors.name && (
                <span className="mt-1 block text-xs text-destructive" role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div>
              <label
                htmlFor="connected-app-description"
                className="mb-1 block text-sm font-medium text-foreground"
              >
                Description
              </label>
              <textarea
                id="connected-app-description"
                className={cn(
                  'w-full rounded-md border px-3 py-2 text-sm text-foreground bg-background focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary',
                  touched.description && errors.description ? 'border-destructive' : 'border-border'
                )}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                onBlur={() => handleBlur('description')}
                placeholder="Enter connected app description"
                disabled={isSubmitting}
                rows={3}
                data-testid="connected-app-description-input"
              />
              {touched.description && errors.description && (
                <span className="mt-1 block text-xs text-destructive" role="alert">
                  {errors.description}
                </span>
              )}
            </div>

            {!isEditing && (
              <div>
                <label
                  htmlFor="connected-app-scopes"
                  className="mb-1 block text-sm font-medium text-foreground"
                >
                  Scopes
                </label>
                <input
                  id="connected-app-scopes"
                  type="text"
                  className={cn(
                    'w-full rounded-md border px-3 py-2 text-sm text-foreground bg-background focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary',
                    touched.scopes && errors.scopes ? 'border-destructive' : 'border-border'
                  )}
                  value={formData.scopes}
                  onChange={(e) => handleChange('scopes', e.target.value)}
                  onBlur={() => handleBlur('scopes')}
                  placeholder='["api"]'
                  disabled={isSubmitting}
                  data-testid="connected-app-scopes-input"
                />
                <span className="mt-1 block text-xs text-muted-foreground">
                  JSON array of scope strings
                </span>
                {touched.scopes && errors.scopes && (
                  <span className="mt-1 block text-xs text-destructive" role="alert">
                    {errors.scopes}
                  </span>
                )}
              </div>
            )}

            <div>
              <label
                htmlFor="connected-app-rate-limit"
                className="mb-1 block text-sm font-medium text-foreground"
              >
                Rate Limit (per hour)
              </label>
              <input
                id="connected-app-rate-limit"
                type="number"
                className={cn(
                  'w-full rounded-md border px-3 py-2 text-sm text-foreground bg-background focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary',
                  touched.rateLimitPerHour && errors.rateLimitPerHour
                    ? 'border-destructive'
                    : 'border-border'
                )}
                value={formData.rateLimitPerHour}
                onChange={(e) =>
                  handleChange('rateLimitPerHour', parseInt(e.target.value, 10) || 1)
                }
                onBlur={() => handleBlur('rateLimitPerHour')}
                min={1}
                max={1000000}
                disabled={isSubmitting}
                data-testid="connected-app-rate-limit-input"
              />
              {touched.rateLimitPerHour && errors.rateLimitPerHour && (
                <span className="mt-1 block text-xs text-destructive" role="alert">
                  {errors.rateLimitPerHour}
                </span>
              )}
            </div>

            <div className="flex items-center gap-2">
              <input
                id="connected-app-active"
                type="checkbox"
                checked={formData.active}
                onChange={(e) => handleChange('active', e.target.checked)}
                disabled={isSubmitting}
                className="h-4 w-4 accent-primary"
                data-testid="connected-app-active-input"
              />
              <label htmlFor="connected-app-active" className="text-sm font-medium text-foreground">
                Active
              </label>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                className="rounded-md border border-border bg-secondary px-4 py-2 text-sm text-foreground hover:bg-muted disabled:opacity-50"
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="connected-app-form-cancel"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
                disabled={isSubmitting}
                data-testid="connected-app-form-submit"
              >
                {isSubmitting ? 'Saving...' : 'Save'}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}

interface CredentialsDialogProps {
  clientId: string
  clientSecret: string
  onClose: () => void
}

function CredentialsDialog({
  clientId,
  clientSecret,
  onClose,
}: CredentialsDialogProps): React.ReactElement {
  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose()
      }
    },
    [onClose]
  )

  return (
    <div
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/50 p-4"
      onClick={(e) => e.target === e.currentTarget && onClose()}
      onKeyDown={handleKeyDown}
      data-testid="credentials-dialog-overlay"
      role="presentation"
    >
      <div
        className="w-full max-w-[700px] max-h-[90vh] overflow-y-auto rounded-lg bg-card shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="credentials-dialog-title"
        data-testid="credentials-dialog-modal"
      >
        <div className="flex items-center justify-between border-b border-border p-6">
          <h2 id="credentials-dialog-title" className="m-0 text-xl font-semibold text-foreground">
            Connected App Created
          </h2>
          <button
            type="button"
            className="rounded p-2 text-2xl leading-none text-muted-foreground hover:bg-muted hover:text-foreground"
            onClick={onClose}
            aria-label="Close"
            data-testid="credentials-dialog-close"
          >
            &times;
          </button>
        </div>
        <div className="p-6">
          <div className="rounded-lg border border-amber-300 bg-amber-50 p-4 dark:border-amber-700 dark:bg-amber-950">
            <p className="mb-3 text-sm font-medium text-amber-800 dark:text-amber-300">
              Save these credentials now. The client secret will not be shown again.
            </p>
            <div className="mb-2 flex flex-col gap-1">
              <span className="text-xs font-medium text-muted-foreground">Client ID</span>
              <code
                className="rounded bg-muted px-2 py-1 font-mono text-sm text-foreground"
                data-testid="credentials-client-id"
              >
                {clientId}
              </code>
            </div>
            <div className="flex flex-col gap-1">
              <span className="text-xs font-medium text-muted-foreground">Client Secret</span>
              <code
                className="rounded bg-muted px-2 py-1 font-mono text-sm text-foreground"
                data-testid="credentials-client-secret"
              >
                {clientSecret}
              </code>
            </div>
          </div>
          <div className="mt-4 flex justify-end">
            <button
              type="button"
              className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
              onClick={onClose}
              data-testid="credentials-dialog-done"
            >
              Done
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

export function ConnectedAppsPage({
  testId = 'connected-apps-page',
}: ConnectedAppsPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingApp, setEditingApp] = useState<ConnectedApp | undefined>(undefined)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [appToDelete, setAppToDelete] = useState<ConnectedApp | null>(null)
  const [createdCredentials, setCreatedCredentials] = useState<{
    clientId: string
    clientSecret: string
  } | null>(null)

  const {
    data: connectedApps,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['connected-apps'],
    queryFn: () => apiClient.get<ConnectedApp[]>(`/control/connected-apps`),
  })

  const appList: ConnectedApp[] = connectedApps ?? []

  const createMutation = useMutation({
    mutationFn: (data: ConnectedAppFormData) =>
      apiClient.post<ConnectedAppCreatedResponse>(`/control/connected-apps?userId=system`, data),
    onSuccess: (result: ConnectedAppCreatedResponse) => {
      queryClient.invalidateQueries({ queryKey: ['connected-apps'] })
      showToast('Connected app created successfully', 'success')
      handleCloseForm()
      setCreatedCredentials({ clientId: result.clientId, clientSecret: result.clientSecret })
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: ConnectedAppFormData }) =>
      apiClient.put<ConnectedApp>(`/control/connected-apps/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['connected-apps'] })
      showToast('Connected app updated successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/control/connected-apps/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['connected-apps'] })
      showToast('Connected app deleted successfully', 'success')
      setDeleteDialogOpen(false)
      setAppToDelete(null)
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const handleCreate = useCallback(() => {
    setEditingApp(undefined)
    setIsFormOpen(true)
  }, [])
  const handleEdit = useCallback((app: ConnectedApp) => {
    setEditingApp(app)
    setIsFormOpen(true)
  }, [])
  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingApp(undefined)
  }, [])

  const handleFormSubmit = useCallback(
    (data: ConnectedAppFormData) => {
      if (editingApp) {
        updateMutation.mutate({ id: editingApp.id, data })
      } else {
        createMutation.mutate(data)
      }
    },
    [editingApp, createMutation, updateMutation]
  )

  const handleDeleteClick = useCallback((app: ConnectedApp) => {
    setAppToDelete(app)
    setDeleteDialogOpen(true)
  }, [])
  const handleDeleteConfirm = useCallback(() => {
    if (appToDelete) {
      deleteMutation.mutate(appToDelete.id)
    }
  }, [appToDelete, deleteMutation])
  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setAppToDelete(null)
  }, [])
  const handleCredentialsClose = useCallback(() => {
    setCreatedCredentials(null)
  }, [])

  if (isLoading) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <div className="flex min-h-[400px] items-center justify-center">
          <LoadingSpinner size="large" label="Loading connected apps..." />
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
        <h1 className="m-0 text-2xl font-semibold text-foreground">Connected Apps</h1>
        <button
          type="button"
          className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
          onClick={handleCreate}
          aria-label="Create Connected App"
          data-testid="add-connected-app-button"
        >
          Create Connected App
        </button>
      </header>

      {appList.length === 0 ? (
        <div
          className="rounded-lg border border-border bg-card p-16 text-center text-muted-foreground"
          data-testid="empty-state"
        >
          <p>No connected apps found.</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border bg-card">
          <table
            className="w-full border-collapse text-sm"
            role="grid"
            aria-label="Connected Apps"
            data-testid="connected-apps-table"
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
                  Client ID
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Rate Limit
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
                  Last Used
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
              {appList.map((app, index) => (
                <tr
                  key={app.id}
                  role="row"
                  className="border-b border-border transition-colors last:border-b-0 hover:bg-muted/50"
                  data-testid={`connected-app-row-${index}`}
                >
                  <td role="gridcell" className="px-4 py-3 text-foreground">
                    {app.name}
                  </td>
                  <td role="gridcell" className="px-4 py-3">
                    <code className="rounded bg-muted px-1.5 py-0.5 font-mono text-xs">
                      {app.clientId}
                    </code>
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-foreground">
                    {app.rateLimitPerHour.toLocaleString()}
                  </td>
                  <td role="gridcell" className="px-4 py-3">
                    <span
                      className={cn(
                        'inline-block rounded px-2 py-0.5 text-xs font-medium',
                        app.active
                          ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                          : 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300'
                      )}
                    >
                      {app.active ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-foreground">
                    {app.lastUsedAt
                      ? formatDate(new Date(app.lastUsedAt), {
                          year: 'numeric',
                          month: 'short',
                          day: 'numeric',
                        })
                      : 'Never'}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-foreground">
                    {formatDate(new Date(app.createdAt), {
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric',
                    })}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-right">
                    <div className="flex justify-end gap-2">
                      <button
                        type="button"
                        className="rounded-md border border-border px-3 py-1.5 text-sm font-medium text-primary hover:border-primary hover:bg-muted"
                        onClick={() => handleEdit(app)}
                        aria-label={`Edit ${app.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className="rounded-md border border-border px-3 py-1.5 text-sm font-medium text-destructive hover:border-destructive hover:bg-destructive/10"
                        onClick={() => handleDeleteClick(app)}
                        aria-label={`Delete ${app.name}`}
                        data-testid={`delete-button-${index}`}
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {isFormOpen && (
        <ConnectedAppForm
          connectedApp={editingApp}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={isSubmitting}
        />
      )}
      {createdCredentials && (
        <CredentialsDialog
          clientId={createdCredentials.clientId}
          clientSecret={createdCredentials.clientSecret}
          onClose={handleCredentialsClose}
        />
      )}

      <ConfirmDialog
        open={deleteDialogOpen}
        title="Delete Connected App"
        message="Are you sure you want to delete this connected app? This action cannot be undone."
        confirmLabel="Delete"
        cancelLabel="Cancel"
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  )
}

export default ConnectedAppsPage
