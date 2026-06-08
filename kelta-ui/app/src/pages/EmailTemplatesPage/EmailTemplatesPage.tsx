import React, { useState, useCallback, useEffect, useRef, useMemo } from 'react'
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
import type { CreateEmailTemplateRequest, EmailTemplate as SdkEmailTemplate } from '@kelta/sdk'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { AdminDataTable, type AdminColumn } from '@/components/AdminDataTable'
import { Plus } from 'lucide-react'
import { FieldExpressionPicker, type StaticNamespace } from '../../components/FieldExpressionPicker'
import { RichTextEditor, type RichTextEditorHandle } from '../../components/RichTextEditor'
import { tokenizeMergeTags } from '../../components/RichTextEditor'

type EmailTemplate = SdkEmailTemplate

interface EmailTemplateFormData {
  name: string
  description: string
  subject: string
  bodyHtml: string
  bodyText: string
  relatedCollectionId: string
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

interface CollectionOption {
  id: string
  name: string
  displayName: string
}

export interface EmailTemplatesPageProps {
  testId?: string
}

const ENVELOPE_NAMESPACES: StaticNamespace[] = [
  {
    name: 'recipient',
    label: 'Recipient',
    fields: [
      { name: 'email', displayName: 'Email', type: 'email' },
      { name: 'firstName', displayName: 'First name', type: 'string' },
      { name: 'lastName', displayName: 'Last name', type: 'string' },
      { name: 'fullName', displayName: 'Full name', type: 'string' },
    ],
  },
  {
    name: 'currentUser',
    label: 'Current user',
    fields: [
      { name: 'email', displayName: 'Email', type: 'email' },
      { name: 'firstName', displayName: 'First name', type: 'string' },
      { name: 'lastName', displayName: 'Last name', type: 'string' },
      { name: 'fullName', displayName: 'Full name', type: 'string' },
    ],
  },
]

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
  collections: CollectionOption[]
  onSubmit: (data: EmailTemplateFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

type PickerTarget = 'subject' | 'body' | 'bodyText' | null

function EmailTemplateForm({
  template,
  collections,
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
    relatedCollectionId: template?.relatedCollectionId ?? '',
    folder: template?.folder ?? '',
    active: template?.active ?? true,
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const [pickerTarget, setPickerTarget] = useState<PickerTarget>(null)

  const nameInputRef = useRef<HTMLInputElement>(null)
  const subjectInputRef = useRef<HTMLInputElement>(null)
  const bodyTextRef = useRef<HTMLTextAreaElement>(null)
  const editorRef = useRef<RichTextEditorHandle>(null)

  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    <K extends keyof EmailTemplateFormData>(field: K, value: EmailTemplateFormData[K]) => {
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
      if (e.key === 'Escape' && !pickerTarget) {
        onCancel()
      }
    },
    [onCancel, pickerTarget]
  )

  const insertAtCaret = useCallback(
    (
      el: HTMLInputElement | HTMLTextAreaElement | null,
      currentValue: string,
      insertion: string,
      onUpdate: (next: string) => void
    ) => {
      const start = el?.selectionStart ?? currentValue.length
      const end = el?.selectionEnd ?? currentValue.length
      const next = currentValue.slice(0, start) + insertion + currentValue.slice(end)
      onUpdate(next)
      requestAnimationFrame(() => {
        if (el) {
          const caret = start + insertion.length
          el.focus()
          el.setSelectionRange(caret, caret)
        }
      })
    },
    []
  )

  const handlePickerInsert = useCallback(
    (token: string) => {
      const wrapped = `{{${token}}}`
      switch (pickerTarget) {
        case 'subject':
          insertAtCaret(subjectInputRef.current, formData.subject, wrapped, (next) =>
            handleChange('subject', next)
          )
          break
        case 'bodyText':
          insertAtCaret(bodyTextRef.current, formData.bodyText, wrapped, (next) =>
            handleChange('bodyText', next)
          )
          break
        case 'body':
          editorRef.current?.insertMergeTag(token)
          break
        default:
          break
      }
      setPickerTarget(null)
    },
    [pickerTarget, formData.subject, formData.bodyText, handleChange, insertAtCaret]
  )

  const previewHtml = useMemo(() => {
    // Highlight unresolved merge tags so they're visible in the preview pane.
    return tokenizeMergeTags(formData.bodyHtml || '<p><em>Email body preview…</em></p>')
  }, [formData.bodyHtml])

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
        className="flex h-[90vh] w-full max-w-[1400px] flex-col overflow-hidden rounded-lg bg-background shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="email-template-form-title"
        data-testid="email-template-form-modal"
      >
        <div className="flex items-center justify-between border-b border-border px-6 py-4">
          <h2 id="email-template-form-title" className="text-lg font-semibold text-foreground">
            {title}
          </h2>
          <div className="flex items-center gap-2">
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
              form="email-template-form"
              disabled={isSubmitting}
              data-testid="email-template-form-submit"
            >
              {isSubmitting ? 'Saving…' : 'Save'}
            </Button>
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
        </div>

        <div className="grid flex-1 grid-cols-[minmax(0,7fr)_minmax(0,5fr)] overflow-hidden">
          {/* Left: form */}
          <div className="overflow-y-auto border-r border-border">
            <form
              id="email-template-form"
              className="flex flex-col gap-4 p-6"
              onSubmit={handleSubmit}
              noValidate
            >
              <div className="grid grid-cols-2 gap-4">
                <div className="flex flex-col gap-2">
                  <label
                    htmlFor="email-template-name"
                    className="text-sm font-medium text-foreground"
                  >
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
                      'rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground transition-colors',
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
                    htmlFor="email-template-related-collection"
                    className="text-sm font-medium text-foreground"
                  >
                    Related collection
                  </label>
                  <select
                    id="email-template-related-collection"
                    className={cn(
                      'rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground transition-colors',
                      'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                      'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground'
                    )}
                    value={formData.relatedCollectionId}
                    onChange={(e) => handleChange('relatedCollectionId', e.target.value)}
                    disabled={isSubmitting}
                    data-testid="email-template-related-collection-input"
                  >
                    <option value="">— None —</option>
                    {collections.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.displayName}
                      </option>
                    ))}
                  </select>
                  <span className="text-[11px] text-muted-foreground">
                    Sets which collection's fields appear in the field picker.
                  </span>
                </div>
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
                    'min-h-[60px] resize-y rounded-md border border-border bg-background px-3 py-2 font-[inherit] text-sm text-foreground transition-colors',
                    'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                    touched.description && errors.description && 'border-destructive'
                  )}
                  value={formData.description}
                  onChange={(e) => handleChange('description', e.target.value)}
                  onBlur={() => handleBlur('description')}
                  placeholder="Enter template description"
                  disabled={isSubmitting}
                  rows={2}
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
                <div className="flex items-center gap-2">
                  <input
                    ref={subjectInputRef}
                    id="email-template-subject"
                    type="text"
                    className={cn(
                      'flex-1 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground transition-colors',
                      'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
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
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="gap-1 px-2 text-xs"
                    onClick={() => setPickerTarget('subject')}
                    data-testid="email-template-subject-insert-field"
                  >
                    <Plus className="h-3 w-3" />
                    Insert field
                  </Button>
                </div>
                {touched.subject && errors.subject && (
                  <span className="text-xs text-destructive" role="alert">
                    {errors.subject}
                  </span>
                )}
              </div>

              <div className="flex flex-col gap-2">
                <div
                  className="text-sm font-medium text-foreground"
                  id="email-template-body-html-label"
                >
                  Body HTML
                  <span className="ml-1 text-destructive" aria-hidden="true">
                    *
                  </span>
                </div>
                <RichTextEditor
                  ref={editorRef}
                  value={formData.bodyHtml}
                  onChange={(html) => handleChange('bodyHtml', html)}
                  onInsertFieldClick={() => setPickerTarget('body')}
                  placeholder="Compose your email…"
                  disabled={isSubmitting}
                  testId="email-template-body-html"
                />
                {touched.bodyHtml && errors.bodyHtml && (
                  <span className="text-xs text-destructive" role="alert">
                    {errors.bodyHtml}
                  </span>
                )}
                <details className="mt-1 rounded-md border border-border bg-muted/20 px-3 py-2 text-xs">
                  <summary className="cursor-pointer font-medium text-foreground">
                    How merge tags work
                  </summary>
                  <div className="mt-2 space-y-1 text-muted-foreground">
                    <p>
                      Use <code className="font-mono">{'{{path}}'}</code> to insert a value from the
                      related collection. Click <strong>Insert field</strong> to pick one — it
                      inserts the correct token for you.
                    </p>
                    <ul className="list-inside list-disc space-y-0.5 pl-2">
                      <li>
                        Plain field: <code className="font-mono">{'{{firstName}}'}</code>
                      </li>
                      <li>
                        Across a relationship:{' '}
                        <code className="font-mono">{'{{account_id.name}}'}</code>
                      </li>
                      <li>
                        With a function: <code className="font-mono">{'{{TEXT(amount)}}'}</code>
                      </li>
                      <li>
                        Envelope variables:{' '}
                        <code className="font-mono">{'{{recipient.firstName}}'}</code>,{' '}
                        <code className="font-mono">{'{{currentUser.email}}'}</code>
                      </li>
                    </ul>
                  </div>
                </details>
              </div>

              <div className="flex flex-col gap-2">
                <label
                  htmlFor="email-template-body-text"
                  className="text-sm font-medium text-foreground"
                >
                  Body text (plain-text fallback)
                </label>
                <div className="flex items-start gap-2">
                  <textarea
                    ref={bodyTextRef}
                    id="email-template-body-text"
                    className={cn(
                      'min-h-[80px] flex-1 resize-y rounded-md border border-border bg-background px-3 py-2 font-[inherit] text-sm text-foreground transition-colors',
                      'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
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
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="gap-1 px-2 text-xs"
                    onClick={() => setPickerTarget('bodyText')}
                    data-testid="email-template-body-text-insert-field"
                  >
                    <Plus className="h-3 w-3" />
                    Insert field
                  </Button>
                </div>
                {touched.bodyText && errors.bodyText && (
                  <span className="text-xs text-destructive" role="alert">
                    {errors.bodyText}
                  </span>
                )}
              </div>

              <div className="grid grid-cols-2 gap-4">
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
                      'rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground transition-colors',
                      'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
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
                <div className="flex items-end gap-2 pb-1">
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
              </div>
            </form>
          </div>

          {/* Right: preview */}
          <div className="flex flex-col overflow-hidden bg-muted/10">
            <div className="border-b border-border bg-background px-4 py-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
              Preview
            </div>
            <div className="flex flex-1 flex-col gap-3 overflow-y-auto p-4">
              <div className="rounded-md border border-border bg-background px-4 py-3">
                <div className="text-[11px] uppercase tracking-wide text-muted-foreground">
                  Subject
                </div>
                <div
                  className="mt-1 break-words text-sm font-medium text-foreground"
                  data-testid="email-template-preview-subject"
                  dangerouslySetInnerHTML={{
                    __html: tokenizeMergeTags(formData.subject || ''),
                  }}
                />
              </div>
              <div className="min-h-[320px] flex-1 overflow-hidden rounded-md border border-border bg-white">
                <iframe
                  title="Email body preview"
                  sandbox=""
                  srcDoc={`<!doctype html><html><head><meta charset="utf-8"><style>
                    :root { color-scheme: light; }
                    html, body { background: #ffffff; }
                    body { font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, sans-serif; padding: 16px; color: #111; margin: 0; line-height: 1.5; }
                    body > *:first-child { margin-top: 0; }
                    p { margin: 0 0 0.75em; }
                    h1, h2, h3 { color: #111; margin: 1em 0 0.5em; }
                    a { color: #2563eb; }
                    [data-merge-tag] { display: inline-block; border-radius: 4px; background: rgba(99,102,241,0.15); color: #4338ca; padding: 0 4px; font-family: ui-monospace, monospace; font-size: 0.9em; }
                  </style></head><body>${previewHtml}</body></html>`}
                  className="h-full w-full"
                  data-testid="email-template-preview-iframe"
                />
              </div>
              <div className="rounded-md border border-dashed border-border bg-background/50 px-3 py-2 text-[11px] text-muted-foreground">
                The preview shows merge tags as styled chips. Real values will be substituted when
                the email is sent.
              </div>
            </div>
          </div>
        </div>
      </div>

      <FieldExpressionPicker
        open={pickerTarget !== null}
        onOpenChange={(o) => {
          if (!o) setPickerTarget(null)
        }}
        rootCollectionId={formData.relatedCollectionId || null}
        staticNamespaces={ENVELOPE_NAMESPACES}
        onInsert={handlePickerInsert}
        title={
          pickerTarget === 'subject'
            ? 'Insert into subject'
            : pickerTarget === 'bodyText'
              ? 'Insert into plain-text body'
              : 'Insert into body'
        }
      />
    </div>
  )
}

export function EmailTemplatesPage({
  testId = 'email-templates-page',
}: EmailTemplatesPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { formatDate } = useI18n()
  const { keltaClient } = useApi()
  const { showToast } = useToast()

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingTemplate, setEditingTemplate] = useState<EmailTemplate | undefined>(undefined)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [templateToDelete, setTemplateToDelete] = useState<EmailTemplate | null>(null)
  const [logsItemId, setLogsItemId] = useState<string | null>(null)
  const [_logsItemName, setLogsItemName] = useState('')

  const {
    data: allEmailLogs,
    isLoading: _logsLoading,
    error: _logsError,
  } = useQuery({
    queryKey: ['email-template-logs', logsItemId],
    queryFn: () => keltaClient.admin.emailTemplates.listLogs(),
    enabled: !!logsItemId,
  })

  const filteredLogs = (allEmailLogs ?? []).filter((log) => log.templateId === logsItemId)
  void filteredLogs

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
  void logColumns

  const {
    data: templates,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['email-templates'],
    queryFn: () => keltaClient.admin.emailTemplates.list(),
  })

  const { data: collectionsData } = useQuery({
    queryKey: ['email-template-collections'],
    queryFn: () => keltaClient.admin.collections.list(),
    staleTime: 5 * 60 * 1000,
  })

  const collections: CollectionOption[] = useMemo(() => {
    return (collectionsData ?? []).map((c) => ({
      id: c.id ?? '',
      name: c.name,
      displayName: c.displayName ?? c.name,
    }))
  }, [collectionsData])

  const templateList: EmailTemplate[] = templates ?? []

  const createMutation = useMutation({
    mutationFn: (data: EmailTemplateFormData) =>
      keltaClient.admin.emailTemplates.create(
        '',
        '',
        data as unknown as CreateEmailTemplateRequest
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
      keltaClient.admin.emailTemplates.update(
        id,
        data as unknown as Partial<CreateEmailTemplateRequest>
      ),
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
    mutationFn: (id: string) => keltaClient.admin.emailTemplates.delete(id),
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
        <h1 className="text-2xl font-semibold text-foreground">Email templates</h1>
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
        <div
          className="overflow-x-auto rounded-lg border border-border bg-card"
          role="grid"
          aria-label="Email Templates"
          data-testid="email-templates-table"
        >
          <AdminDataTable
            tableId="email-templates"
            rows={templateList}
            rowKey={(t) => t.id}
            columns={
              [
                { id: 'name', header: 'Name', accessor: (r) => r.name },
                { id: 'subject', header: 'Subject', accessor: (r) => r.subject },
                {
                  id: 'folder',
                  header: 'Folder',
                  accessor: (r) => r.folder ?? '',
                  cell: (r) => r.folder || '-',
                },
                {
                  id: 'active',
                  header: 'Active',
                  accessor: (r) => r.active,
                  cell: (r) => (
                    <span
                      className={cn(
                        'inline-block rounded-full px-3 py-1 text-xs font-semibold',
                        r.active
                          ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                          : 'bg-muted text-muted-foreground'
                      )}
                    >
                      {r.active ? 'Yes' : 'No'}
                    </span>
                  ),
                },
                {
                  id: 'createdAt',
                  header: 'Created',
                  accessor: (r) => r.createdAt,
                  cell: (r) =>
                    formatDate(new Date(r.createdAt), {
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric',
                    }),
                },
              ] as AdminColumn<EmailTemplate>[]
            }
            renderActions={(template) => {
              const index = templateList.indexOf(template)
              return (
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
              )
            }}
          />
        </div>
      )}

      {logsItemId && (
        <ExecutionLogModal<EmailLog>
          title="Email Logs"
          subtitle={_logsItemName}
          columns={logColumns}
          data={filteredLogs}
          isLoading={_logsLoading}
          error={_logsError instanceof Error ? _logsError : null}
          onClose={() => setLogsItemId(null)}
          emptyMessage="No email logs found."
        />
      )}

      {isFormOpen && (
        <EmailTemplateForm
          template={editingTemplate}
          collections={collections}
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
