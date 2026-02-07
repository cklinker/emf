import React, { useState, useCallback, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import styles from './ReportsPage.module.css'

interface Report {
  id: string
  name: string
  description: string | null
  reportType: string
  primaryCollectionId: string
  columns: string | null
  filters: string | null
  scope: string
  accessLevel: string
  folderId: string | null
  createdBy: string
  createdAt: string
  updatedAt: string
}

interface ReportFormData {
  name: string
  description: string
  reportType: string
  primaryCollectionId: string
  columns: string
  filters: string
  scope: string
  accessLevel: string
}

interface FormErrors {
  name?: string
  primaryCollectionId?: string
}

export interface ReportsPageProps {
  testId?: string
}

function validateForm(data: ReportFormData): FormErrors {
  const errors: FormErrors = {}
  if (!data.name.trim()) {
    errors.name = 'Name is required'
  } else if (data.name.length > 100) {
    errors.name = 'Name must be 100 characters or fewer'
  }
  if (!data.primaryCollectionId.trim()) {
    errors.primaryCollectionId = 'Primary collection is required'
  }
  return errors
}

interface ReportFormProps {
  report?: Report
  onSubmit: (data: ReportFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function ReportForm({
  report,
  onSubmit,
  onCancel,
  isSubmitting,
}: ReportFormProps): React.ReactElement {
  const isEditing = !!report
  const [formData, setFormData] = useState<ReportFormData>({
    name: report?.name ?? '',
    description: report?.description ?? '',
    reportType: report?.reportType ?? 'TABULAR',
    primaryCollectionId: report?.primaryCollectionId ?? '',
    columns: report?.columns ?? '',
    filters: report?.filters ?? '',
    scope: report?.scope ?? 'ALL_RECORDS',
    accessLevel: report?.accessLevel ?? 'PRIVATE',
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const nameInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    (field: keyof ReportFormData, value: string) => {
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
      setTouched({ name: true, primaryCollectionId: true })
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

  const title = isEditing ? 'Edit Report' : 'Create Report'

  return (
    <div
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="report-form-overlay"
      role="presentation"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="report-form-title"
        data-testid="report-form-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="report-form-title" className={styles.modalTitle}>
            {title}
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onCancel}
            aria-label="Close"
            data-testid="report-form-close"
          >
            &times;
          </button>
        </div>
        <div className={styles.modalBody}>
          <form className={styles.form} onSubmit={handleSubmit} noValidate>
            <div className={styles.formGroup}>
              <label htmlFor="report-name" className={styles.formLabel}>
                Name
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="report-name"
                type="text"
                className={`${styles.formInput} ${touched.name && errors.name ? styles.hasError : ''}`}
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                onBlur={() => handleBlur('name')}
                placeholder="Enter report name"
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                disabled={isSubmitting}
                data-testid="report-name-input"
              />
              {touched.name && errors.name && (
                <span className={styles.formError} role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="report-description" className={styles.formLabel}>
                Description
              </label>
              <textarea
                id="report-description"
                className={`${styles.formInput} ${styles.formTextarea}`}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                placeholder="Enter report description"
                disabled={isSubmitting}
                rows={3}
                data-testid="report-description-input"
              />
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="report-type" className={styles.formLabel}>
                Report Type
              </label>
              <select
                id="report-type"
                className={styles.formInput}
                value={formData.reportType}
                onChange={(e) => handleChange('reportType', e.target.value)}
                disabled={isSubmitting}
                data-testid="report-type-input"
              >
                <option value="TABULAR">Tabular</option>
                <option value="SUMMARY">Summary</option>
                <option value="MATRIX">Matrix</option>
              </select>
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="report-collection" className={styles.formLabel}>
                Primary Collection
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                id="report-collection"
                type="text"
                className={`${styles.formInput} ${touched.primaryCollectionId && errors.primaryCollectionId ? styles.hasError : ''}`}
                value={formData.primaryCollectionId}
                onChange={(e) => handleChange('primaryCollectionId', e.target.value)}
                onBlur={() => handleBlur('primaryCollectionId')}
                placeholder="Enter collection ID"
                aria-required="true"
                aria-invalid={touched.primaryCollectionId && !!errors.primaryCollectionId}
                disabled={isSubmitting}
                data-testid="report-collection-input"
              />
              {touched.primaryCollectionId && errors.primaryCollectionId && (
                <span className={styles.formError} role="alert">
                  {errors.primaryCollectionId}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="report-columns" className={styles.formLabel}>
                Columns
              </label>
              <textarea
                id="report-columns"
                className={`${styles.formInput} ${styles.formTextarea}`}
                value={formData.columns}
                onChange={(e) => handleChange('columns', e.target.value)}
                placeholder="Enter column definitions (JSON)"
                disabled={isSubmitting}
                rows={3}
                data-testid="report-columns-input"
              />
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="report-filters" className={styles.formLabel}>
                Filters
              </label>
              <textarea
                id="report-filters"
                className={`${styles.formInput} ${styles.formTextarea}`}
                value={formData.filters}
                onChange={(e) => handleChange('filters', e.target.value)}
                placeholder="Enter filter definitions (JSON)"
                disabled={isSubmitting}
                rows={3}
                data-testid="report-filters-input"
              />
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="report-scope" className={styles.formLabel}>
                Scope
              </label>
              <select
                id="report-scope"
                className={styles.formInput}
                value={formData.scope}
                onChange={(e) => handleChange('scope', e.target.value)}
                disabled={isSubmitting}
                data-testid="report-scope-input"
              >
                <option value="MY_RECORDS">My Records</option>
                <option value="ALL_RECORDS">All Records</option>
                <option value="MY_TEAM_RECORDS">My Team Records</option>
              </select>
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="report-access" className={styles.formLabel}>
                Access Level
              </label>
              <select
                id="report-access"
                className={styles.formInput}
                value={formData.accessLevel}
                onChange={(e) => handleChange('accessLevel', e.target.value)}
                disabled={isSubmitting}
                data-testid="report-access-input"
              >
                <option value="PRIVATE">Private</option>
                <option value="PUBLIC">Public</option>
                <option value="HIDDEN">Hidden</option>
              </select>
            </div>

            <div className={styles.formActions}>
              <button
                type="button"
                className={styles.cancelButton}
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="report-form-cancel"
              >
                Cancel
              </button>
              <button
                type="submit"
                className={styles.submitButton}
                disabled={isSubmitting}
                data-testid="report-form-submit"
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

export function ReportsPage({ testId = 'reports-page' }: ReportsPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingReport, setEditingReport] = useState<Report | undefined>(undefined)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [reportToDelete, setReportToDelete] = useState<Report | null>(null)

  const {
    data: reports,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['reports'],
    queryFn: () => apiClient.get<Report[]>('/control/reports?tenantId=default'),
  })

  const reportList: Report[] = reports ?? []

  const createMutation = useMutation({
    mutationFn: (data: ReportFormData) =>
      apiClient.post<Report>('/control/reports?tenantId=default&userId=system', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reports'] })
      showToast('Report created successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: ReportFormData }) =>
      apiClient.put<Report>(`/control/reports/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reports'] })
      showToast('Report updated successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/control/reports/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reports'] })
      showToast('Report deleted successfully', 'success')
      setDeleteDialogOpen(false)
      setReportToDelete(null)
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const handleCreate = useCallback(() => {
    setEditingReport(undefined)
    setIsFormOpen(true)
  }, [])

  const handleEdit = useCallback((report: Report) => {
    setEditingReport(report)
    setIsFormOpen(true)
  }, [])

  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingReport(undefined)
  }, [])

  const handleFormSubmit = useCallback(
    (data: ReportFormData) => {
      if (editingReport) {
        updateMutation.mutate({ id: editingReport.id, data })
      } else {
        createMutation.mutate(data)
      }
    },
    [editingReport, createMutation, updateMutation]
  )

  const handleDeleteClick = useCallback((report: Report) => {
    setReportToDelete(report)
    setDeleteDialogOpen(true)
  }, [])

  const handleDeleteConfirm = useCallback(() => {
    if (reportToDelete) {
      deleteMutation.mutate(reportToDelete.id)
    }
  }, [reportToDelete, deleteMutation])

  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setReportToDelete(null)
  }, [])

  if (isLoading) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label="Loading reports..." />
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
        <h1 className={styles.title}>Reports</h1>
        <button
          type="button"
          className={styles.createButton}
          onClick={handleCreate}
          aria-label="Create Report"
          data-testid="add-report-button"
        >
          Create Report
        </button>
      </header>

      {reportList.length === 0 ? (
        <div className={styles.emptyState} data-testid="empty-state">
          <p>No reports found. Create your first report to get started.</p>
        </div>
      ) : (
        <div className={styles.tableContainer}>
          <table
            className={styles.table}
            role="grid"
            aria-label="Reports"
            data-testid="reports-table"
          >
            <thead>
              <tr role="row">
                <th role="columnheader" scope="col">
                  Name
                </th>
                <th role="columnheader" scope="col">
                  Type
                </th>
                <th role="columnheader" scope="col">
                  Collection
                </th>
                <th role="columnheader" scope="col">
                  Scope
                </th>
                <th role="columnheader" scope="col">
                  Access
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
              {reportList.map((report, index) => (
                <tr
                  key={report.id}
                  role="row"
                  className={styles.tableRow}
                  data-testid={`report-row-${index}`}
                >
                  <td role="gridcell">{report.name}</td>
                  <td role="gridcell">
                    <span className={styles.badge}>{report.reportType}</span>
                  </td>
                  <td role="gridcell">{report.primaryCollectionId}</td>
                  <td role="gridcell">
                    <span className={styles.badge}>{report.scope}</span>
                  </td>
                  <td role="gridcell">
                    <span className={styles.badge}>{report.accessLevel}</span>
                  </td>
                  <td role="gridcell">
                    {formatDate(new Date(report.createdAt), {
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
                        onClick={() => handleEdit(report)}
                        aria-label={`Edit ${report.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className={`${styles.actionButton} ${styles.deleteButton}`}
                        onClick={() => handleDeleteClick(report)}
                        aria-label={`Delete ${report.name}`}
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
        <ReportForm
          report={editingReport}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={isSubmitting}
        />
      )}

      <ConfirmDialog
        open={deleteDialogOpen}
        title="Delete Report"
        message="Are you sure you want to delete this report? This action cannot be undone."
        confirmLabel="Delete"
        cancelLabel="Cancel"
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  )
}

export default ReportsPage
