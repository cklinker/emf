import React, { useState, useCallback, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import styles from './ApprovalProcessesPage.module.css'

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
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="approval-process-form-overlay"
      role="presentation"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="approval-process-form-title"
        data-testid="approval-process-form-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="approval-process-form-title" className={styles.modalTitle}>
            {title}
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onCancel}
            aria-label="Close"
            data-testid="approval-process-form-close"
          >
            &times;
          </button>
        </div>
        <div className={styles.modalBody}>
          <form className={styles.form} onSubmit={handleSubmit} noValidate>
            <div className={styles.formGroup}>
              <label htmlFor="approval-process-name" className={styles.formLabel}>
                Name
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="approval-process-name"
                type="text"
                className={`${styles.formInput} ${touched.name && errors.name ? styles.hasError : ''}`}
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
                <span className={styles.formError} role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="approval-process-description" className={styles.formLabel}>
                Description
              </label>
              <textarea
                id="approval-process-description"
                className={`${styles.formInput} ${styles.formTextarea} ${touched.description && errors.description ? styles.hasError : ''}`}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                onBlur={() => handleBlur('description')}
                placeholder="Enter approval process description"
                disabled={isSubmitting}
                rows={3}
                data-testid="approval-process-description-input"
              />
              {touched.description && errors.description && (
                <span className={styles.formError} role="alert">
                  {errors.description}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="approval-process-collection-id" className={styles.formLabel}>
                Collection ID
              </label>
              <input
                id="approval-process-collection-id"
                type="text"
                className={`${styles.formInput} ${touched.collectionId && errors.collectionId ? styles.hasError : ''}`}
                value={formData.collectionId}
                onChange={(e) => handleChange('collectionId', e.target.value)}
                onBlur={() => handleBlur('collectionId')}
                placeholder="Enter collection ID"
                disabled={isSubmitting}
                data-testid="approval-process-collection-id-input"
              />
              {touched.collectionId && errors.collectionId && (
                <span className={styles.formError} role="alert">
                  {errors.collectionId}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="approval-process-entry-criteria" className={styles.formLabel}>
                Entry Criteria
              </label>
              <textarea
                id="approval-process-entry-criteria"
                className={`${styles.formInput} ${styles.formTextarea} ${touched.entryCriteria && errors.entryCriteria ? styles.hasError : ''}`}
                value={formData.entryCriteria}
                onChange={(e) => handleChange('entryCriteria', e.target.value)}
                onBlur={() => handleBlur('entryCriteria')}
                placeholder="Enter entry criteria formula"
                disabled={isSubmitting}
                rows={3}
                data-testid="approval-process-entry-criteria-input"
              />
              {touched.entryCriteria && errors.entryCriteria && (
                <span className={styles.formError} role="alert">
                  {errors.entryCriteria}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="approval-process-record-editability" className={styles.formLabel}>
                Record Editability
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <select
                id="approval-process-record-editability"
                className={styles.formInput}
                value={formData.recordEditability}
                onChange={(e) => handleChange('recordEditability', e.target.value)}
                disabled={isSubmitting}
                data-testid="approval-process-record-editability-input"
              >
                <option value="LOCKED">Locked</option>
                <option value="ADMIN_ONLY">Admin Only</option>
              </select>
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="approval-process-execution-order" className={styles.formLabel}>
                Execution Order
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                id="approval-process-execution-order"
                type="number"
                className={`${styles.formInput} ${touched.executionOrder && errors.executionOrder ? styles.hasError : ''}`}
                value={formData.executionOrder}
                onChange={(e) => handleChange('executionOrder', parseInt(e.target.value, 10) || 0)}
                onBlur={() => handleBlur('executionOrder')}
                min={0}
                disabled={isSubmitting}
                data-testid="approval-process-execution-order-input"
              />
              {touched.executionOrder && errors.executionOrder && (
                <span className={styles.formError} role="alert">
                  {errors.executionOrder}
                </span>
              )}
            </div>

            <div className={styles.checkboxGroup}>
              <input
                id="approval-process-allow-recall"
                type="checkbox"
                checked={formData.allowRecall}
                onChange={(e) => handleChange('allowRecall', e.target.checked)}
                disabled={isSubmitting}
                data-testid="approval-process-allow-recall-input"
              />
              <label htmlFor="approval-process-allow-recall" className={styles.formLabel}>
                Allow Recall
              </label>
            </div>

            <div className={styles.checkboxGroup}>
              <input
                id="approval-process-active"
                type="checkbox"
                checked={formData.active}
                onChange={(e) => handleChange('active', e.target.checked)}
                disabled={isSubmitting}
                data-testid="approval-process-active-input"
              />
              <label htmlFor="approval-process-active" className={styles.formLabel}>
                Active
              </label>
            </div>

            <div className={styles.formActions}>
              <button
                type="button"
                className={styles.cancelButton}
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="approval-process-form-cancel"
              >
                Cancel
              </button>
              <button
                type="submit"
                className={styles.submitButton}
                disabled={isSubmitting}
                data-testid="approval-process-form-submit"
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

export function ApprovalProcessesPage({
  testId = 'approval-processes-page',
}: ApprovalProcessesPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { formatDate } = useI18n()
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

  const {
    data: approvalProcesses,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['approvalProcesses'],
    queryFn: () =>
      apiClient.get<ApprovalProcess[]>('/control/approvals/processes?tenantId=default'),
  })

  const approvalProcessList: ApprovalProcess[] = approvalProcesses ?? []

  const createMutation = useMutation({
    mutationFn: (data: ApprovalProcessFormData) =>
      apiClient.post<ApprovalProcess>(
        '/control/approvals/processes?tenantId=default&userId=system',
        data
      ),
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
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label="Loading approval processes..." />
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
        <h1 className={styles.title}>Approval Processes</h1>
        <button
          type="button"
          className={styles.createButton}
          onClick={handleCreate}
          aria-label="Create Approval Process"
          data-testid="add-approval-process-button"
        >
          Create Approval Process
        </button>
      </header>

      {approvalProcessList.length === 0 ? (
        <div className={styles.emptyState} data-testid="empty-state">
          <p>No approval processes found.</p>
        </div>
      ) : (
        <div className={styles.tableContainer}>
          <table
            className={styles.table}
            role="grid"
            aria-label="Approval Processes"
            data-testid="approval-processes-table"
          >
            <thead>
              <tr role="row">
                <th role="columnheader" scope="col">
                  Name
                </th>
                <th role="columnheader" scope="col">
                  Collection ID
                </th>
                <th role="columnheader" scope="col">
                  Record Editability
                </th>
                <th role="columnheader" scope="col">
                  Active
                </th>
                <th role="columnheader" scope="col">
                  Allow Recall
                </th>
                <th role="columnheader" scope="col">
                  Order
                </th>
                <th role="columnheader" scope="col">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody>
              {approvalProcessList.map((approvalProcess, index) => (
                <tr
                  key={approvalProcess.id}
                  role="row"
                  className={styles.tableRow}
                  data-testid={`approval-process-row-${index}`}
                >
                  <td role="gridcell">{approvalProcess.name}</td>
                  <td role="gridcell">
                    <span className={styles.descriptionCell}>
                      {approvalProcess.collectionId || '-'}
                    </span>
                  </td>
                  <td role="gridcell">
                    <span className={styles.badge}>{approvalProcess.recordEditability}</span>
                  </td>
                  <td role="gridcell">
                    <span
                      className={`${styles.boolBadge} ${approvalProcess.active ? styles.boolTrue : styles.boolFalse}`}
                    >
                      {approvalProcess.active ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td role="gridcell">
                    <span
                      className={`${styles.boolBadge} ${approvalProcess.allowRecall ? styles.boolTrue : styles.boolFalse}`}
                    >
                      {approvalProcess.allowRecall ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td role="gridcell">{approvalProcess.executionOrder}</td>
                  <td role="gridcell" className={styles.actionsCell}>
                    <div className={styles.actions}>
                      <button
                        type="button"
                        className={styles.actionButton}
                        onClick={() => handleEdit(approvalProcess)}
                        aria-label={`Edit ${approvalProcess.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className={`${styles.actionButton} ${styles.deleteButton}`}
                        onClick={() => handleDeleteClick(approvalProcess)}
                        aria-label={`Delete ${approvalProcess.name}`}
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
    </div>
  )
}

export default ApprovalProcessesPage
