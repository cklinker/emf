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
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="webhook-form-overlay"
      role="presentation"
    >
      <div
        className="w-full max-w-[600px] max-h-[90vh] overflow-y-auto rounded-lg bg-background shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="webhook-form-title"
        data-testid="webhook-form-modal"
      >
        <div className="flex items-center justify-between border-b border-border p-6">
          <h2 id="webhook-form-title" className="text-lg font-semibold text-foreground">
            {title}
          </h2>
          <button
            type="button"
            className="rounded p-2 text-2xl leading-none text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            onClick={onCancel}
            aria-label="Close"
            data-testid="webhook-form-close"
          >
            &times;
          </button>
        </div>
        <div className="p-6">
          <form className="flex flex-col gap-5" onSubmit={handleSubmit} noValidate>
            <div className="flex flex-col gap-2">
              <label htmlFor="webhook-name" className="text-sm font-medium text-foreground">
                Name
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="webhook-name"
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
                placeholder="Enter webhook name"
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                disabled={isSubmitting}
                data-testid="webhook-name-input"
              />
              {touched.name && errors.name && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label htmlFor="webhook-url" className="text-sm font-medium text-foreground">
                URL
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                id="webhook-url"
                type="text"
                className={cn(
                  'rounded-md border border-border bg-background px-3 py-2.5 text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.url && errors.url && 'border-destructive'
                )}
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
                <span className="text-xs text-destructive" role="alert">
                  {errors.url}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label htmlFor="webhook-events" className="text-sm font-medium text-foreground">
                Events
              </label>
              <textarea
                id="webhook-events"
                className={cn(
                  'min-h-[80px] resize-y rounded-md border border-border bg-background px-3 py-2.5 font-[inherit] text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.events && errors.events && 'border-destructive'
                )}
                value={formData.events}
                onChange={(e) => handleChange('events', e.target.value)}
                onBlur={() => handleBlur('events')}
                placeholder='["record.created","record.updated"]'
                disabled={isSubmitting}
                rows={3}
                data-testid="webhook-events-input"
              />
              <span className="text-xs text-muted-foreground">JSON array of event types</span>
              {touched.events && errors.events && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.events}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="webhook-collection-id"
                className="text-sm font-medium text-foreground"
              >
                Collection ID
              </label>
              <input
                id="webhook-collection-id"
                type="text"
                className={cn(
                  'rounded-md border border-border bg-background px-3 py-2.5 text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground'
                )}
                value={formData.collectionId}
                onChange={(e) => handleChange('collectionId', e.target.value)}
                placeholder="Optional collection ID"
                disabled={isSubmitting}
                data-testid="webhook-collection-id-input"
              />
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="webhook-filter-formula"
                className="text-sm font-medium text-foreground"
              >
                Filter Formula
              </label>
              <textarea
                id="webhook-filter-formula"
                className={cn(
                  'min-h-[80px] resize-y rounded-md border border-border bg-background px-3 py-2.5 font-[inherit] text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground'
                )}
                value={formData.filterFormula}
                onChange={(e) => handleChange('filterFormula', e.target.value)}
                placeholder="Optional filter formula"
                disabled={isSubmitting}
                rows={2}
                data-testid="webhook-filter-formula-input"
              />
            </div>

            <div className="flex flex-col gap-2">
              <label htmlFor="webhook-secret" className="text-sm font-medium text-foreground">
                Secret
              </label>
              <input
                id="webhook-secret"
                type="text"
                className={cn(
                  'rounded-md border border-border bg-background px-3 py-2.5 text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground'
                )}
                value={formData.secret}
                onChange={(e) => handleChange('secret', e.target.value)}
                placeholder="Optional HMAC signing secret"
                disabled={isSubmitting}
                data-testid="webhook-secret-input"
              />
              <span className="text-xs text-muted-foreground">
                Used for HMAC signature verification
              </span>
            </div>

            <div className="flex items-center gap-2">
              <input
                id="webhook-active"
                type="checkbox"
                className="h-4 w-4 accent-primary"
                checked={formData.active}
                onChange={(e) => handleChange('active', e.target.checked)}
                disabled={isSubmitting}
                data-testid="webhook-active-input"
              />
              <label htmlFor="webhook-active" className="text-sm font-medium text-foreground">
                Active
              </label>
            </div>

            <div className="mt-2 flex justify-end gap-3 border-t border-border pt-4">
              <Button
                type="button"
                variant="outline"
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="webhook-form-cancel"
              >
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting} data-testid="webhook-form-submit">
                {isSubmitting ? 'Saving...' : 'Save'}
              </Button>
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
    queryFn: () => apiClient.get<Webhook[]>(`/control/webhooks`),
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
      return apiClient.post<Webhook>(`/control/webhooks?userId=system`, payload)
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
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <div className="flex min-h-[400px] items-center justify-center">
          <LoadingSpinner size="large" label="Loading webhooks..." />
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
        <h1 className="text-2xl font-semibold text-foreground">Webhooks</h1>
        <Button
          type="button"
          onClick={handleCreate}
          aria-label="Create Webhook"
          data-testid="add-webhook-button"
        >
          Create Webhook
        </Button>
      </header>

      {webhookList.length === 0 ? (
        <div
          className="rounded-lg border border-border bg-card py-16 text-center text-muted-foreground"
          data-testid="empty-state"
        >
          <p>No webhooks found.</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border bg-card">
          <table
            className="w-full border-collapse"
            role="grid"
            aria-label="Webhooks"
            data-testid="webhooks-table"
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
                  URL
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Events
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
              {webhookList.map((webhook, index) => (
                <tr
                  key={webhook.id}
                  role="row"
                  className="border-b border-border transition-colors last:border-b-0 hover:bg-muted/50"
                  data-testid={`webhook-row-${index}`}
                >
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    {webhook.name}
                  </td>
                  <td
                    role="gridcell"
                    className="max-w-[300px] truncate px-4 py-3 text-sm text-foreground"
                    title={webhook.url}
                  >
                    {truncateUrl(webhook.url)}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    <span className="inline-block rounded-full bg-muted px-3 py-1 text-xs font-semibold uppercase tracking-wider text-primary">
                      {webhook.events?.length ?? 0} event
                      {(webhook.events?.length ?? 0) !== 1 ? 's' : ''}
                    </span>
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    <span
                      className={cn(
                        'inline-block rounded-full px-3 py-1 text-xs font-semibold',
                        webhook.active
                          ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                          : 'bg-muted text-muted-foreground'
                      )}
                    >
                      {webhook.active ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    {formatDate(new Date(webhook.createdAt), {
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric',
                    })}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-right text-sm">
                    <div className="flex justify-end gap-2">
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => {
                          setLogsItemId(webhook.id)
                          setLogsItemName(webhook.name)
                        }}
                        aria-label={`View deliveries for ${webhook.name}`}
                        data-testid={`deliveries-button-${index}`}
                      >
                        Deliveries
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => handleEdit(webhook)}
                        aria-label={`Edit ${webhook.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        Edit
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        className="border-destructive/30 text-destructive hover:bg-destructive/10"
                        onClick={() => handleDeleteClick(webhook)}
                        aria-label={`Delete ${webhook.name}`}
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
