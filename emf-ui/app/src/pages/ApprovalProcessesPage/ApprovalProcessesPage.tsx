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

interface ApprovalProcess {
  id: string
  name: string
  description: string | null
  collectionId: string | null
  entryCriteria: string | null
  recordEditability: string
  allowRecall: boolean
  active: boolean
  executionOrder: number
  createdBy: string
  createdAt: string
  updatedAt: string
}

interface ApprovalProcessFormData {
  name: string
  description: string
  collectionId: string
  entryCriteria: string
  recordEditability: string
  allowRecall: boolean
  active: boolean
  executionOrder: number
}

interface StepInstance {
  id: string
  stepId: string
  assignedTo: string
  status: string
  comments: string | null
  actedAt: string | null
}

interface ApprovalInstance {
  id: string
  approvalProcessId: string
  approvalProcessName: string
  collectionId: string
  recordId: string
  submittedBy: string
  currentStepNumber: number
  status: string
  submittedAt: string
  completedAt: string | null
  stepInstances: StepInstance[]
}

interface FormErrors {
  name?: string
  description?: string
  collectionId?: string
  entryCriteria?: string
  executionOrder?: string
}

export interface ApprovalProcessesPageProps {
  testId?: string
}

function validateForm(data: ApprovalProcessFormData): FormErrors {
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
  if (data.entryCriteria && data.entryCriteria.length > 1000) {
    errors.entryCriteria = 'Entry criteria must be 1000 characters or fewer'
  }
  if (data.executionOrder < 0) {
    errors.executionOrder = 'Execution order must be 0 or greater'
  }
  return errors
}

interface ApprovalProcessFormProps {
  approvalProcess?: ApprovalProcess
  onSubmit: (data: ApprovalProcessFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function ApprovalProcessForm({
  approvalProcess,
  onSubmit,
  onCancel,
  isSubmitting,
}: ApprovalProcessFormProps): React.ReactElement {
  const isEditing = !!approvalProcess
  const [formData, setFormData] = useState<ApprovalProcessFormData>({
    name: approvalProcess?.name ?? '',
    description: approvalProcess?.description ?? '',
    collectionId: approvalProcess?.collectionId ?? '',
    entryCriteria: approvalProcess?.entryCriteria ?? '',
    recordEditability: approvalProcess?.recordEditability ?? 'LOCKED',
    allowRecall: approvalProcess?.allowRecall ?? false,
    active: approvalProcess?.active ?? true,
    executionOrder: approvalProcess?.executionOrder ?? 0,
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const nameInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    (field: keyof ApprovalProcessFormData, value: string | boolean | number) => {
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
      setTouched({
        name: true,
        description: true,
        collectionId: true,
        entryCriteria: true,
        executionOrder: true,
      })
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

  const title = isEditing ? 'Edit Approval Process' : 'Create Approval Process'

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="approval-process-form-overlay"
      role="presentation"
    >
      <div
        className="w-full max-w-[600px] max-h-[90vh] overflow-y-auto rounded-lg bg-background shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="approval-process-form-title"
        data-testid="approval-process-form-modal"
      >
        <div className="flex items-center justify-between border-b border-border p-6">
          <h2 id="approval-process-form-title" className="text-lg font-semibold text-foreground">
            {title}
          </h2>
          <button
            type="button"
            className="rounded p-2 text-2xl leading-none text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            onClick={onCancel}
            aria-label="Close"
            data-testid="approval-process-form-close"
          >
            &times;
          </button>
        </div>
        <div className="p-6">
          <form className="flex flex-col gap-5" onSubmit={handleSubmit} noValidate>
            <div className="flex flex-col gap-2">
              <label
                htmlFor="approval-process-name"
                className="text-sm font-medium text-foreground"
              >
                Name
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="approval-process-name"
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
                placeholder="Enter approval process name"
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                disabled={isSubmitting}
                data-testid="approval-process-name-input"
              />
              {touched.name && errors.name && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="approval-process-description"
                className="text-sm font-medium text-foreground"
              >
                Description
              </label>
              <textarea
                id="approval-process-description"
                className={cn(
                  'min-h-[80px] resize-y rounded-md border border-border bg-background px-3 py-2.5 font-[inherit] text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.description && errors.description && 'border-destructive'
                )}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                onBlur={() => handleBlur('description')}
                placeholder="Enter approval process description"
                disabled={isSubmitting}
                rows={3}
                data-testid="approval-process-description-input"
              />
              {touched.description && errors.description && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.description}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="approval-process-collection-id"
                className="text-sm font-medium text-foreground"
              >
                Collection ID
              </label>
              <input
                id="approval-process-collection-id"
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
                data-testid="approval-process-collection-id-input"
              />
              {touched.collectionId && errors.collectionId && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.collectionId}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="approval-process-entry-criteria"
                className="text-sm font-medium text-foreground"
              >
                Entry Criteria
              </label>
              <textarea
                id="approval-process-entry-criteria"
                className={cn(
                  'min-h-[80px] resize-y rounded-md border border-border bg-background px-3 py-2.5 font-[inherit] text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.entryCriteria && errors.entryCriteria && 'border-destructive'
                )}
                value={formData.entryCriteria}
                onChange={(e) => handleChange('entryCriteria', e.target.value)}
                onBlur={() => handleBlur('entryCriteria')}
                placeholder="Enter entry criteria formula"
                disabled={isSubmitting}
                rows={3}
                data-testid="approval-process-entry-criteria-input"
              />
              {touched.entryCriteria && errors.entryCriteria && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.entryCriteria}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="approval-process-record-editability"
                className="text-sm font-medium text-foreground"
              >
                Record Editability
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <select
                id="approval-process-record-editability"
                className="rounded-md border border-border bg-background px-3 py-2.5 text-sm text-foreground transition-colors focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground"
                value={formData.recordEditability}
                onChange={(e) => handleChange('recordEditability', e.target.value)}
                disabled={isSubmitting}
                data-testid="approval-process-record-editability-input"
              >
                <option value="LOCKED">Locked</option>
                <option value="ADMIN_ONLY">Admin Only</option>
              </select>
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="approval-process-execution-order"
                className="text-sm font-medium text-foreground"
              >
                Execution Order
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                id="approval-process-execution-order"
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
                data-testid="approval-process-execution-order-input"
              />
              {touched.executionOrder && errors.executionOrder && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.executionOrder}
                </span>
              )}
            </div>

            <div className="flex items-center gap-2">
              <input
                id="approval-process-allow-recall"
                type="checkbox"
                className="h-4 w-4 accent-primary"
                checked={formData.allowRecall}
                onChange={(e) => handleChange('allowRecall', e.target.checked)}
                disabled={isSubmitting}
                data-testid="approval-process-allow-recall-input"
              />
              <label
                htmlFor="approval-process-allow-recall"
                className="text-sm font-medium text-foreground"
              >
                Allow Recall
              </label>
            </div>

            <div className="flex items-center gap-2">
              <input
                id="approval-process-active"
                type="checkbox"
                className="h-4 w-4 accent-primary"
                checked={formData.active}
                onChange={(e) => handleChange('active', e.target.checked)}
                disabled={isSubmitting}
                data-testid="approval-process-active-input"
              />
              <label
                htmlFor="approval-process-active"
                className="text-sm font-medium text-foreground"
              >
                Active
              </label>
            </div>

            <div className="mt-2 flex justify-end gap-3 border-t border-border pt-4">
              <Button
                type="button"
                variant="outline"
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="approval-process-form-cancel"
              >
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={isSubmitting}
                data-testid="approval-process-form-submit"
              >
                {isSubmitting ? 'Saving...' : 'Save'}
              </Button>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}

export function ApprovalProcessesPage({
  testId = 'approval-processes-page',
}: ApprovalProcessesPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { t, formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingApprovalProcess, setEditingApprovalProcess] = useState<ApprovalProcess | undefined>(
    undefined
  )
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [approvalProcessToDelete, setApprovalProcessToDelete] = useState<ApprovalProcess | null>(
    null
  )
  const [instancesItemId, setInstancesItemId] = useState<string | null>(null)
  const [instancesItemName, setInstancesItemName] = useState('')

  const {
    data: approvalProcesses,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['approvalProcesses'],
    queryFn: () => apiClient.get<ApprovalProcess[]>(`/control/approvals/processes`),
  })

  const approvalProcessList: ApprovalProcess[] = approvalProcesses ?? []

  const createMutation = useMutation({
    mutationFn: (data: ApprovalProcessFormData) =>
      apiClient.post<ApprovalProcess>(`/control/approvals/processes?userId=system`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['approvalProcesses'] })
      showToast('Approval process created successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: ApprovalProcessFormData }) =>
      apiClient.put<ApprovalProcess>(`/control/approvals/processes/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['approvalProcesses'] })
      showToast('Approval process updated successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/control/approvals/processes/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['approvalProcesses'] })
      showToast('Approval process deleted successfully', 'success')
      setDeleteDialogOpen(false)
      setApprovalProcessToDelete(null)
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  // Instances query
  const {
    data: allInstances,
    isLoading: instancesLoading,
    error: instancesError,
  } = useQuery({
    queryKey: ['approval-instances'],
    queryFn: () => apiClient.get<ApprovalInstance[]>(`/control/approvals/instances`),
    enabled: !!instancesItemId,
  })

  const filteredInstances = (allInstances ?? []).filter(
    (i) => i.approvalProcessId === instancesItemId
  )

  // Approve mutation
  const approveMutation = useMutation({
    mutationFn: (stepInstanceId: string) =>
      apiClient.post(
        `/control/approvals/instances/steps/${stepInstanceId}/approve?${new URLSearchParams({ userId: 'system', comments: '' }).toString()}`
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['approval-instances'] })
      showToast(t('approvals.approved'), 'success')
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  // Reject mutation
  const rejectMutation = useMutation({
    mutationFn: (stepInstanceId: string) =>
      apiClient.post(
        `/control/approvals/instances/steps/${stepInstanceId}/reject?${new URLSearchParams({ userId: 'system', comments: '' }).toString()}`
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['approval-instances'] })
      showToast(t('approvals.rejected'), 'success')
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  // Recall mutation
  const recallMutation = useMutation({
    mutationFn: (instanceId: string) =>
      apiClient.post(
        `/control/approvals/instances/${instanceId}/recall?${new URLSearchParams({ userId: 'system' }).toString()}`
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['approval-instances'] })
      showToast(t('approvals.recalled'), 'success')
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  // Instance columns for ExecutionLogModal
  const instanceColumns: LogColumn<ApprovalInstance>[] = [
    {
      key: 'status',
      header: t('logs.status'),
      render: (v) => {
        const status = v as string
        const colorMap: Record<string, { color: string; bg: string }> = {
          PENDING: { color: '#92400e', bg: '#fef3c7' },
          APPROVED: { color: '#065f46', bg: '#d1fae5' },
          REJECTED: { color: '#991b1b', bg: '#fee2e2' },
          RECALLED: { color: '#6b7280', bg: '#f3f4f6' },
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
    { key: 'submittedBy', header: t('approvals.submittedBy') },
    { key: 'recordId', header: t('logs.recordId') },
    {
      key: 'currentStepNumber',
      header: t('approvals.currentStep'),
      render: (v) => (v != null ? String(v) : '-'),
    },
    {
      key: 'submittedAt',
      header: t('approvals.submittedAt'),
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
      render: (_v: unknown, row: ApprovalInstance) => {
        if (row.status === 'PENDING') {
          const pendingStep = row.stepInstances?.find((s) => s.status === 'PENDING')
          return (
            <div className="flex justify-end gap-2">
              {pendingStep && (
                <>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => approveMutation.mutate(pendingStep.id)}
                    disabled={approveMutation.isPending}
                    className="h-auto px-2 py-1 text-xs"
                  >
                    {t('approvals.approve')}
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => rejectMutation.mutate(pendingStep.id)}
                    disabled={rejectMutation.isPending}
                    className="h-auto border-destructive/30 px-2 py-1 text-xs text-destructive hover:bg-destructive/10"
                  >
                    {t('approvals.reject')}
                  </Button>
                </>
              )}
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => recallMutation.mutate(row.id)}
                disabled={recallMutation.isPending}
                className="h-auto px-2 py-1 text-xs"
              >
                {t('approvals.recall')}
              </Button>
            </div>
          )
        }
        return null
      },
    },
  ]

  const handleCreate = useCallback(() => {
    setEditingApprovalProcess(undefined)
    setIsFormOpen(true)
  }, [])

  const handleEdit = useCallback((approvalProcess: ApprovalProcess) => {
    setEditingApprovalProcess(approvalProcess)
    setIsFormOpen(true)
  }, [])

  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingApprovalProcess(undefined)
  }, [])

  const handleFormSubmit = useCallback(
    (data: ApprovalProcessFormData) => {
      if (editingApprovalProcess) {
        updateMutation.mutate({ id: editingApprovalProcess.id, data })
      } else {
        createMutation.mutate(data)
      }
    },
    [editingApprovalProcess, createMutation, updateMutation]
  )

  const handleDeleteClick = useCallback((approvalProcess: ApprovalProcess) => {
    setApprovalProcessToDelete(approvalProcess)
    setDeleteDialogOpen(true)
  }, [])

  const handleDeleteConfirm = useCallback(() => {
    if (approvalProcessToDelete) {
      deleteMutation.mutate(approvalProcessToDelete.id)
    }
  }, [approvalProcessToDelete, deleteMutation])

  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setApprovalProcessToDelete(null)
  }, [])

  if (isLoading) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <div className="flex min-h-[400px] items-center justify-center">
          <LoadingSpinner size="large" label="Loading approval processes..." />
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
        <h1 className="text-2xl font-semibold text-foreground">Approval Processes</h1>
        <Button
          type="button"
          onClick={handleCreate}
          aria-label="Create Approval Process"
          data-testid="add-approval-process-button"
        >
          Create Approval Process
        </Button>
      </header>

      {approvalProcessList.length === 0 ? (
        <div
          className="rounded-lg border border-border bg-card py-16 text-center text-muted-foreground"
          data-testid="empty-state"
        >
          <p>No approval processes found.</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border bg-card">
          <table
            className="w-full border-collapse"
            role="grid"
            aria-label="Approval Processes"
            data-testid="approval-processes-table"
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
                  Record Editability
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
                  Allow Recall
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Order
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
              {approvalProcessList.map((approvalProcess, index) => (
                <tr
                  key={approvalProcess.id}
                  role="row"
                  className="border-b border-border transition-colors last:border-b-0 hover:bg-muted/50"
                  data-testid={`approval-process-row-${index}`}
                >
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    {approvalProcess.name}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    <span className="max-w-[300px] truncate text-muted-foreground">
                      {approvalProcess.collectionId || '-'}
                    </span>
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    <span className="inline-block rounded-full bg-muted px-3 py-1 text-xs font-semibold uppercase tracking-wider text-primary">
                      {approvalProcess.recordEditability}
                    </span>
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    <span
                      className={cn(
                        'inline-block rounded-full px-3 py-1 text-xs font-semibold',
                        approvalProcess.active
                          ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                          : 'bg-muted text-muted-foreground'
                      )}
                    >
                      {approvalProcess.active ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    <span
                      className={cn(
                        'inline-block rounded-full px-3 py-1 text-xs font-semibold',
                        approvalProcess.allowRecall
                          ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                          : 'bg-muted text-muted-foreground'
                      )}
                    >
                      {approvalProcess.allowRecall ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    {approvalProcess.executionOrder}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-right text-sm">
                    <div className="flex justify-end gap-2">
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => {
                          setInstancesItemId(approvalProcess.id)
                          setInstancesItemName(approvalProcess.name)
                        }}
                        aria-label={`View instances for ${approvalProcess.name}`}
                        data-testid={`instances-button-${index}`}
                      >
                        {t('approvals.instances')}
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => handleEdit(approvalProcess)}
                        aria-label={`Edit ${approvalProcess.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        Edit
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        className="border-destructive/30 text-destructive hover:bg-destructive/10"
                        onClick={() => handleDeleteClick(approvalProcess)}
                        aria-label={`Delete ${approvalProcess.name}`}
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
        <ApprovalProcessForm
          approvalProcess={editingApprovalProcess}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={isSubmitting}
        />
      )}

      <ConfirmDialog
        open={deleteDialogOpen}
        title="Delete Approval Process"
        message="Are you sure you want to delete this approval process? This action cannot be undone."
        confirmLabel="Delete"
        cancelLabel="Cancel"
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />

      {instancesItemId && (
        <ExecutionLogModal<ApprovalInstance>
          title={t('approvals.approvalInstances')}
          subtitle={instancesItemName}
          columns={instanceColumns}
          data={filteredInstances}
          isLoading={instancesLoading}
          error={instancesError instanceof Error ? instancesError : null}
          onClose={() => setInstancesItemId(null)}
          emptyMessage={t('approvals.noInstances')}
        />
      )}
    </div>
  )
}

export default ApprovalProcessesPage
