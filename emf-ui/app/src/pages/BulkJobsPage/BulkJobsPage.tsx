import React, { useState, useCallback, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import { getTenantId } from '../../hooks'
import styles from './BulkJobsPage.module.css'

interface BulkJob {
  id: string
  collectionId: string
  operation: string
  status: string
  totalRecords: number
  processedRecords: number
  successRecords: number
  errorRecords: number
  externalIdField: string | null
  batchSize: number
  createdBy: string
  startedAt: string | null
  completedAt: string | null
  createdAt: string
  updatedAt: string
}

interface BulkJobFormData {
  collectionId: string
  operation: string
  externalIdField: string
  batchSize: number
}

interface FormErrors {
  collectionId?: string
  batchSize?: string
}

export interface BulkJobsPageProps {
  testId?: string
}

function validateForm(data: BulkJobFormData): FormErrors {
  const errors: FormErrors = {}
  if (!data.collectionId.trim()) {
    errors.collectionId = 'Collection ID is required'
  }
  if (data.batchSize < 1 || data.batchSize > 10000) {
    errors.batchSize = 'Batch size must be between 1 and 10000'
  }
  return errors
}

function getStatusBadgeClass(status: string): string {
  switch (status) {
    case 'QUEUED':
      return styles.badgeGray
    case 'PROCESSING':
      return styles.badgeBlue
    case 'COMPLETED':
      return styles.badgeGreen
    case 'FAILED':
      return styles.badgeRed
    case 'ABORTED':
      return styles.badgeOrange
    default:
      return styles.badgeGray
  }
}

interface BulkJobFormProps {
  onSubmit: (data: BulkJobFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function BulkJobForm({ onSubmit, onCancel, isSubmitting }: BulkJobFormProps): React.ReactElement {
  const [formData, setFormData] = useState<BulkJobFormData>({
    collectionId: '',
    operation: 'INSERT',
    externalIdField: '',
    batchSize: 200,
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const collectionInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    collectionInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    (field: keyof BulkJobFormData, value: string | number) => {
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
      setTouched({ collectionId: true, batchSize: true })
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

  return (
    <div
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="bulk-job-form-overlay"
      role="presentation"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="bulk-job-form-title"
        data-testid="bulk-job-form-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="bulk-job-form-title" className={styles.modalTitle}>
            Create Bulk Job
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onCancel}
            aria-label="Close"
            data-testid="bulk-job-form-close"
          >
            &times;
          </button>
        </div>
        <div className={styles.modalBody}>
          <form className={styles.form} onSubmit={handleSubmit} noValidate>
            <div className={styles.formGroup}>
              <label htmlFor="bulk-job-collection-id" className={styles.formLabel}>
                Collection ID
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={collectionInputRef}
                id="bulk-job-collection-id"
                type="text"
                className={`${styles.formInput} ${touched.collectionId && errors.collectionId ? styles.hasError : ''}`}
                value={formData.collectionId}
                onChange={(e) => handleChange('collectionId', e.target.value)}
                onBlur={() => handleBlur('collectionId')}
                placeholder="Enter collection ID"
                aria-required="true"
                aria-invalid={touched.collectionId && !!errors.collectionId}
                disabled={isSubmitting}
                data-testid="bulk-job-collection-id-input"
              />
              {touched.collectionId && errors.collectionId && (
                <span className={styles.formError} role="alert">
                  {errors.collectionId}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="bulk-job-operation" className={styles.formLabel}>
                Operation
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <select
                id="bulk-job-operation"
                className={styles.formInput}
                value={formData.operation}
                onChange={(e) => handleChange('operation', e.target.value)}
                disabled={isSubmitting}
                data-testid="bulk-job-operation-input"
              >
                <option value="INSERT">Insert</option>
                <option value="UPDATE">Update</option>
                <option value="UPSERT">Upsert</option>
                <option value="DELETE">Delete</option>
              </select>
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="bulk-job-external-id" className={styles.formLabel}>
                External ID Field
              </label>
              <input
                id="bulk-job-external-id"
                type="text"
                className={styles.formInput}
                value={formData.externalIdField}
                onChange={(e) => handleChange('externalIdField', e.target.value)}
                placeholder="Enter external ID field (optional)"
                disabled={isSubmitting}
                data-testid="bulk-job-external-id-input"
              />
              <span className={styles.formHint}>Required for UPSERT operations</span>
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="bulk-job-batch-size" className={styles.formLabel}>
                Batch Size
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                id="bulk-job-batch-size"
                type="number"
                className={`${styles.formInput} ${touched.batchSize && errors.batchSize ? styles.hasError : ''}`}
                value={formData.batchSize}
                onChange={(e) => handleChange('batchSize', parseInt(e.target.value, 10) || 1)}
                onBlur={() => handleBlur('batchSize')}
                min={1}
                max={10000}
                disabled={isSubmitting}
                data-testid="bulk-job-batch-size-input"
              />
              {touched.batchSize && errors.batchSize && (
                <span className={styles.formError} role="alert">
                  {errors.batchSize}
                </span>
              )}
            </div>

            <div className={styles.formActions}>
              <button
                type="button"
                className={styles.cancelButton}
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="bulk-job-form-cancel"
              >
                Cancel
              </button>
              <button
                type="submit"
                className={styles.submitButton}
                disabled={isSubmitting}
                data-testid="bulk-job-form-submit"
              >
                {isSubmitting ? 'Creating...' : 'Create Job'}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}

export function BulkJobsPage({ testId = 'bulk-jobs-page' }: BulkJobsPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [abortDialogOpen, setAbortDialogOpen] = useState(false)
  const [jobToAbort, setJobToAbort] = useState<BulkJob | null>(null)

  const {
    data: bulkJobs,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['bulk-jobs'],
    queryFn: () => apiClient.get<BulkJob[]>(`/control/bulk-jobs?tenantId=${getTenantId()}`),
    refetchInterval: 5000,
  })

  const jobList: BulkJob[] = bulkJobs ?? []

  const createMutation = useMutation({
    mutationFn: (data: BulkJobFormData) =>
      apiClient.post<BulkJob>(`/control/bulk-jobs?tenantId=${getTenantId()}&userId=system`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bulk-jobs'] })
      showToast('Bulk job created successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const abortMutation = useMutation({
    mutationFn: (id: string) => apiClient.post<void>(`/control/bulk-jobs/${id}/abort`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bulk-jobs'] })
      showToast('Bulk job aborted successfully', 'success')
      setAbortDialogOpen(false)
      setJobToAbort(null)
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const handleCreate = useCallback(() => {
    setIsFormOpen(true)
  }, [])

  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
  }, [])

  const handleFormSubmit = useCallback(
    (data: BulkJobFormData) => {
      createMutation.mutate(data)
    },
    [createMutation]
  )

  const handleAbortClick = useCallback((job: BulkJob) => {
    setJobToAbort(job)
    setAbortDialogOpen(true)
  }, [])

  const handleAbortConfirm = useCallback(() => {
    if (jobToAbort) {
      abortMutation.mutate(jobToAbort.id)
    }
  }, [jobToAbort, abortMutation])

  const handleAbortCancel = useCallback(() => {
    setAbortDialogOpen(false)
    setJobToAbort(null)
  }, [])

  if (isLoading) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label="Loading bulk jobs..." />
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

  const isSubmitting = createMutation.isPending

  return (
    <div className={styles.container} data-testid={testId}>
      <header className={styles.header}>
        <h1 className={styles.title}>Bulk Jobs</h1>
        <button
          type="button"
          className={styles.createButton}
          onClick={handleCreate}
          aria-label="Create Bulk Job"
          data-testid="add-bulk-job-button"
        >
          Create Bulk Job
        </button>
      </header>

      {jobList.length === 0 ? (
        <div className={styles.emptyState} data-testid="empty-state">
          <p>No bulk jobs found.</p>
        </div>
      ) : (
        <div className={styles.tableContainer}>
          <table
            className={styles.table}
            role="grid"
            aria-label="Bulk Jobs"
            data-testid="bulk-jobs-table"
          >
            <thead>
              <tr role="row">
                <th role="columnheader" scope="col">
                  Collection ID
                </th>
                <th role="columnheader" scope="col">
                  Operation
                </th>
                <th role="columnheader" scope="col">
                  Status
                </th>
                <th role="columnheader" scope="col">
                  Progress
                </th>
                <th role="columnheader" scope="col">
                  Success
                </th>
                <th role="columnheader" scope="col">
                  Errors
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
              {jobList.map((job, index) => {
                const progressPercent =
                  job.totalRecords > 0
                    ? Math.round((job.processedRecords / job.totalRecords) * 100)
                    : 0
                const canAbort = job.status === 'QUEUED' || job.status === 'PROCESSING'

                return (
                  <tr
                    key={job.id}
                    role="row"
                    className={styles.tableRow}
                    data-testid={`bulk-job-row-${index}`}
                  >
                    <td role="gridcell">{job.collectionId}</td>
                    <td role="gridcell">
                      <span className={styles.badge}>{job.operation}</span>
                    </td>
                    <td role="gridcell">
                      <span className={`${styles.badge} ${getStatusBadgeClass(job.status)}`}>
                        {job.status}
                      </span>
                    </td>
                    <td role="gridcell">
                      <div className={styles.progressBar}>
                        <div className={styles.progressTrack}>
                          <div
                            className={styles.progressFill}
                            style={{ width: `${progressPercent}%` }}
                          />
                        </div>
                        <span className={styles.progressText}>
                          {job.processedRecords}/{job.totalRecords}
                        </span>
                      </div>
                    </td>
                    <td role="gridcell">{job.successRecords}</td>
                    <td role="gridcell">{job.errorRecords}</td>
                    <td role="gridcell">
                      {formatDate(new Date(job.createdAt), {
                        year: 'numeric',
                        month: 'short',
                        day: 'numeric',
                      })}
                    </td>
                    <td role="gridcell" className={styles.actionsCell}>
                      <div className={styles.actions}>
                        {canAbort && (
                          <button
                            type="button"
                            className={`${styles.actionButton} ${styles.abortButton}`}
                            onClick={() => handleAbortClick(job)}
                            aria-label={`Abort job ${job.id}`}
                            data-testid={`abort-button-${index}`}
                          >
                            Abort
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {isFormOpen && (
        <BulkJobForm
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={isSubmitting}
        />
      )}

      <ConfirmDialog
        open={abortDialogOpen}
        title="Abort Bulk Job"
        message="Are you sure you want to abort this bulk job? Records already processed will not be rolled back."
        confirmLabel="Abort"
        cancelLabel="Cancel"
        onConfirm={handleAbortConfirm}
        onCancel={handleAbortCancel}
        variant="danger"
      />
    </div>
  )
}

export default BulkJobsPage
