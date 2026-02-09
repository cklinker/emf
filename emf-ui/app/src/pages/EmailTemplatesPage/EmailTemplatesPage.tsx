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
import styles from './EmailTemplatesPage.module.css'

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
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="email-template-form-overlay"
      role="presentation"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="email-template-form-title"
        data-testid="email-template-form-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="email-template-form-title" className={styles.modalTitle}>
            {title}
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onCancel}
            aria-label="Close"
            data-testid="email-template-form-close"
          >
            &times;
          </button>
        </div>
        <div className={styles.modalBody}>
          <form className={styles.form} onSubmit={handleSubmit} noValidate>
            <div className={styles.formGroup}>
              <label htmlFor="email-template-name" className={styles.formLabel}>
                Name
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="email-template-name"
                type="text"
                className={`${styles.formInput} ${touched.name && errors.name ? styles.hasError : ''}`}
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
                <span className={styles.formError} role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="email-template-description" className={styles.formLabel}>
                Description
              </label>
              <textarea
                id="email-template-description"
                className={`${styles.formInput} ${styles.formTextarea} ${touched.description && errors.description ? styles.hasError : ''}`}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                onBlur={() => handleBlur('description')}
                placeholder="Enter template description"
                disabled={isSubmitting}
                rows={3}
                data-testid="email-template-description-input"
              />
              {touched.description && errors.description && (
                <span className={styles.formError} role="alert">
                  {errors.description}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="email-template-subject" className={styles.formLabel}>
                Subject
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                id="email-template-subject"
                type="text"
                className={`${styles.formInput} ${touched.subject && errors.subject ? styles.hasError : ''}`}
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
                <span className={styles.formError} role="alert">
                  {errors.subject}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="email-template-body-html" className={styles.formLabel}>
                Body HTML
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <textarea
                id="email-template-body-html"
                className={`${styles.formInput} ${styles.formTextarea} ${touched.bodyHtml && errors.bodyHtml ? styles.hasError : ''}`}
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
                <span className={styles.formError} role="alert">
                  {errors.bodyHtml}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="email-template-body-text" className={styles.formLabel}>
                Body Text
              </label>
              <textarea
                id="email-template-body-text"
                className={`${styles.formInput} ${styles.formTextarea} ${touched.bodyText && errors.bodyText ? styles.hasError : ''}`}
                value={formData.bodyText}
                onChange={(e) => handleChange('bodyText', e.target.value)}
                onBlur={() => handleBlur('bodyText')}
                placeholder="Enter plain text fallback (optional)"
                disabled={isSubmitting}
                rows={3}
                data-testid="email-template-body-text-input"
              />
              {touched.bodyText && errors.bodyText && (
                <span className={styles.formError} role="alert">
                  {errors.bodyText}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="email-template-folder" className={styles.formLabel}>
                Folder
              </label>
              <input
                id="email-template-folder"
                type="text"
                className={`${styles.formInput} ${touched.folder && errors.folder ? styles.hasError : ''}`}
                value={formData.folder}
                onChange={(e) => handleChange('folder', e.target.value)}
                onBlur={() => handleBlur('folder')}
                placeholder="Enter folder (optional)"
                disabled={isSubmitting}
                data-testid="email-template-folder-input"
              />
              {touched.folder && errors.folder && (
                <span className={styles.formError} role="alert">
                  {errors.folder}
                </span>
              )}
            </div>

            <div className={styles.checkboxGroup}>
              <input
                id="email-template-active"
                type="checkbox"
                checked={formData.active}
                onChange={(e) => handleChange('active', e.target.checked)}
                disabled={isSubmitting}
                data-testid="email-template-active-input"
              />
              <label htmlFor="email-template-active" className={styles.formLabel}>
                Active
              </label>
            </div>

            <div className={styles.formActions}>
              <button
                type="button"
                className={styles.cancelButton}
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="email-template-form-cancel"
              >
                Cancel
              </button>
              <button
                type="submit"
                className={styles.submitButton}
                disabled={isSubmitting}
                data-testid="email-template-form-submit"
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
    queryFn: () => apiClient.get<EmailLog[]>('/control/email-templates/logs?tenantId=default'),
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
    queryFn: () => apiClient.get<EmailTemplate[]>('/control/email-templates?tenantId=default'),
  })

  const templateList: EmailTemplate[] = templates ?? []

  const createMutation = useMutation({
    mutationFn: (data: EmailTemplateFormData) =>
      apiClient.post<EmailTemplate>(
        '/control/email-templates?tenantId=default&userId=system',
        data
      ),
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
      apiClient.put<EmailTemplate>(`/control/email-templates/${id}`, data),
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
    mutationFn: (id: string) => apiClient.delete(`/control/email-templates/${id}`),
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
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label="Loading email templates..." />
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
        <h1 className={styles.title}>Email Templates</h1>
        <button
          type="button"
          className={styles.createButton}
          onClick={handleCreate}
          aria-label="Create Email Template"
          data-testid="add-email-template-button"
        >
          Create Email Template
        </button>
      </header>

      {templateList.length === 0 ? (
        <div className={styles.emptyState} data-testid="empty-state">
          <p>No email templates found.</p>
        </div>
      ) : (
        <div className={styles.tableContainer}>
          <table
            className={styles.table}
            role="grid"
            aria-label="Email Templates"
            data-testid="email-templates-table"
          >
            <thead>
              <tr role="row">
                <th role="columnheader" scope="col">
                  Name
                </th>
                <th role="columnheader" scope="col">
                  Subject
                </th>
                <th role="columnheader" scope="col">
                  Folder
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
              {templateList.map((template, index) => (
                <tr
                  key={template.id}
                  role="row"
                  className={styles.tableRow}
                  data-testid={`email-template-row-${index}`}
                >
                  <td role="gridcell">{template.name}</td>
                  <td role="gridcell">{template.subject}</td>
                  <td role="gridcell">{template.folder || '-'}</td>
                  <td role="gridcell">
                    <span
                      className={`${styles.boolBadge} ${template.active ? styles.boolTrue : styles.boolFalse}`}
                    >
                      {template.active ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td role="gridcell">
                    {formatDate(new Date(template.createdAt), {
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
                          setLogsItemId(template.id)
                          setLogsItemName(template.name)
                        }}
                        aria-label={`View logs for ${template.name}`}
                        data-testid={`logs-button-${index}`}
                      >
                        Logs
                      </button>
                      <button
                        type="button"
                        className={styles.actionButton}
                        onClick={() => handleEdit(template)}
                        aria-label={`Edit ${template.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className={`${styles.actionButton} ${styles.deleteButton}`}
                        onClick={() => handleDeleteClick(template)}
                        aria-label={`Delete ${template.name}`}
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
