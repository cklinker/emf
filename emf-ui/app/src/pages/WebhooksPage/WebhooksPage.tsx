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
import { getTenantId } from '../../hooks'
import styles from './WebhooksPage.module.css'

interface Webhook {
  id: string
  name: string
  url: string
  events: string[]
  collectionId: string | null
  filterFormula: string | null
  secret: string | null
  active: boolean
  createdBy: string
  createdAt: string
  updatedAt: string
}

interface WebhookFormData {
  name: string
  url: string
  events: string
  collectionId: string
  filterFormula: string
  secret: string
  active: boolean
}

interface FormErrors {
  name?: string
  url?: string
  events?: string
}

interface WebhookDelivery {
  id: string
  webhookId: string
  eventType: string
  payload: string
  responseStatus: number | null
  responseBody: string | null
  attemptCount: number
  status: string
  nextRetryAt: string | null
  deliveredAt: string | null
}

export interface WebhooksPageProps {
  testId?: string
}

function validateForm(data: WebhookFormData): FormErrors {
  const errors: FormErrors = {}
  if (!data.name.trim()) {
    errors.name = 'Name is required'
  } else if (data.name.length > 100) {
    errors.name = 'Name must be 100 characters or fewer'
  }
  if (!data.url.trim()) {
    errors.url = 'URL is required'
  } else {
    try {
      new URL(data.url)
    } catch {
      errors.url = 'Must be a valid URL'
    }
  }
  if (data.events.trim()) {
    try {
      const parsed = JSON.parse(data.events)
      if (!Array.isArray(parsed)) {
        errors.events = 'Events must be a JSON array'
      }
    } catch {
      errors.events = 'Events must be valid JSON (e.g. ["record.created"])'
    }
  }
  return errors
}

interface WebhookFormProps {
  webhook?: Webhook
  onSubmit: (data: WebhookFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function WebhookForm({
  webhook,
  onSubmit,
  onCancel,
  isSubmitting,
}: WebhookFormProps): React.ReactElement {
  const isEditing = !!webhook
  const [formData, setFormData] = useState<WebhookFormData>({
    name: webhook?.name ?? '',
    url: webhook?.url ?? '',
    events: webhook?.events ? JSON.stringify(webhook.events) : '',
    collectionId: webhook?.collectionId ?? '',
    filterFormula: webhook?.filterFormula ?? '',
    secret: webhook?.secret ?? '',
    active: webhook?.active ?? true,
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const nameInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    (field: keyof WebhookFormData, value: string | boolean) => {
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
      setTouched({ name: true, url: true, events: true })
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

  const title = isEditing ? 'Edit Webhook' : 'Create Webhook'

  return (
    <div
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="webhook-form-overlay"
      role="presentation"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="webhook-form-title"
        data-testid="webhook-form-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="webhook-form-title" className={styles.modalTitle}>
            {title}
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onCancel}
            aria-label="Close"
            data-testid="webhook-form-close"
          >
            &times;
          </button>
        </div>
        <div className={styles.modalBody}>
          <form className={styles.form} onSubmit={handleSubmit} noValidate>
            <div className={styles.formGroup}>
              <label htmlFor="webhook-name" className={styles.formLabel}>
                Name
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="webhook-name"
                type="text"
                className={`${styles.formInput} ${touched.name && errors.name ? styles.hasError : ''}`}
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                onBlur={() => handleBlur('name')}
                placeholder="Enter webhook name"
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                disabled={isSubmitting}
                data-testid="webhook-name-input"
              />
              {touched.name && errors.name && (
                <span className={styles.formError} role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="webhook-url" className={styles.formLabel}>
                URL
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                id="webhook-url"
                type="text"
                className={`${styles.formInput} ${touched.url && errors.url ? styles.hasError : ''}`}
                value={formData.url}
                onChange={(e) => handleChange('url', e.target.value)}
                onBlur={() => handleBlur('url')}
                placeholder="https://example.com/webhook"
                aria-required="true"
                aria-invalid={touched.url && !!errors.url}
                disabled={isSubmitting}
                data-testid="webhook-url-input"
              />
              {touched.url && errors.url && (
                <span className={styles.formError} role="alert">
                  {errors.url}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="webhook-events" className={styles.formLabel}>
                Events
              </label>
              <textarea
                id="webhook-events"
                className={`${styles.formInput} ${styles.formTextarea} ${touched.events && errors.events ? styles.hasError : ''}`}
                value={formData.events}
                onChange={(e) => handleChange('events', e.target.value)}
                onBlur={() => handleBlur('events')}
                placeholder='["record.created","record.updated"]'
                disabled={isSubmitting}
                rows={3}
                data-testid="webhook-events-input"
              />
              <span className={styles.formHint}>JSON array of event types</span>
              {touched.events && errors.events && (
                <span className={styles.formError} role="alert">
                  {errors.events}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="webhook-collection-id" className={styles.formLabel}>
                Collection ID
              </label>
              <input
                id="webhook-collection-id"
                type="text"
                className={styles.formInput}
                value={formData.collectionId}
                onChange={(e) => handleChange('collectionId', e.target.value)}
                placeholder="Optional collection ID"
                disabled={isSubmitting}
                data-testid="webhook-collection-id-input"
              />
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="webhook-filter-formula" className={styles.formLabel}>
                Filter Formula
              </label>
              <textarea
                id="webhook-filter-formula"
                className={`${styles.formInput} ${styles.formTextarea}`}
                value={formData.filterFormula}
                onChange={(e) => handleChange('filterFormula', e.target.value)}
                placeholder="Optional filter formula"
                disabled={isSubmitting}
                rows={2}
                data-testid="webhook-filter-formula-input"
              />
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="webhook-secret" className={styles.formLabel}>
                Secret
              </label>
              <input
                id="webhook-secret"
                type="text"
                className={styles.formInput}
                value={formData.secret}
                onChange={(e) => handleChange('secret', e.target.value)}
                placeholder="Optional HMAC signing secret"
                disabled={isSubmitting}
                data-testid="webhook-secret-input"
              />
              <span className={styles.formHint}>Used for HMAC signature verification</span>
            </div>

            <div className={styles.checkboxGroup}>
              <input
                id="webhook-active"
                type="checkbox"
                checked={formData.active}
                onChange={(e) => handleChange('active', e.target.checked)}
                disabled={isSubmitting}
                data-testid="webhook-active-input"
              />
              <label htmlFor="webhook-active" className={styles.formLabel}>
                Active
              </label>
            </div>

            <div className={styles.formActions}>
              <button
                type="button"
                className={styles.cancelButton}
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="webhook-form-cancel"
              >
                Cancel
              </button>
              <button
                type="submit"
                className={styles.submitButton}
                disabled={isSubmitting}
                data-testid="webhook-form-submit"
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

export function WebhooksPage({ testId = 'webhooks-page' }: WebhooksPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingWebhook, setEditingWebhook] = useState<Webhook | undefined>(undefined)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [webhookToDelete, setWebhookToDelete] = useState<Webhook | null>(null)
  const [logsItemId, setLogsItemId] = useState<string | null>(null)
  const [logsItemName, setLogsItemName] = useState('')

  const {
    data: deliveries,
    isLoading: deliveriesLoading,
    error: deliveriesError,
  } = useQuery({
    queryKey: ['webhook-deliveries', logsItemId],
    queryFn: () => apiClient.get<WebhookDelivery[]>(`/control/webhooks/${logsItemId}/deliveries`),
    enabled: !!logsItemId,
  })

  const deliveryColumns: LogColumn<WebhookDelivery>[] = [
    { key: 'status', header: 'Status' },
    { key: 'eventType', header: 'Event Type' },
    {
      key: 'responseStatus',
      header: 'Response Code',
      render: (v) => (v != null ? String(v) : '-'),
    },
    { key: 'attemptCount', header: 'Attempts' },
    {
      key: 'deliveredAt',
      header: 'Delivered At',
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
      key: 'nextRetryAt',
      header: 'Next Retry',
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
    data: webhooks,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['webhooks'],
    queryFn: () => apiClient.get<Webhook[]>(`/control/webhooks?tenantId=${getTenantId()}`),
  })

  const webhookList: Webhook[] = webhooks ?? []

  const createMutation = useMutation({
    mutationFn: (data: WebhookFormData) => {
      const payload = {
        name: data.name,
        url: data.url,
        events: data.events.trim() ? JSON.parse(data.events) : [],
        collectionId: data.collectionId || null,
        filterFormula: data.filterFormula || null,
        secret: data.secret || null,
        active: data.active,
      }
      return apiClient.post<Webhook>(
        `/control/webhooks?tenantId=${getTenantId()}&userId=system`,
        payload
      )
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['webhooks'] })
      showToast('Webhook created successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: WebhookFormData }) => {
      const payload = {
        name: data.name,
        url: data.url,
        events: data.events.trim() ? JSON.parse(data.events) : [],
        collectionId: data.collectionId || null,
        filterFormula: data.filterFormula || null,
        secret: data.secret || null,
        active: data.active,
      }
      return apiClient.put<Webhook>(`/control/webhooks/${id}`, payload)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['webhooks'] })
      showToast('Webhook updated successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/control/webhooks/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['webhooks'] })
      showToast('Webhook deleted successfully', 'success')
      setDeleteDialogOpen(false)
      setWebhookToDelete(null)
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const handleCreate = useCallback(() => {
    setEditingWebhook(undefined)
    setIsFormOpen(true)
  }, [])

  const handleEdit = useCallback((webhook: Webhook) => {
    setEditingWebhook(webhook)
    setIsFormOpen(true)
  }, [])

  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingWebhook(undefined)
  }, [])

  const handleFormSubmit = useCallback(
    (data: WebhookFormData) => {
      if (editingWebhook) {
        updateMutation.mutate({ id: editingWebhook.id, data })
      } else {
        createMutation.mutate(data)
      }
    },
    [editingWebhook, createMutation, updateMutation]
  )

  const handleDeleteClick = useCallback((webhook: Webhook) => {
    setWebhookToDelete(webhook)
    setDeleteDialogOpen(true)
  }, [])

  const handleDeleteConfirm = useCallback(() => {
    if (webhookToDelete) {
      deleteMutation.mutate(webhookToDelete.id)
    }
  }, [webhookToDelete, deleteMutation])

  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setWebhookToDelete(null)
  }, [])

  const truncateUrl = (url: string, maxLength: number = 40): string => {
    if (url.length <= maxLength) return url
    return url.substring(0, maxLength) + '...'
  }

  if (isLoading) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label="Loading webhooks..." />
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
        <h1 className={styles.title}>Webhooks</h1>
        <button
          type="button"
          className={styles.createButton}
          onClick={handleCreate}
          aria-label="Create Webhook"
          data-testid="add-webhook-button"
        >
          Create Webhook
        </button>
      </header>

      {webhookList.length === 0 ? (
        <div className={styles.emptyState} data-testid="empty-state">
          <p>No webhooks found.</p>
        </div>
      ) : (
        <div className={styles.tableContainer}>
          <table
            className={styles.table}
            role="grid"
            aria-label="Webhooks"
            data-testid="webhooks-table"
          >
            <thead>
              <tr role="row">
                <th role="columnheader" scope="col">
                  Name
                </th>
                <th role="columnheader" scope="col">
                  URL
                </th>
                <th role="columnheader" scope="col">
                  Events
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
              {webhookList.map((webhook, index) => (
                <tr
                  key={webhook.id}
                  role="row"
                  className={styles.tableRow}
                  data-testid={`webhook-row-${index}`}
                >
                  <td role="gridcell">{webhook.name}</td>
                  <td role="gridcell" className={styles.descriptionCell} title={webhook.url}>
                    {truncateUrl(webhook.url)}
                  </td>
                  <td role="gridcell">
                    <span className={styles.badge}>
                      {webhook.events?.length ?? 0} event
                      {(webhook.events?.length ?? 0) !== 1 ? 's' : ''}
                    </span>
                  </td>
                  <td role="gridcell">
                    <span
                      className={`${styles.boolBadge} ${webhook.active ? styles.boolTrue : styles.boolFalse}`}
                    >
                      {webhook.active ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td role="gridcell">
                    {formatDate(new Date(webhook.createdAt), {
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
                          setLogsItemId(webhook.id)
                          setLogsItemName(webhook.name)
                        }}
                        aria-label={`View deliveries for ${webhook.name}`}
                        data-testid={`deliveries-button-${index}`}
                      >
                        Deliveries
                      </button>
                      <button
                        type="button"
                        className={styles.actionButton}
                        onClick={() => handleEdit(webhook)}
                        aria-label={`Edit ${webhook.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className={`${styles.actionButton} ${styles.deleteButton}`}
                        onClick={() => handleDeleteClick(webhook)}
                        aria-label={`Delete ${webhook.name}`}
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
        <ExecutionLogModal<WebhookDelivery>
          title="Webhook Deliveries"
          subtitle={logsItemName}
          columns={deliveryColumns}
          data={deliveries ?? []}
          isLoading={deliveriesLoading}
          error={deliveriesError instanceof Error ? deliveriesError : null}
          onClose={() => setLogsItemId(null)}
          emptyMessage="No deliveries found."
        />
      )}

      {isFormOpen && (
        <WebhookForm
          webhook={editingWebhook}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={isSubmitting}
        />
      )}

      <ConfirmDialog
        open={deleteDialogOpen}
        title="Delete Webhook"
        message="Are you sure you want to delete this webhook? This action cannot be undone."
        confirmLabel="Delete"
        cancelLabel="Cancel"
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  )
}

export default WebhooksPage
