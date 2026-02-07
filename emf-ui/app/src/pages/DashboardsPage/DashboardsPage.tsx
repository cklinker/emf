import React, { useState, useCallback, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import styles from './DashboardsPage.module.css'

interface Dashboard {
  id: string
  name: string
  description: string | null
  accessLevel: string
  dynamic: boolean
  columnCount: number
  createdBy: string
  createdAt: string
  updatedAt: string
  components: unknown[]
}

interface DashboardFormData {
  name: string
  description: string
  accessLevel: string
  dynamic: boolean
  columnCount: number
}

interface FormErrors {
  name?: string
  description?: string
  columnCount?: string
}

export interface DashboardsPageProps {
  testId?: string
}

function validateForm(data: DashboardFormData): FormErrors {
  const errors: FormErrors = {}
  if (!data.name.trim()) {
    errors.name = 'Name is required'
  } else if (data.name.length > 100) {
    errors.name = 'Name must be 100 characters or fewer'
  }
  if (data.description && data.description.length > 500) {
    errors.description = 'Description must be 500 characters or fewer'
  }
  if (data.columnCount < 1 || data.columnCount > 12) {
    errors.columnCount = 'Column count must be between 1 and 12'
  }
  return errors
}

interface DashboardFormProps {
  dashboard?: Dashboard
  onSubmit: (data: DashboardFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function DashboardForm({
  dashboard,
  onSubmit,
  onCancel,
  isSubmitting,
}: DashboardFormProps): React.ReactElement {
  const isEditing = !!dashboard
  const [formData, setFormData] = useState<DashboardFormData>({
    name: dashboard?.name ?? '',
    description: dashboard?.description ?? '',
    accessLevel: dashboard?.accessLevel ?? 'PRIVATE',
    dynamic: dashboard?.dynamic ?? false,
    columnCount: dashboard?.columnCount ?? 3,
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const nameInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    (field: keyof DashboardFormData, value: string | boolean | number) => {
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
      setTouched({ name: true, description: true, columnCount: true })
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

  const title = isEditing ? 'Edit Dashboard' : 'Create Dashboard'

  return (
    <div
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="dashboard-form-overlay"
      role="presentation"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="dashboard-form-title"
        data-testid="dashboard-form-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="dashboard-form-title" className={styles.modalTitle}>
            {title}
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onCancel}
            aria-label="Close"
            data-testid="dashboard-form-close"
          >
            &times;
          </button>
        </div>
        <div className={styles.modalBody}>
          <form className={styles.form} onSubmit={handleSubmit} noValidate>
            <div className={styles.formGroup}>
              <label htmlFor="dashboard-name" className={styles.formLabel}>
                Name
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="dashboard-name"
                type="text"
                className={`${styles.formInput} ${touched.name && errors.name ? styles.hasError : ''}`}
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                onBlur={() => handleBlur('name')}
                placeholder="Enter dashboard name"
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                disabled={isSubmitting}
                data-testid="dashboard-name-input"
              />
              {touched.name && errors.name && (
                <span className={styles.formError} role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="dashboard-description" className={styles.formLabel}>
                Description
              </label>
              <textarea
                id="dashboard-description"
                className={`${styles.formInput} ${styles.formTextarea} ${touched.description && errors.description ? styles.hasError : ''}`}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                onBlur={() => handleBlur('description')}
                placeholder="Enter dashboard description"
                disabled={isSubmitting}
                rows={3}
                data-testid="dashboard-description-input"
              />
              {touched.description && errors.description && (
                <span className={styles.formError} role="alert">
                  {errors.description}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="dashboard-access-level" className={styles.formLabel}>
                Access Level
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <select
                id="dashboard-access-level"
                className={styles.formInput}
                value={formData.accessLevel}
                onChange={(e) => handleChange('accessLevel', e.target.value)}
                disabled={isSubmitting}
                data-testid="dashboard-access-level-input"
              >
                <option value="PRIVATE">Private</option>
                <option value="PUBLIC">Public</option>
                <option value="HIDDEN">Hidden</option>
              </select>
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="dashboard-column-count" className={styles.formLabel}>
                Column Count
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                id="dashboard-column-count"
                type="number"
                className={`${styles.formInput} ${touched.columnCount && errors.columnCount ? styles.hasError : ''}`}
                value={formData.columnCount}
                onChange={(e) => handleChange('columnCount', parseInt(e.target.value, 10) || 1)}
                onBlur={() => handleBlur('columnCount')}
                min={1}
                max={12}
                disabled={isSubmitting}
                data-testid="dashboard-column-count-input"
              />
              {touched.columnCount && errors.columnCount && (
                <span className={styles.formError} role="alert">
                  {errors.columnCount}
                </span>
              )}
            </div>

            <div className={styles.checkboxGroup}>
              <input
                id="dashboard-dynamic"
                type="checkbox"
                checked={formData.dynamic}
                onChange={(e) => handleChange('dynamic', e.target.checked)}
                disabled={isSubmitting}
                data-testid="dashboard-dynamic-input"
              />
              <label htmlFor="dashboard-dynamic" className={styles.formLabel}>
                Dynamic
              </label>
            </div>

            <div className={styles.formActions}>
              <button
                type="button"
                className={styles.cancelButton}
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="dashboard-form-cancel"
              >
                Cancel
              </button>
              <button
                type="submit"
                className={styles.submitButton}
                disabled={isSubmitting}
                data-testid="dashboard-form-submit"
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

export function DashboardsPage({
  testId = 'dashboards-page',
}: DashboardsPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingDashboard, setEditingDashboard] = useState<Dashboard | undefined>(undefined)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [dashboardToDelete, setDashboardToDelete] = useState<Dashboard | null>(null)

  const {
    data: dashboards,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['dashboards'],
    queryFn: () => apiClient.get<Dashboard[]>('/control/dashboards?tenantId=default'),
  })

  const dashboardList: Dashboard[] = dashboards ?? []

  const createMutation = useMutation({
    mutationFn: (data: DashboardFormData) =>
      apiClient.post<Dashboard>('/control/dashboards?tenantId=default&userId=system', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dashboards'] })
      showToast('Dashboard created successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: DashboardFormData }) =>
      apiClient.put<Dashboard>(`/control/dashboards/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dashboards'] })
      showToast('Dashboard updated successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/control/dashboards/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dashboards'] })
      showToast('Dashboard deleted successfully', 'success')
      setDeleteDialogOpen(false)
      setDashboardToDelete(null)
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const handleCreate = useCallback(() => {
    setEditingDashboard(undefined)
    setIsFormOpen(true)
  }, [])

  const handleEdit = useCallback((dashboard: Dashboard) => {
    setEditingDashboard(dashboard)
    setIsFormOpen(true)
  }, [])

  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingDashboard(undefined)
  }, [])

  const handleFormSubmit = useCallback(
    (data: DashboardFormData) => {
      if (editingDashboard) {
        updateMutation.mutate({ id: editingDashboard.id, data })
      } else {
        createMutation.mutate(data)
      }
    },
    [editingDashboard, createMutation, updateMutation]
  )

  const handleDeleteClick = useCallback((dashboard: Dashboard) => {
    setDashboardToDelete(dashboard)
    setDeleteDialogOpen(true)
  }, [])

  const handleDeleteConfirm = useCallback(() => {
    if (dashboardToDelete) {
      deleteMutation.mutate(dashboardToDelete.id)
    }
  }, [dashboardToDelete, deleteMutation])

  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setDashboardToDelete(null)
  }, [])

  if (isLoading) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label="Loading dashboards..." />
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
        <h1 className={styles.title}>Dashboards</h1>
        <button
          type="button"
          className={styles.createButton}
          onClick={handleCreate}
          aria-label="Create Dashboard"
          data-testid="add-dashboard-button"
        >
          Create Dashboard
        </button>
      </header>

      {dashboardList.length === 0 ? (
        <div className={styles.emptyState} data-testid="empty-state">
          <p>No dashboards found.</p>
        </div>
      ) : (
        <div className={styles.tableContainer}>
          <table
            className={styles.table}
            role="grid"
            aria-label="Dashboards"
            data-testid="dashboards-table"
          >
            <thead>
              <tr role="row">
                <th role="columnheader" scope="col">
                  Name
                </th>
                <th role="columnheader" scope="col">
                  Access Level
                </th>
                <th role="columnheader" scope="col">
                  Columns
                </th>
                <th role="columnheader" scope="col">
                  Dynamic
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
              {dashboardList.map((dashboard, index) => (
                <tr
                  key={dashboard.id}
                  role="row"
                  className={styles.tableRow}
                  data-testid={`dashboard-row-${index}`}
                >
                  <td role="gridcell">{dashboard.name}</td>
                  <td role="gridcell">
                    <span className={styles.badge}>{dashboard.accessLevel}</span>
                  </td>
                  <td role="gridcell">{dashboard.columnCount}</td>
                  <td role="gridcell">
                    <span
                      className={`${styles.boolBadge} ${dashboard.dynamic ? styles.boolTrue : styles.boolFalse}`}
                    >
                      {dashboard.dynamic ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td role="gridcell">
                    {formatDate(new Date(dashboard.createdAt), {
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
                        onClick={() => handleEdit(dashboard)}
                        aria-label={`Edit ${dashboard.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className={`${styles.actionButton} ${styles.deleteButton}`}
                        onClick={() => handleDeleteClick(dashboard)}
                        aria-label={`Delete ${dashboard.name}`}
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
        <DashboardForm
          dashboard={editingDashboard}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={isSubmitting}
        />
      )}

      <ConfirmDialog
        open={deleteDialogOpen}
        title="Delete Dashboard"
        message="Are you sure you want to delete this dashboard? This action cannot be undone."
        confirmLabel="Delete"
        cancelLabel="Cancel"
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  )
}

export default DashboardsPage
