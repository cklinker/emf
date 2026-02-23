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

interface EmailTemplate {
  id: string
  name: string
  description: string | null
  subject: string
  bodyHtml: string
  bodyText: string | null
  folder: string | null
  active: boolean
  createdBy: string
  createdAt: string
  updatedAt: string
}

interface EmailTemplateFormData {
  name: string
  description: string
  subject: string
  bodyHtml: string
  bodyText: string
  folder: string
  active: boolean
}

interface FormErrors {
  name?: string
  description?: string
  subject?: string
  bodyHtml?: string
  bodyText?: string
  folder?: string
}

interface EmailLog {
  id: string
  templateId: string
  recipientEmail: string
  subject: string
  status: string
  source: string | null
  sourceId: string | null
  errorMessage: string | null
  sentAt: string | null
}

export interface EmailTemplatesPageProps {
  testId?: string
}

function validateForm(data: EmailTemplateFormData): FormErrors {
  const errors: FormErrors = {}
  if (!data.name.trim()) {
    errors.name = 'Name is required'
  } else if (data.name.length > 100) {
    errors.name = 'Name must be 100 characters or fewer'
  }
  if (data.description && data.description.length > 500) {
    errors.description = 'Description must be 500 characters or fewer'
  }
  if (!data.subject.trim()) {
    errors.subject = 'Subject is required'
  } else if (data.subject.length > 200) {
    errors.subject = 'Subject must be 200 characters or fewer'
  }
  if (!data.bodyHtml.trim()) {
    errors.bodyHtml = 'Body HTML is required'
  }
  if (data.folder && data.folder.length > 100) {
    errors.folder = 'Folder must be 100 characters or fewer'
  }
  return errors
}

interface EmailTemplateFormProps {
  template?: EmailTemplate
  onSubmit: (data: EmailTemplateFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function EmailTemplateForm({
  template,
  onSubmit,
  onCancel,
  isSubmitting,
}: EmailTemplateFormProps): React.ReactElement {
  const isEditing = !!template
  const [formData, setFormData] = useState<EmailTemplateFormData>({
    name: template?.name ?? '',
    description: template?.description ?? '',
    subject: template?.subject ?? '',
    bodyHtml: template?.bodyHtml ?? '',
    bodyText: template?.bodyText ?? '',
    folder: template?.folder ?? '',
    active: template?.active ?? true,
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const nameInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    (field: keyof EmailTemplateFormData, value: string | boolean) => {
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
        subject: true,
        bodyHtml: true,
        bodyText: true,
        folder: true,
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

  const title = isEditing ? 'Edit Email Template' : 'Create Email Template'

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="email-template-form-overlay"
      role="presentation"
    >
      <div
        className="w-full max-w-[600px] max-h-[90vh] overflow-y-auto rounded-lg bg-background shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="email-template-form-title"
        data-testid="email-template-form-modal"
      >
        <div className="flex items-center justify-between border-b border-border p-6">
          <h2 id="email-template-form-title" className="text-lg font-semibold text-foreground">
            {title}
          </h2>
          <button
            type="button"
            className="rounded p-2 text-2xl leading-none text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            onClick={onCancel}
            aria-label="Close"
            data-testid="email-template-form-close"
          >
            &times;
          </button>
        </div>
        <div className="p-6">
          <form className="flex flex-col gap-5" onSubmit={handleSubmit} noValidate>
            <div className="flex flex-col gap-2">
              <label htmlFor="email-template-name" className="text-sm font-medium text-foreground">
                Name
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="email-template-name"
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
                placeholder="Enter template name"
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                disabled={isSubmitting}
                data-testid="email-template-name-input"
              />
              {touched.name && errors.name && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="email-template-description"
                className="text-sm font-medium text-foreground"
              >
                Description
              </label>
              <textarea
                id="email-template-description"
                className={cn(
                  'min-h-[80px] resize-y rounded-md border border-border bg-background px-3 py-2.5 font-[inherit] text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.description && errors.description && 'border-destructive'
                )}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                onBlur={() => handleBlur('description')}
                placeholder="Enter template description"
                disabled={isSubmitting}
                rows={3}
                data-testid="email-template-description-input"
              />
              {touched.description && errors.description && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.description}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="email-template-subject"
                className="text-sm font-medium text-foreground"
              >
                Subject
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                id="email-template-subject"
                type="text"
                className={cn(
                  'rounded-md border border-border bg-background px-3 py-2.5 text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.subject && errors.subject && 'border-destructive'
                )}
                value={formData.subject}
                onChange={(e) => handleChange('subject', e.target.value)}
                onBlur={() => handleBlur('subject')}
                placeholder="Enter email subject"
                aria-required="true"
                aria-invalid={touched.subject && !!errors.subject}
                disabled={isSubmitting}
                data-testid="email-template-subject-input"
              />
              {touched.subject && errors.subject && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.subject}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="email-template-body-html"
                className="text-sm font-medium text-foreground"
              >
                Body HTML
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <textarea
                id="email-template-body-html"
                className={cn(
                  'min-h-[80px] resize-y rounded-md border border-border bg-background px-3 py-2.5 font-[inherit] text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.bodyHtml && errors.bodyHtml && 'border-destructive'
                )}
                value={formData.bodyHtml}
                onChange={(e) => handleChange('bodyHtml', e.target.value)}
                onBlur={() => handleBlur('bodyHtml')}
                placeholder="Enter HTML email body"
                aria-required="true"
                aria-invalid={touched.bodyHtml && !!errors.bodyHtml}
                disabled={isSubmitting}
                rows={6}
                data-testid="email-template-body-html-input"
              />
              {touched.bodyHtml && errors.bodyHtml && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.bodyHtml}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="email-template-body-text"
                className="text-sm font-medium text-foreground"
              >
                Body Text
              </label>
              <textarea
                id="email-template-body-text"
                className={cn(
                  'min-h-[80px] resize-y rounded-md border border-border bg-background px-3 py-2.5 font-[inherit] text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.bodyText && errors.bodyText && 'border-destructive'
                )}
                value={formData.bodyText}
                onChange={(e) => handleChange('bodyText', e.target.value)}
                onBlur={() => handleBlur('bodyText')}
                placeholder="Enter plain text fallback (optional)"
                disabled={isSubmitting}
                rows={3}
                data-testid="email-template-body-text-input"
              />
              {touched.bodyText && errors.bodyText && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.bodyText}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="email-template-folder"
                className="text-sm font-medium text-foreground"
              >
                Folder
              </label>
              <input
                id="email-template-folder"
                type="text"
                className={cn(
                  'rounded-md border border-border bg-background px-3 py-2.5 text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.folder && errors.folder && 'border-destructive'
                )}
                value={formData.folder}
                onChange={(e) => handleChange('folder', e.target.value)}
                onBlur={() => handleBlur('folder')}
                placeholder="Enter folder (optional)"
                disabled={isSubmitting}
                data-testid="email-template-folder-input"
              />
              {touched.folder && errors.folder && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.folder}
                </span>
              )}
            </div>

            <div className="flex items-center gap-2">
              <input
                id="email-template-active"
                type="checkbox"
                className="h-4 w-4 accent-primary"
                checked={formData.active}
                onChange={(e) => handleChange('active', e.target.checked)}
                disabled={isSubmitting}
                data-testid="email-template-active-input"
              />
              <label
                htmlFor="email-template-active"
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
                data-testid="email-template-form-cancel"
              >
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={isSubmitting}
                data-testid="email-template-form-submit"
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

export function EmailTemplatesPage({
  testId = 'email-templates-page',
}: EmailTemplatesPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingTemplate, setEditingTemplate] = useState<EmailTemplate | undefined>(undefined)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [templateToDelete, setTemplateToDelete] = useState<EmailTemplate | null>(null)
  const [logsItemId, setLogsItemId] = useState<string | null>(null)
  const [logsItemName, setLogsItemName] = useState('')

  const {
    data: allEmailLogs,
    isLoading: logsLoading,
    error: logsError,
  } = useQuery({
    queryKey: ['email-template-logs', logsItemId],
    queryFn: () => apiClient.getList<EmailLog>(`/api/email-logs`),
    enabled: !!logsItemId,
  })

  const filteredLogs = (allEmailLogs ?? []).filter((log) => log.templateId === logsItemId)

  const logColumns: LogColumn<EmailLog>[] = [
    { key: 'status', header: 'Status' },
    { key: 'recipientEmail', header: 'Recipient' },
    { key: 'subject', header: 'Subject' },
    { key: 'source', header: 'Source' },
    { key: 'errorMessage', header: 'Error' },
    {
      key: 'sentAt',
      header: 'Sent At',
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
    data: templates,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['email-templates'],
    queryFn: () => apiClient.getList<EmailTemplate>(`/api/email-templates`),
  })

  const templateList: EmailTemplate[] = templates ?? []

  const createMutation = useMutation({
    mutationFn: (data: EmailTemplateFormData) =>
      apiClient.postResource<EmailTemplate>(`/api/email-templates`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['email-templates'] })
      showToast('Email template created successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: EmailTemplateFormData }) =>
      apiClient.putResource<EmailTemplate>(`/api/email-templates/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['email-templates'] })
      showToast('Email template updated successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/email-templates/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['email-templates'] })
      showToast('Email template deleted successfully', 'success')
      setDeleteDialogOpen(false)
      setTemplateToDelete(null)
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const handleCreate = useCallback(() => {
    setEditingTemplate(undefined)
    setIsFormOpen(true)
  }, [])

  const handleEdit = useCallback((template: EmailTemplate) => {
    setEditingTemplate(template)
    setIsFormOpen(true)
  }, [])

  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingTemplate(undefined)
  }, [])

  const handleFormSubmit = useCallback(
    (data: EmailTemplateFormData) => {
      if (editingTemplate) {
        updateMutation.mutate({ id: editingTemplate.id, data })
      } else {
        createMutation.mutate(data)
      }
    },
    [editingTemplate, createMutation, updateMutation]
  )

  const handleDeleteClick = useCallback((template: EmailTemplate) => {
    setTemplateToDelete(template)
    setDeleteDialogOpen(true)
  }, [])

  const handleDeleteConfirm = useCallback(() => {
    if (templateToDelete) {
      deleteMutation.mutate(templateToDelete.id)
    }
  }, [templateToDelete, deleteMutation])

  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setTemplateToDelete(null)
  }, [])

  if (isLoading) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <div className="flex min-h-[400px] items-center justify-center">
          <LoadingSpinner size="large" label="Loading email templates..." />
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
        <h1 className="text-2xl font-semibold text-foreground">Email Templates</h1>
        <Button
          type="button"
          onClick={handleCreate}
          aria-label="Create Email Template"
          data-testid="add-email-template-button"
        >
          Create Email Template
        </Button>
      </header>

      {templateList.length === 0 ? (
        <div
          className="rounded-lg border border-border bg-card py-16 text-center text-muted-foreground"
          data-testid="empty-state"
        >
          <p>No email templates found.</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border bg-card">
          <table
            className="w-full border-collapse"
            role="grid"
            aria-label="Email Templates"
            data-testid="email-templates-table"
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
                  Subject
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Folder
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
              {templateList.map((template, index) => (
                <tr
                  key={template.id}
                  role="row"
                  className="border-b border-border transition-colors last:border-b-0 hover:bg-muted/50"
                  data-testid={`email-template-row-${index}`}
                >
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    {template.name}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    {template.subject}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    {template.folder || '-'}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    <span
                      className={cn(
                        'inline-block rounded-full px-3 py-1 text-xs font-semibold',
                        template.active
                          ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                          : 'bg-muted text-muted-foreground'
                      )}
                    >
                      {template.active ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    {formatDate(new Date(template.createdAt), {
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
                          setLogsItemId(template.id)
                          setLogsItemName(template.name)
                        }}
                        aria-label={`View logs for ${template.name}`}
                        data-testid={`logs-button-${index}`}
                      >
                        Logs
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => handleEdit(template)}
                        aria-label={`Edit ${template.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        Edit
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        className="border-destructive/30 text-destructive hover:bg-destructive/10"
                        onClick={() => handleDeleteClick(template)}
                        aria-label={`Delete ${template.name}`}
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
        <ExecutionLogModal<EmailLog>
          title="Email Logs"
          subtitle={logsItemName}
          columns={logColumns}
          data={filteredLogs}
          isLoading={logsLoading}
          error={logsError instanceof Error ? logsError : null}
          onClose={() => setLogsItemId(null)}
          emptyMessage="No email logs found."
        />
      )}

      {isFormOpen && (
        <EmailTemplateForm
          template={editingTemplate}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={isSubmitting}
        />
      )}

      <ConfirmDialog
        open={deleteDialogOpen}
        title="Delete Email Template"
        message="Are you sure you want to delete this email template? This action cannot be undone."
        confirmLabel="Delete"
        cancelLabel="Cancel"
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  )
}

export default EmailTemplatesPage
