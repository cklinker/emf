import React, { useState, useCallback, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import { cn } from '@/lib/utils'

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

function getStatusBadgeColor(status: string): string {
  switch (status) {
    case 'QUEUED':
      return 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400'
    case 'PROCESSING':
      return 'bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300'
    case 'COMPLETED':
      return 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900 dark:text-emerald-300'
    case 'FAILED':
      return 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-300'
    case 'ABORTED':
      return 'bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-300'
    default:
      return 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400'
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
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/50 p-4"
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="bulk-job-form-overlay"
      role="presentation"
    >
      <div
        className="w-full max-w-[600px] max-h-[90vh] overflow-y-auto rounded-lg bg-card shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="bulk-job-form-title"
        data-testid="bulk-job-form-modal"
      >
        <div className="flex items-center justify-between border-b border-border p-6">
          <h2 id="bulk-job-form-title" className="m-0 text-lg font-semibold text-foreground">
            Create Bulk Job
          </h2>
          <button
            type="button"
            className="rounded p-2 text-xl leading-none text-muted-foreground hover:bg-muted hover:text-foreground"
            onClick={onCancel}
            aria-label="Close"
            data-testid="bulk-job-form-close"
          >
            &times;
          </button>
        </div>
        <div className="p-6">
          <form className="flex flex-col gap-5" onSubmit={handleSubmit} noValidate>
            <div className="flex flex-col gap-2">
              <label
                htmlFor="bulk-job-collection-id"
                className="text-sm font-medium text-foreground"
              >
                Collection ID
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={collectionInputRef}
                id="bulk-job-collection-id"
                type="text"
                className={cn(
                  'w-full rounded-md border bg-background px-3 py-2.5 text-sm text-foreground focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary',
                  touched.collectionId && errors.collectionId
                    ? 'border-destructive'
                    : 'border-border'
                )}
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
                <span className="text-xs text-destructive" role="alert">
                  {errors.collectionId}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label htmlFor="bulk-job-operation" className="text-sm font-medium text-foreground">
                Operation
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <select
                id="bulk-job-operation"
                className="w-full rounded-md border border-border bg-background px-3 py-2.5 text-sm text-foreground focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
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

            <div className="flex flex-col gap-2">
              <label htmlFor="bulk-job-external-id" className="text-sm font-medium text-foreground">
                External ID Field
              </label>
              <input
                id="bulk-job-external-id"
                type="text"
                className="w-full rounded-md border border-border bg-background px-3 py-2.5 text-sm text-foreground focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                value={formData.externalIdField}
                onChange={(e) => handleChange('externalIdField', e.target.value)}
                placeholder="Enter external ID field (optional)"
                disabled={isSubmitting}
                data-testid="bulk-job-external-id-input"
              />
              <span className="text-xs text-muted-foreground">Required for UPSERT operations</span>
            </div>

            <div className="flex flex-col gap-2">
              <label htmlFor="bulk-job-batch-size" className="text-sm font-medium text-foreground">
                Batch Size
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                id="bulk-job-batch-size"
                type="number"
                className={cn(
                  'w-full rounded-md border bg-background px-3 py-2.5 text-sm text-foreground focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary',
                  touched.batchSize && errors.batchSize ? 'border-destructive' : 'border-border'
                )}
                value={formData.batchSize}
                onChange={(e) => handleChange('batchSize', parseInt(e.target.value, 10) || 1)}
                onBlur={() => handleBlur('batchSize')}
                min={1}
                max={10000}
                disabled={isSubmitting}
                data-testid="bulk-job-batch-size-input"
              />
              {touched.batchSize && errors.batchSize && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.batchSize}
                </span>
              )}
            </div>

            <div className="mt-2 flex justify-end gap-3 border-t border-border pt-4">
              <button
                type="button"
                className="rounded-md border border-border bg-card px-5 py-2.5 text-sm font-medium text-foreground hover:bg-muted disabled:opacity-50"
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="bulk-job-form-cancel"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="rounded-md bg-primary px-5 py-2.5 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
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
    queryFn: () => apiClient.getList<BulkJob>(`/api/bulk-jobs`),
    refetchInterval: 5000,
  })

  const jobList: BulkJob[] = bulkJobs ?? []

  const createMutation = useMutation({
    mutationFn: (data: BulkJobFormData) => apiClient.postResource<BulkJob>(`/api/bulk-jobs`, data),
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
    mutationFn: (id: string) => apiClient.post<void>(`/control/bulk-jobs/${id}/actions/abort`, {}),
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
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <div className="flex min-h-[400px] items-center justify-center">
          <LoadingSpinner size="large" label="Loading bulk jobs..." />
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

  const isSubmitting = createMutation.isPending

  return (
    <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
      <header className="flex items-center justify-between">
        <h1 className="m-0 text-2xl font-semibold text-foreground">Bulk Jobs</h1>
        <button
          type="button"
          className="rounded-md bg-primary px-6 py-3 text-base font-medium text-primary-foreground hover:bg-primary/90"
          onClick={handleCreate}
          aria-label="Create Bulk Job"
          data-testid="add-bulk-job-button"
        >
          Create Bulk Job
        </button>
      </header>

      {jobList.length === 0 ? (
        <div
          className="rounded-lg border border-border bg-card p-16 text-center text-muted-foreground"
          data-testid="empty-state"
        >
          <p>No bulk jobs found.</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border bg-card">
          <table
            className="w-full border-collapse text-sm"
            role="grid"
            aria-label="Bulk Jobs"
            data-testid="bulk-jobs-table"
          >
            <thead>
              <tr role="row" className="bg-muted">
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
                  Operation
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
                  Progress
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Success
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Errors
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
                    className="border-b border-border transition-colors last:border-b-0 hover:bg-muted/50"
                    data-testid={`bulk-job-row-${index}`}
                  >
                    <td role="gridcell" className="px-4 py-3 text-foreground">
                      {job.collectionId}
                    </td>
                    <td role="gridcell" className="px-4 py-3">
                      <span className="inline-block rounded-full bg-muted px-3 py-1 text-xs font-semibold uppercase tracking-wider text-primary">
                        {job.operation}
                      </span>
                    </td>
                    <td role="gridcell" className="px-4 py-3">
                      <span
                        className={cn(
                          'inline-block rounded-full px-3 py-1 text-xs font-semibold uppercase tracking-wider',
                          getStatusBadgeColor(job.status)
                        )}
                      >
                        {job.status}
                      </span>
                    </td>
                    <td role="gridcell" className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <div className="min-w-[60px] flex-1">
                          <div className="h-1.5 overflow-hidden rounded-full bg-muted">
                            <div
                              className="h-full rounded-full bg-primary transition-all duration-300"
                              style={{ width: `${progressPercent}%` }}
                            />
                          </div>
                        </div>
                        <span className="whitespace-nowrap text-xs text-muted-foreground">
                          {job.processedRecords}/{job.totalRecords}
                        </span>
                      </div>
                    </td>
                    <td role="gridcell" className="px-4 py-3 text-foreground">
                      {job.successRecords}
                    </td>
                    <td role="gridcell" className="px-4 py-3 text-foreground">
                      {job.errorRecords}
                    </td>
                    <td role="gridcell" className="px-4 py-3 text-foreground">
                      {formatDate(new Date(job.createdAt), {
                        year: 'numeric',
                        month: 'short',
                        day: 'numeric',
                      })}
                    </td>
                    <td role="gridcell" className="px-4 py-3 text-right">
                      <div className="flex justify-end gap-2">
                        {canAbort && (
                          <button
                            type="button"
                            className="rounded-md border border-border px-4 py-2 text-sm font-medium text-amber-600 hover:border-amber-600 hover:bg-amber-50 dark:text-amber-400 dark:hover:border-amber-400 dark:hover:bg-amber-950"
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
