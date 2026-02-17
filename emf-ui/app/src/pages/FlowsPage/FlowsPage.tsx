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

import styles from './FlowsPage.module.css'

interface Flow {
  id: string
  name: string
  description: string | null
  flowType: string
  active: boolean
  definition: string | null
  createdBy: string
  createdAt: string
  updatedAt: string
}

interface FlowExecutionLog {
  id: string
  flowId: string
  flowName: string
  status: string
  startedBy: string | null
  triggerRecordId: string | null
  currentNodeId: string | null
  errorMessage: string | null
  startedAt: string
  completedAt: string | null
}

interface FlowFormData {
  name: string
  description: string
  flowType: string
  active: boolean
  definition: string
}

interface FormErrors {
  name?: string
  description?: string
  definition?: string
}

export interface FlowsPageProps {
  testId?: string
}

function validateForm(data: FlowFormData): FormErrors {
  const errors: FormErrors = {}
  if (!data.name.trim()) {
    errors.name = 'Name is required'
  } else if (data.name.length > 100) {
    errors.name = 'Name must be 100 characters or fewer'
  }
  if (data.description && data.description.length > 500) {
    errors.description = 'Description must be 500 characters or fewer'
  }
  if (data.definition) {
    try {
      JSON.parse(data.definition)
    } catch {
      errors.definition = 'Definition must be valid JSON'
    }
  }
  return errors
}

interface FlowFormProps {
  flow?: Flow
  onSubmit: (data: FlowFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function FlowForm({ flow, onSubmit, onCancel, isSubmitting }: FlowFormProps): React.ReactElement {
  const isEditing = !!flow
  const [formData, setFormData] = useState<FlowFormData>({
    name: flow?.name ?? '',
    description: flow?.description ?? '',
    flowType: flow?.flowType ?? 'AUTOLAUNCHED',
    active: flow?.active ?? false,
    definition: flow?.definition ?? '',
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const nameInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    (field: keyof FlowFormData, value: string | boolean) => {
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
      setTouched({ name: true, description: true, definition: true })
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

  const title = isEditing ? 'Edit Flow' : 'Create Flow'

  return (
    <div
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="flow-form-overlay"
      role="presentation"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="flow-form-title"
        data-testid="flow-form-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="flow-form-title" className={styles.modalTitle}>
            {title}
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onCancel}
            aria-label="Close"
            data-testid="flow-form-close"
          >
            &times;
          </button>
        </div>
        <div className={styles.modalBody}>
          <form className={styles.form} onSubmit={handleSubmit} noValidate>
            <div className={styles.formGroup}>
              <label htmlFor="flow-name" className={styles.formLabel}>
                Name
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="flow-name"
                type="text"
                className={`${styles.formInput} ${touched.name && errors.name ? styles.hasError : ''}`}
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                onBlur={() => handleBlur('name')}
                placeholder="Enter flow name"
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                disabled={isSubmitting}
                data-testid="flow-name-input"
              />
              {touched.name && errors.name && (
                <span className={styles.formError} role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="flow-description" className={styles.formLabel}>
                Description
              </label>
              <textarea
                id="flow-description"
                className={`${styles.formInput} ${styles.formTextarea} ${touched.description && errors.description ? styles.hasError : ''}`}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                onBlur={() => handleBlur('description')}
                placeholder="Enter flow description"
                disabled={isSubmitting}
                rows={3}
                data-testid="flow-description-input"
              />
              {touched.description && errors.description && (
                <span className={styles.formError} role="alert">
                  {errors.description}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="flow-type" className={styles.formLabel}>
                Flow Type
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <select
                id="flow-type"
                className={styles.formInput}
                value={formData.flowType}
                onChange={(e) => handleChange('flowType', e.target.value)}
                disabled={isSubmitting}
                data-testid="flow-type-input"
              >
                <option value="RECORD_TRIGGERED">Record Triggered</option>
                <option value="SCHEDULED">Scheduled</option>
                <option value="AUTOLAUNCHED">Autolaunched</option>
                <option value="SCREEN">Screen</option>
              </select>
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="flow-definition" className={styles.formLabel}>
                Definition
              </label>
              <textarea
                id="flow-definition"
                className={`${styles.formInput} ${styles.formTextarea} ${touched.definition && errors.definition ? styles.hasError : ''}`}
                value={formData.definition}
                onChange={(e) => handleChange('definition', e.target.value)}
                onBlur={() => handleBlur('definition')}
                placeholder="Enter flow definition (JSON)"
                disabled={isSubmitting}
                rows={6}
                data-testid="flow-definition-input"
              />
              {touched.definition && errors.definition && (
                <span className={styles.formError} role="alert">
                  {errors.definition}
                </span>
              )}
            </div>

            <div className={styles.checkboxGroup}>
              <input
                id="flow-active"
                type="checkbox"
                checked={formData.active}
                onChange={(e) => handleChange('active', e.target.checked)}
                disabled={isSubmitting}
                data-testid="flow-active-input"
              />
              <label htmlFor="flow-active" className={styles.formLabel}>
                Active
              </label>
            </div>

            <div className={styles.formActions}>
              <button
                type="button"
                className={styles.cancelButton}
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="flow-form-cancel"
              >
                Cancel
              </button>
              <button
                type="submit"
                className={styles.submitButton}
                disabled={isSubmitting}
                data-testid="flow-form-submit"
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

export function FlowsPage({ testId = 'flows-page' }: FlowsPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { t, formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingFlow, setEditingFlow] = useState<Flow | undefined>(undefined)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [flowToDelete, setFlowToDelete] = useState<Flow | null>(null)
  const [execItemId, setExecItemId] = useState<string | null>(null)
  const [execItemName, setExecItemName] = useState('')

  const {
    data: flows,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['flows'],
    queryFn: () => apiClient.get<Flow[]>(`/control/flows`),
  })

  const flowList: Flow[] = flows ?? []

  const createMutation = useMutation({
    mutationFn: (data: FlowFormData) =>
      apiClient.post<Flow>(`/control/flows?userId=system`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['flows'] })
      showToast('Flow created successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: FlowFormData }) =>
      apiClient.put<Flow>(`/control/flows/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['flows'] })
      showToast('Flow updated successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/control/flows/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['flows'] })
      showToast('Flow deleted successfully', 'success')
      setDeleteDialogOpen(false)
      setFlowToDelete(null)
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const {
    data: executions,
    isLoading: execLoading,
    error: execError,
  } = useQuery({
    queryKey: ['flow-executions', execItemId],
    queryFn: () =>
      apiClient.get<FlowExecutionLog[]>(
        `/control/flows/${execItemId}/executions`
      ),
    enabled: !!execItemId,
  })

  const executeMutation = useMutation({
    mutationFn: async (flowId: string) => {
      const resp = await apiClient.fetch(
        `/control/flows/${flowId}/execute?${new URLSearchParams({ userId: 'system' }).toString()}`,
        { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' }
      )
      if (!resp.ok) {
        const errBody = await resp.json().catch(() => ({}))
        throw new Error(
          (errBody as Record<string, string>).message || `Execute failed: ${resp.statusText}`
        )
      }
    },
    onSuccess: () => {
      showToast(t('flows.executionStarted'), 'success')
      if (execItemId) {
        queryClient.invalidateQueries({ queryKey: ['flow-executions', execItemId] })
      }
    },
    onError: (err: Error) => showToast(err.message, 'error'),
  })

  const cancelMutation = useMutation({
    mutationFn: async (executionId: string) => {
      const resp = await apiClient.fetch(
        `/control/flows/executions/${executionId}/cancel`,
        { method: 'POST' }
      )
      if (!resp.ok) throw new Error(`Cancel failed: ${resp.statusText}`)
    },
    onSuccess: () => {
      showToast(t('flows.executionCancelled'), 'success')
      queryClient.invalidateQueries({ queryKey: ['flow-executions', execItemId] })
    },
    onError: (err: Error) => showToast(err.message, 'error'),
  })

  const execColumns: LogColumn<FlowExecutionLog>[] = [
    {
      key: 'status',
      header: t('logs.status'),
      render: (v) => {
        const status = v as string
        const colorMap: Record<string, { color: string; bg: string }> = {
          SUCCESS: { color: '#065f46', bg: '#d1fae5' },
          FAILED: { color: '#991b1b', bg: '#fee2e2' },
          RUNNING: { color: '#1e40af', bg: '#dbeafe' },
          CANCELLED: { color: '#6b7280', bg: '#f3f4f6' },
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
    { key: 'startedBy', header: t('flows.startedBy') },
    { key: 'triggerRecordId', header: t('flows.triggerRecord') },
    { key: 'currentNodeId', header: t('flows.currentNode') },
    { key: 'errorMessage', header: t('logs.errorMessage') },
    {
      key: 'startedAt',
      header: t('logs.startedAt'),
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
      render: (_v: unknown, row: FlowExecutionLog) =>
        row.status === 'RUNNING' ? (
          <button
            type="button"
            className={`${styles.actionButton} ${styles.deleteButton}`}
            onClick={() => cancelMutation.mutate(row.id)}
            disabled={cancelMutation.isPending}
            style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem' }}
          >
            {t('flows.cancelExecution')}
          </button>
        ) : null,
    },
  ]

  const handleCreate = useCallback(() => {
    setEditingFlow(undefined)
    setIsFormOpen(true)
  }, [])

  const handleEdit = useCallback((flow: Flow) => {
    setEditingFlow(flow)
    setIsFormOpen(true)
  }, [])

  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingFlow(undefined)
  }, [])

  const handleFormSubmit = useCallback(
    (data: FlowFormData) => {
      if (editingFlow) {
        updateMutation.mutate({ id: editingFlow.id, data })
      } else {
        createMutation.mutate(data)
      }
    },
    [editingFlow, createMutation, updateMutation]
  )

  const handleDeleteClick = useCallback((flow: Flow) => {
    setFlowToDelete(flow)
    setDeleteDialogOpen(true)
  }, [])

  const handleDeleteConfirm = useCallback(() => {
    if (flowToDelete) {
      deleteMutation.mutate(flowToDelete.id)
    }
  }, [flowToDelete, deleteMutation])

  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setFlowToDelete(null)
  }, [])

  if (isLoading) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label="Loading flows..." />
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
        <h1 className={styles.title}>Flows</h1>
        <button
          type="button"
          className={styles.createButton}
          onClick={handleCreate}
          aria-label="Create Flow"
          data-testid="add-flow-button"
        >
          Create Flow
        </button>
      </header>

      {flowList.length === 0 ? (
        <div className={styles.emptyState} data-testid="empty-state">
          <p>No flows found.</p>
        </div>
      ) : (
        <div className={styles.tableContainer}>
          <table className={styles.table} role="grid" aria-label="Flows" data-testid="flows-table">
            <thead>
              <tr role="row">
                <th role="columnheader" scope="col">
                  Name
                </th>
                <th role="columnheader" scope="col">
                  Flow Type
                </th>
                <th role="columnheader" scope="col">
                  Active
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
              {flowList.map((flow, index) => (
                <tr
                  key={flow.id}
                  role="row"
                  className={styles.tableRow}
                  data-testid={`flow-row-${index}`}
                >
                  <td role="gridcell">{flow.name}</td>
                  <td role="gridcell">
                    <span className={styles.badge}>{flow.flowType}</span>
                  </td>
                  <td role="gridcell">
                    <span
                      className={`${styles.boolBadge} ${flow.active ? styles.boolTrue : styles.boolFalse}`}
                    >
                      {flow.active ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td role="gridcell">
                    {formatDate(new Date(flow.createdAt), {
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
                        onClick={() => {
                          setExecItemId(flow.id)
                          setExecItemName(flow.name)
                        }}
                        aria-label={`View executions for ${flow.name}`}
                        data-testid={`executions-button-${index}`}
                      >
                        {t('flows.executions')}
                      </button>
                      <button
                        type="button"
                        className={styles.actionButton}
                        onClick={() => executeMutation.mutate(flow.id)}
                        disabled={!flow.active || executeMutation.isPending}
                        aria-label={`Run ${flow.name}`}
                        data-testid={`run-button-${index}`}
                      >
                        {t('flows.runFlow')}
                      </button>
                      <button
                        type="button"
                        className={styles.actionButton}
                        onClick={() => handleEdit(flow)}
                        aria-label={`Edit ${flow.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className={`${styles.actionButton} ${styles.deleteButton}`}
                        onClick={() => handleDeleteClick(flow)}
                        aria-label={`Delete ${flow.name}`}
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
        <FlowForm
          flow={editingFlow}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={isSubmitting}
        />
      )}

      <ConfirmDialog
        open={deleteDialogOpen}
        title="Delete Flow"
        message="Are you sure you want to delete this flow? This action cannot be undone."
        confirmLabel="Delete"
        cancelLabel="Cancel"
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />

      {execItemId && (
        <ExecutionLogModal<FlowExecutionLog>
          title={t('flows.flowExecutionHistory')}
          subtitle={execItemName}
          columns={execColumns}
          data={executions ?? []}
          isLoading={execLoading}
          error={execError instanceof Error ? execError : null}
          onClose={() => setExecItemId(null)}
          emptyMessage={t('flows.noExecutions')}
        />
      )}
    </div>
  )
}

export default FlowsPage
