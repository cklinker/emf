import React, { useState, useCallback, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import styles from './ConnectedAppsPage.module.css'

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
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="connected-app-form-overlay"
      role="presentation"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="connected-app-form-title"
        data-testid="connected-app-form-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="connected-app-form-title" className={styles.modalTitle}>
            {title}
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onCancel}
            aria-label="Close"
            data-testid="connected-app-form-close"
          >
            &times;
          </button>
        </div>
        <div className={styles.modalBody}>
          <form className={styles.form} onSubmit={handleSubmit} noValidate>
            <div className={styles.formGroup}>
              <label htmlFor="connected-app-name" className={styles.formLabel}>
                Name
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="connected-app-name"
                type="text"
                className={`${styles.formInput} ${touched.name && errors.name ? styles.hasError : ''}`}
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
                <span className={styles.formError} role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="connected-app-description" className={styles.formLabel}>
                Description
              </label>
              <textarea
                id="connected-app-description"
                className={`${styles.formInput} ${styles.formTextarea} ${touched.description && errors.description ? styles.hasError : ''}`}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                onBlur={() => handleBlur('description')}
                placeholder="Enter connected app description"
                disabled={isSubmitting}
                rows={3}
                data-testid="connected-app-description-input"
              />
              {touched.description && errors.description && (
                <span className={styles.formError} role="alert">
                  {errors.description}
                </span>
              )}
            </div>

            {!isEditing && (
              <div className={styles.formGroup}>
                <label htmlFor="connected-app-scopes" className={styles.formLabel}>
                  Scopes
                </label>
                <input
                  id="connected-app-scopes"
                  type="text"
                  className={`${styles.formInput} ${touched.scopes && errors.scopes ? styles.hasError : ''}`}
                  value={formData.scopes}
                  onChange={(e) => handleChange('scopes', e.target.value)}
                  onBlur={() => handleBlur('scopes')}
                  placeholder='["api"]'
                  disabled={isSubmitting}
                  data-testid="connected-app-scopes-input"
                />
                <span className={styles.formHint}>JSON array of scope strings</span>
                {touched.scopes && errors.scopes && (
                  <span className={styles.formError} role="alert">
                    {errors.scopes}
                  </span>
                )}
              </div>
            )}

            <div className={styles.formGroup}>
              <label htmlFor="connected-app-rate-limit" className={styles.formLabel}>
                Rate Limit (per hour)
              </label>
              <input
                id="connected-app-rate-limit"
                type="number"
                className={`${styles.formInput} ${touched.rateLimitPerHour && errors.rateLimitPerHour ? styles.hasError : ''}`}
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
                <span className={styles.formError} role="alert">
                  {errors.rateLimitPerHour}
                </span>
              )}
            </div>

            <div className={styles.checkboxGroup}>
              <input
                id="connected-app-active"
                type="checkbox"
                checked={formData.active}
                onChange={(e) => handleChange('active', e.target.checked)}
                disabled={isSubmitting}
                data-testid="connected-app-active-input"
              />
              <label htmlFor="connected-app-active" className={styles.formLabel}>
                Active
              </label>
            </div>

            <div className={styles.formActions}>
              <button
                type="button"
                className={styles.cancelButton}
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="connected-app-form-cancel"
              >
                Cancel
              </button>
              <button
                type="submit"
                className={styles.submitButton}
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
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onClose()}
      onKeyDown={handleKeyDown}
      data-testid="credentials-dialog-overlay"
      role="presentation"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="credentials-dialog-title"
        data-testid="credentials-dialog-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="credentials-dialog-title" className={styles.modalTitle}>
            Connected App Created
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onClose}
            aria-label="Close"
            data-testid="credentials-dialog-close"
          >
            &times;
          </button>
        </div>
        <div className={styles.modalBody}>
          <div className={styles.credentialsBox}>
            <p className={styles.credentialsWarning}>
              Save these credentials now. The client secret will not be shown again.
            </p>
            <div className={styles.credentialField}>
              <span className={styles.credentialLabel}>Client ID</span>
              <code className={styles.credentialValue} data-testid="credentials-client-id">
                {clientId}
              </code>
            </div>
            <div className={styles.credentialField}>
              <span className={styles.credentialLabel}>Client Secret</span>
              <code className={styles.credentialValue} data-testid="credentials-client-secret">
                {clientSecret}
              </code>
            </div>
          </div>
          <div className={styles.formActions}>
            <button
              type="button"
              className={styles.submitButton}
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
    queryFn: () => apiClient.get<ConnectedApp[]>('/control/connected-apps?tenantId=default'),
  })

  const appList: ConnectedApp[] = connectedApps ?? []

  const createMutation = useMutation({
    mutationFn: (data: ConnectedAppFormData) =>
      apiClient.post<ConnectedAppCreatedResponse>(
        '/control/connected-apps?tenantId=default&userId=system',
        data
      ),
    onSuccess: (result: ConnectedAppCreatedResponse) => {
      queryClient.invalidateQueries({ queryKey: ['connected-apps'] })
      showToast('Connected app created successfully', 'success')
      handleCloseForm()
      setCreatedCredentials({
        clientId: result.clientId,
        clientSecret: result.clientSecret,
      })
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
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label="Loading connected apps..." />
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className={styles.container} data-testid={testId}>
        <ErrorMessage
          error={error instanceof Error ? error : new Error('An error occurred')}
          onRetry={() => refetch()}
        />
      </div>
    )
  }

  const isSubmitting = createMutation.isPending || updateMutation.isPending

  return (
    <div className={styles.container} data-testid={testId}>
      <header className={styles.header}>
        <h1 className={styles.title}>Connected Apps</h1>
        <button
          type="button"
          className={styles.createButton}
          onClick={handleCreate}
          aria-label="Create Connected App"
          data-testid="add-connected-app-button"
        >
          Create Connected App
        </button>
      </header>

      {appList.length === 0 ? (
        <div className={styles.emptyState} data-testid="empty-state">
          <p>No connected apps found.</p>
        </div>
      ) : (
        <div className={styles.tableContainer}>
          <table
            className={styles.table}
            role="grid"
            aria-label="Connected Apps"
            data-testid="connected-apps-table"
          >
            <thead>
              <tr role="row">
                <th role="columnheader" scope="col">
                  Name
                </th>
                <th role="columnheader" scope="col">
                  Client ID
                </th>
                <th role="columnheader" scope="col">
                  Rate Limit
                </th>
                <th role="columnheader" scope="col">
                  Active
                </th>
                <th role="columnheader" scope="col">
                  Last Used
                </th>
                <th role="columnheader" scope="col">
                  Created
                </th>
                <th role="columnheader" scope="col">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody>
              {appList.map((app, index) => (
                <tr
                  key={app.id}
                  role="row"
                  className={styles.tableRow}
                  data-testid={`connected-app-row-${index}`}
                >
                  <td role="gridcell">{app.name}</td>
                  <td role="gridcell">
                    <code>{app.clientId}</code>
                  </td>
                  <td role="gridcell">{app.rateLimitPerHour.toLocaleString()}</td>
                  <td role="gridcell">
                    <span
                      className={`${styles.boolBadge} ${app.active ? styles.boolTrue : styles.boolFalse}`}
                    >
                      {app.active ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td role="gridcell">
                    {app.lastUsedAt
                      ? formatDate(new Date(app.lastUsedAt), {
                          year: 'numeric',
                          month: 'short',
                          day: 'numeric',
                        })
                      : 'Never'}
                  </td>
                  <td role="gridcell">
                    {formatDate(new Date(app.createdAt), {
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric',
                    })}
                  </td>
                  <td role="gridcell" className={styles.actionsCell}>
                    <div className={styles.actions}>
                      <button
                        type="button"
                        className={styles.actionButton}
                        onClick={() => handleEdit(app)}
                        aria-label={`Edit ${app.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className={`${styles.actionButton} ${styles.deleteButton}`}
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
