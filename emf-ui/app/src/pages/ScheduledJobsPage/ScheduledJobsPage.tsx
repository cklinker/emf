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
import styles from './ScheduledJobsPage.module.css'

interface ScheduledJob {
  id: string
  name: string
  description: string | null
  jobType: string
  jobReferenceId: string | null
  cronExpression: string
  timezone: string
  active: boolean
  lastRun: string | null
  lastStatus: string | null
  createdBy: string
  createdAt: string
  updatedAt: string
}

interface ScheduledJobFormData {
  name: string
  description: string
  jobType: string
  jobReferenceId: string
  cronExpression: string
  timezone: string
  active: boolean
}

interface FormErrors {
  name?: string
  description?: string
  cronExpression?: string
}

interface JobExecutionLog {
  id: string
  jobId: string
  status: string
  recordsProcessed: number
  errorMessage: string | null
  startedAt: string | null
  completedAt: string | null
  durationMs: number | null
}

export interface ScheduledJobsPageProps {
  testId?: string
}

function validateForm(data: ScheduledJobFormData): FormErrors {
  const errors: FormErrors = {}
  if (!data.name.trim()) {
    errors.name = 'Name is required'
  } else if (data.name.length > 100) {
    errors.name = 'Name must be 100 characters or fewer'
  }
  if (data.description && data.description.length > 500) {
    errors.description = 'Description must be 500 characters or fewer'
  }
  if (!data.cronExpression.trim()) {
    errors.cronExpression = 'Cron expression is required'
  }
  return errors
}

interface ScheduledJobFormProps {
  scheduledJob?: ScheduledJob
  onSubmit: (data: ScheduledJobFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function ScheduledJobForm({
  scheduledJob,
  onSubmit,
  onCancel,
  isSubmitting,
}: ScheduledJobFormProps): React.ReactElement {
  const isEditing = !!scheduledJob
  const [formData, setFormData] = useState<ScheduledJobFormData>({
    name: scheduledJob?.name ?? '',
    description: scheduledJob?.description ?? '',
    jobType: scheduledJob?.jobType ?? 'FLOW',
    jobReferenceId: scheduledJob?.jobReferenceId ?? '',
    cronExpression: scheduledJob?.cronExpression ?? '',
    timezone: scheduledJob?.timezone ?? 'UTC',
    active: scheduledJob?.active ?? true,
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const nameInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    (field: keyof ScheduledJobFormData, value: string | boolean) => {
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
      setTouched({ name: true, description: true, cronExpression: true })
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

  const title = isEditing ? 'Edit Scheduled Job' : 'Create Scheduled Job'

  return (
    <div
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="scheduled-job-form-overlay"
      role="presentation"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="scheduled-job-form-title"
        data-testid="scheduled-job-form-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="scheduled-job-form-title" className={styles.modalTitle}>
            {title}
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onCancel}
            aria-label="Close"
            data-testid="scheduled-job-form-close"
          >
            &times;
          </button>
        </div>
        <div className={styles.modalBody}>
          <form className={styles.form} onSubmit={handleSubmit} noValidate>
            <div className={styles.formGroup}>
              <label htmlFor="scheduled-job-name" className={styles.formLabel}>
                Name
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="scheduled-job-name"
                type="text"
                className={`${styles.formInput} ${touched.name && errors.name ? styles.hasError : ''}`}
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                onBlur={() => handleBlur('name')}
                placeholder="Enter job name"
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                disabled={isSubmitting}
                data-testid="scheduled-job-name-input"
              />
              {touched.name && errors.name && (
                <span className={styles.formError} role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="scheduled-job-description" className={styles.formLabel}>
                Description
              </label>
              <textarea
                id="scheduled-job-description"
                className={`${styles.formInput} ${styles.formTextarea} ${touched.description && errors.description ? styles.hasError : ''}`}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                onBlur={() => handleBlur('description')}
                placeholder="Enter job description"
                disabled={isSubmitting}
                rows={3}
                data-testid="scheduled-job-description-input"
              />
              {touched.description && errors.description && (
                <span className={styles.formError} role="alert">
                  {errors.description}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="scheduled-job-type" className={styles.formLabel}>
                Job Type
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <select
                id="scheduled-job-type"
                className={styles.formInput}
                value={formData.jobType}
                onChange={(e) => handleChange('jobType', e.target.value)}
                disabled={isSubmitting}
                data-testid="scheduled-job-type-input"
              >
                <option value="FLOW">Flow</option>
                <option value="SCRIPT">Script</option>
                <option value="REPORT_EXPORT">Report Export</option>
              </select>
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="scheduled-job-reference-id" className={styles.formLabel}>
                Job Reference ID
              </label>
              <input
                id="scheduled-job-reference-id"
                type="text"
                className={styles.formInput}
                value={formData.jobReferenceId}
                onChange={(e) => handleChange('jobReferenceId', e.target.value)}
                placeholder="Enter job reference ID"
                disabled={isSubmitting}
                data-testid="scheduled-job-reference-id-input"
              />
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="scheduled-job-cron-expression" className={styles.formLabel}>
                Cron Expression
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                id="scheduled-job-cron-expression"
                type="text"
                className={`${styles.formInput} ${touched.cronExpression && errors.cronExpression ? styles.hasError : ''}`}
                value={formData.cronExpression}
                onChange={(e) => handleChange('cronExpression', e.target.value)}
                onBlur={() => handleBlur('cronExpression')}
                placeholder="0 0 * * *"
                aria-required="true"
                aria-invalid={touched.cronExpression && !!errors.cronExpression}
                disabled={isSubmitting}
                data-testid="scheduled-job-cron-expression-input"
              />
              {touched.cronExpression && errors.cronExpression && (
                <span className={styles.formError} role="alert">
                  {errors.cronExpression}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="scheduled-job-timezone" className={styles.formLabel}>
                Timezone
              </label>
              <input
                id="scheduled-job-timezone"
                type="text"
                className={styles.formInput}
                value={formData.timezone}
                onChange={(e) => handleChange('timezone', e.target.value)}
                placeholder="UTC"
                disabled={isSubmitting}
                data-testid="scheduled-job-timezone-input"
              />
            </div>

            <div className={styles.checkboxGroup}>
              <input
                id="scheduled-job-active"
                type="checkbox"
                checked={formData.active}
                onChange={(e) => handleChange('active', e.target.checked)}
                disabled={isSubmitting}
                data-testid="scheduled-job-active-input"
              />
              <label htmlFor="scheduled-job-active" className={styles.formLabel}>
                Active
              </label>
            </div>

            <div className={styles.formActions}>
              <button
                type="button"
                className={styles.cancelButton}
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="scheduled-job-form-cancel"
              >
                Cancel
              </button>
              <button
                type="submit"
                className={styles.submitButton}
                disabled={isSubmitting}
                data-testid="scheduled-job-form-submit"
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

export function ScheduledJobsPage({
  testId = 'scheduled-jobs-page',
}: ScheduledJobsPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingScheduledJob, setEditingScheduledJob] = useState<ScheduledJob | undefined>(
    undefined
  )
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [scheduledJobToDelete, setScheduledJobToDelete] = useState<ScheduledJob | null>(null)
  const [logsItemId, setLogsItemId] = useState<string | null>(null)
  const [logsItemName, setLogsItemName] = useState('')

  const {
    data: logs,
    isLoading: logsLoading,
    error: logsError,
  } = useQuery({
    queryKey: ['scheduled-job-logs', logsItemId],
    queryFn: () => apiClient.get<JobExecutionLog[]>(`/control/scheduled-jobs/${logsItemId}/logs`),
    enabled: !!logsItemId,
  })

  const logColumns: LogColumn<JobExecutionLog>[] = [
    { key: 'status', header: 'Status' },
    { key: 'recordsProcessed', header: 'Records' },
    {
      key: 'durationMs',
      header: 'Duration',
      render: (v) => (v != null ? `${v}ms` : '-'),
    },
    { key: 'errorMessage', header: 'Error' },
    {
      key: 'startedAt',
      header: 'Started At',
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
      header: 'Completed At',
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
    data: scheduledJobs,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['scheduled-jobs'],
    queryFn: () => apiClient.get<ScheduledJob[]>('/control/scheduled-jobs?tenantId=default'),
  })

  const scheduledJobList: ScheduledJob[] = scheduledJobs ?? []

  const createMutation = useMutation({
    mutationFn: (data: ScheduledJobFormData) =>
      apiClient.post<ScheduledJob>('/control/scheduled-jobs?tenantId=default&userId=system', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scheduled-jobs'] })
      showToast('Scheduled job created successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: ScheduledJobFormData }) =>
      apiClient.put<ScheduledJob>(`/control/scheduled-jobs/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scheduled-jobs'] })
      showToast('Scheduled job updated successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/control/scheduled-jobs/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scheduled-jobs'] })
      showToast('Scheduled job deleted successfully', 'success')
      setDeleteDialogOpen(false)
      setScheduledJobToDelete(null)
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const handleCreate = useCallback(() => {
    setEditingScheduledJob(undefined)
    setIsFormOpen(true)
  }, [])

  const handleEdit = useCallback((scheduledJob: ScheduledJob) => {
    setEditingScheduledJob(scheduledJob)
    setIsFormOpen(true)
  }, [])

  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingScheduledJob(undefined)
  }, [])

  const handleFormSubmit = useCallback(
    (data: ScheduledJobFormData) => {
      if (editingScheduledJob) {
        updateMutation.mutate({ id: editingScheduledJob.id, data })
      } else {
        createMutation.mutate(data)
      }
    },
    [editingScheduledJob, createMutation, updateMutation]
  )

  const handleDeleteClick = useCallback((scheduledJob: ScheduledJob) => {
    setScheduledJobToDelete(scheduledJob)
    setDeleteDialogOpen(true)
  }, [])

  const handleDeleteConfirm = useCallback(() => {
    if (scheduledJobToDelete) {
      deleteMutation.mutate(scheduledJobToDelete.id)
    }
  }, [scheduledJobToDelete, deleteMutation])

  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setScheduledJobToDelete(null)
  }, [])

  if (isLoading) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label="Loading scheduled jobs..." />
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
        <h1 className={styles.title}>Scheduled Jobs</h1>
        <button
          type="button"
          className={styles.createButton}
          onClick={handleCreate}
          aria-label="Create Scheduled Job"
          data-testid="add-scheduled-job-button"
        >
          Create Scheduled Job
        </button>
      </header>

      {scheduledJobList.length === 0 ? (
        <div className={styles.emptyState} data-testid="empty-state">
          <p>No scheduled jobs found.</p>
        </div>
      ) : (
        <div className={styles.tableContainer}>
          <table
            className={styles.table}
            role="grid"
            aria-label="Scheduled Jobs"
            data-testid="scheduled-jobs-table"
          >
            <thead>
              <tr role="row">
                <th role="columnheader" scope="col">
                  Name
                </th>
                <th role="columnheader" scope="col">
                  Job Type
                </th>
                <th role="columnheader" scope="col">
                  Cron Expression
                </th>
                <th role="columnheader" scope="col">
                  Active
                </th>
                <th role="columnheader" scope="col">
                  Last Run
                </th>
                <th role="columnheader" scope="col">
                  Last Status
                </th>
                <th role="columnheader" scope="col">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody>
              {scheduledJobList.map((scheduledJob, index) => (
                <tr
                  key={scheduledJob.id}
                  role="row"
                  className={styles.tableRow}
                  data-testid={`scheduled-job-row-${index}`}
                >
                  <td role="gridcell">{scheduledJob.name}</td>
                  <td role="gridcell">
                    <span className={styles.badge}>{scheduledJob.jobType}</span>
                  </td>
                  <td role="gridcell">{scheduledJob.cronExpression}</td>
                  <td role="gridcell">
                    <span
                      className={`${styles.boolBadge} ${scheduledJob.active ? styles.boolTrue : styles.boolFalse}`}
                    >
                      {scheduledJob.active ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td role="gridcell">
                    {scheduledJob.lastRun
                      ? formatDate(new Date(scheduledJob.lastRun), {
                          year: 'numeric',
                          month: 'short',
                          day: 'numeric',
                          hour: '2-digit',
                          minute: '2-digit',
                        })
                      : '-'}
                  </td>
                  <td role="gridcell">
                    {scheduledJob.lastStatus ? (
                      <span className={styles.badge}>{scheduledJob.lastStatus}</span>
                    ) : (
                      '-'
                    )}
                  </td>
                  <td role="gridcell" className={styles.actionsCell}>
                    <div className={styles.actions}>
                      <button
                        type="button"
                        className={styles.actionButton}
                        onClick={() => {
                          setLogsItemId(scheduledJob.id)
                          setLogsItemName(scheduledJob.name)
                        }}
                        aria-label={`View logs for ${scheduledJob.name}`}
                        data-testid={`logs-button-${index}`}
                      >
                        Logs
                      </button>
                      <button
                        type="button"
                        className={styles.actionButton}
                        onClick={() => handleEdit(scheduledJob)}
                        aria-label={`Edit ${scheduledJob.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className={`${styles.actionButton} ${styles.deleteButton}`}
                        onClick={() => handleDeleteClick(scheduledJob)}
                        aria-label={`Delete ${scheduledJob.name}`}
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

      {logsItemId && (
        <ExecutionLogModal<JobExecutionLog>
          title="Scheduled Job Logs"
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
        <ScheduledJobForm
          scheduledJob={editingScheduledJob}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={isSubmitting}
        />
      )}

      <ConfirmDialog
        open={deleteDialogOpen}
        title="Delete Scheduled Job"
        message="Are you sure you want to delete this scheduled job? This action cannot be undone."
        confirmLabel="Delete"
        cancelLabel="Cancel"
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  )
}

export default ScheduledJobsPage
