import React, { useState, useCallback, useEffect, useRef, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import type {
  Campaign as SdkCampaign,
  CreateCampaignRequest,
  CampaignStats,
  EmailSuppression,
} from '@kelta/sdk'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { AdminDataTable, type AdminColumn } from '@/components/AdminDataTable'

type Campaign = SdkCampaign

interface CampaignFormData {
  name: string
  description: string
  subject: string
  bodyHtml: string
  targetCollection: string
  recipientEmailField: string
  fromName: string
  fromAddress: string
  scheduledAt: string
}

interface FormErrors {
  name?: string
  subject?: string
  targetCollection?: string
  recipientEmailField?: string
  fromAddress?: string
}

interface CollectionOption {
  id: string
  name: string
  displayName: string
}

export interface CampaignsPageProps {
  testId?: string
}

/** Statuses in which a campaign can still be edited. */
const EDITABLE_STATUSES = new Set(['DRAFT', 'SCHEDULED'])

function validateForm(data: CampaignFormData): FormErrors {
  const errors: FormErrors = {}
  if (!data.name.trim()) {
    errors.name = 'Name is required'
  } else if (data.name.length > 100) {
    errors.name = 'Name must be 100 characters or fewer'
  }
  if (!data.subject.trim()) {
    errors.subject = 'Subject is required'
  } else if (data.subject.length > 200) {
    errors.subject = 'Subject must be 200 characters or fewer'
  }
  if (!data.targetCollection.trim()) {
    errors.targetCollection = 'Target collection is required'
  }
  if (!data.recipientEmailField.trim()) {
    errors.recipientEmailField = 'Recipient email field is required'
  }
  if (data.fromAddress && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.fromAddress)) {
    errors.fromAddress = 'Enter a valid email address'
  }
  return errors
}

interface CampaignFormProps {
  campaign?: Campaign
  collections: CollectionOption[]
  onSubmit: (data: CampaignFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function CampaignForm({
  campaign,
  collections,
  onSubmit,
  onCancel,
  isSubmitting,
}: CampaignFormProps): React.ReactElement {
  const isEditing = !!campaign
  const [formData, setFormData] = useState<CampaignFormData>({
    name: campaign?.name ?? '',
    description: campaign?.description ?? '',
    subject: campaign?.subject ?? '',
    bodyHtml: campaign?.bodyHtml ?? '',
    targetCollection: campaign?.targetCollection ?? '',
    recipientEmailField: campaign?.recipientEmailField ?? '',
    fromName: campaign?.fromName ?? '',
    fromAddress: campaign?.fromAddress ?? '',
    scheduledAt: campaign?.scheduledAt ? campaign.scheduledAt.slice(0, 16) : '',
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})

  const nameInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    <K extends keyof CampaignFormData>(field: K, value: CampaignFormData[K]) => {
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
        subject: true,
        targetCollection: true,
        recipientEmailField: true,
        fromAddress: true,
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

  const inputClass = (invalid: boolean): string =>
    cn(
      'rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground transition-colors',
      'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
      'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
      invalid && 'border-destructive'
    )

  const title = isEditing ? 'Edit Campaign' : 'New Campaign'

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="campaign-form-overlay"
      role="presentation"
    >
      <div
        className="flex max-h-[90vh] w-full max-w-[720px] flex-col overflow-hidden rounded-lg bg-background shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="campaign-form-title"
        data-testid="campaign-form-modal"
      >
        <div className="flex items-center justify-between border-b border-border px-6 py-4">
          <h2 id="campaign-form-title" className="text-lg font-semibold text-foreground">
            {title}
          </h2>
          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={onCancel}
              disabled={isSubmitting}
              data-testid="campaign-form-cancel"
            >
              Cancel
            </Button>
            <Button
              type="submit"
              form="campaign-form"
              disabled={isSubmitting}
              data-testid="campaign-form-submit"
            >
              {isSubmitting ? 'Saving…' : 'Save'}
            </Button>
          </div>
        </div>

        <div className="overflow-y-auto">
          <form
            id="campaign-form"
            className="flex flex-col gap-4 p-6"
            onSubmit={handleSubmit}
            noValidate
          >
            <div className="flex flex-col gap-2">
              <label htmlFor="campaign-name" className="text-sm font-medium text-foreground">
                Name
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="campaign-name"
                type="text"
                className={inputClass(!!(touched.name && errors.name))}
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                onBlur={() => handleBlur('name')}
                placeholder="Enter campaign name"
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                disabled={isSubmitting}
                data-testid="campaign-name-input"
              />
              {touched.name && errors.name && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label htmlFor="campaign-description" className="text-sm font-medium text-foreground">
                Description
              </label>
              <textarea
                id="campaign-description"
                className={cn(inputClass(false), 'min-h-[60px] resize-y font-[inherit]')}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                placeholder="Enter description (optional)"
                disabled={isSubmitting}
                rows={2}
                data-testid="campaign-description-input"
              />
            </div>

            <div className="flex flex-col gap-2">
              <label htmlFor="campaign-subject" className="text-sm font-medium text-foreground">
                Subject
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                id="campaign-subject"
                type="text"
                className={inputClass(!!(touched.subject && errors.subject))}
                value={formData.subject}
                onChange={(e) => handleChange('subject', e.target.value)}
                onBlur={() => handleBlur('subject')}
                placeholder="Enter email subject"
                aria-required="true"
                aria-invalid={touched.subject && !!errors.subject}
                disabled={isSubmitting}
                data-testid="campaign-subject-input"
              />
              {touched.subject && errors.subject && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.subject}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label htmlFor="campaign-body" className="text-sm font-medium text-foreground">
                Body
              </label>
              <textarea
                id="campaign-body"
                className={cn(inputClass(false), 'min-h-[120px] resize-y font-[inherit]')}
                value={formData.bodyHtml}
                onChange={(e) => handleChange('bodyHtml', e.target.value)}
                placeholder="Compose your email body (HTML supported)"
                disabled={isSubmitting}
                rows={5}
                data-testid="campaign-body-input"
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-2">
                <label
                  htmlFor="campaign-target-collection"
                  className="text-sm font-medium text-foreground"
                >
                  Target collection
                  <span className="ml-1 text-destructive" aria-hidden="true">
                    *
                  </span>
                </label>
                <select
                  id="campaign-target-collection"
                  className={inputClass(!!(touched.targetCollection && errors.targetCollection))}
                  value={formData.targetCollection}
                  onChange={(e) => handleChange('targetCollection', e.target.value)}
                  onBlur={() => handleBlur('targetCollection')}
                  aria-required="true"
                  aria-invalid={touched.targetCollection && !!errors.targetCollection}
                  disabled={isSubmitting}
                  data-testid="campaign-target-collection-input"
                >
                  <option value="">— Select —</option>
                  {collections.map((c) => (
                    <option key={c.id} value={c.name}>
                      {c.displayName}
                    </option>
                  ))}
                </select>
                {touched.targetCollection && errors.targetCollection && (
                  <span className="text-xs text-destructive" role="alert">
                    {errors.targetCollection}
                  </span>
                )}
              </div>

              <div className="flex flex-col gap-2">
                <label
                  htmlFor="campaign-recipient-field"
                  className="text-sm font-medium text-foreground"
                >
                  Recipient email field
                  <span className="ml-1 text-destructive" aria-hidden="true">
                    *
                  </span>
                </label>
                <input
                  id="campaign-recipient-field"
                  type="text"
                  className={inputClass(
                    !!(touched.recipientEmailField && errors.recipientEmailField)
                  )}
                  value={formData.recipientEmailField}
                  onChange={(e) => handleChange('recipientEmailField', e.target.value)}
                  onBlur={() => handleBlur('recipientEmailField')}
                  placeholder="e.g. email"
                  aria-required="true"
                  aria-invalid={touched.recipientEmailField && !!errors.recipientEmailField}
                  disabled={isSubmitting}
                  data-testid="campaign-recipient-field-input"
                />
                {touched.recipientEmailField && errors.recipientEmailField && (
                  <span className="text-xs text-destructive" role="alert">
                    {errors.recipientEmailField}
                  </span>
                )}
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-2">
                <label htmlFor="campaign-from-name" className="text-sm font-medium text-foreground">
                  From name
                </label>
                <input
                  id="campaign-from-name"
                  type="text"
                  className={inputClass(false)}
                  value={formData.fromName}
                  onChange={(e) => handleChange('fromName', e.target.value)}
                  placeholder="e.g. Acme Team"
                  disabled={isSubmitting}
                  data-testid="campaign-from-name-input"
                />
              </div>

              <div className="flex flex-col gap-2">
                <label
                  htmlFor="campaign-from-address"
                  className="text-sm font-medium text-foreground"
                >
                  From address
                </label>
                <input
                  id="campaign-from-address"
                  type="email"
                  className={inputClass(!!(touched.fromAddress && errors.fromAddress))}
                  value={formData.fromAddress}
                  onChange={(e) => handleChange('fromAddress', e.target.value)}
                  onBlur={() => handleBlur('fromAddress')}
                  placeholder="e.g. hello@acme.com"
                  aria-invalid={touched.fromAddress && !!errors.fromAddress}
                  disabled={isSubmitting}
                  data-testid="campaign-from-address-input"
                />
                {touched.fromAddress && errors.fromAddress && (
                  <span className="text-xs text-destructive" role="alert">
                    {errors.fromAddress}
                  </span>
                )}
              </div>
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="campaign-scheduled-at"
                className="text-sm font-medium text-foreground"
              >
                Schedule for (optional)
              </label>
              <input
                id="campaign-scheduled-at"
                type="datetime-local"
                className={inputClass(false)}
                value={formData.scheduledAt}
                onChange={(e) => handleChange('scheduledAt', e.target.value)}
                disabled={isSubmitting}
                data-testid="campaign-scheduled-at-input"
              />
              <span className="text-[11px] text-muted-foreground">
                Leave empty to keep the campaign as a draft.
              </span>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}

interface StatsModalProps {
  campaignName: string
  stats?: CampaignStats
  isLoading: boolean
  error: Error | null
  onClose: () => void
}

function StatsModal({
  campaignName,
  stats,
  isLoading,
  error,
  onClose,
}: StatsModalProps): React.ReactElement {
  const rows: Array<{ label: string; value: number | undefined }> = [
    { label: 'Total recipients', value: stats?.totalRecipients },
    { label: 'Sent', value: stats?.sent },
    { label: 'Failed', value: stats?.failed },
    { label: 'Opens', value: stats?.opens },
    { label: 'Clicks', value: stats?.clicks },
    { label: 'Unsubscribes', value: stats?.unsubscribes },
  ]

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      onClick={(e) => e.target === e.currentTarget && onClose()}
      role="presentation"
      data-testid="campaign-stats-overlay"
    >
      <div
        className="flex max-h-[80vh] w-full max-w-[480px] flex-col overflow-hidden rounded-lg bg-background shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-label={`Stats for ${campaignName}`}
        data-testid="campaign-stats-modal"
      >
        <div className="flex items-center justify-between border-b border-border px-6 py-4">
          <h2 className="text-lg font-semibold text-foreground">Campaign stats</h2>
          <Button type="button" variant="outline" size="sm" onClick={onClose}>
            Close
          </Button>
        </div>
        <div className="p-6">
          {isLoading ? (
            <div className="flex min-h-[120px] items-center justify-center">
              <LoadingSpinner size="medium" label="Loading stats..." />
            </div>
          ) : error ? (
            <ErrorMessage error={error} />
          ) : (
            <>
              <p className="mb-4 text-sm text-muted-foreground">
                Status: <span className="font-medium text-foreground">{stats?.status ?? '-'}</span>
              </p>
              <dl className="grid grid-cols-2 gap-3">
                {rows.map((r) => (
                  <div key={r.label} className="rounded-md border border-border bg-card px-3 py-2">
                    <dt className="text-[11px] uppercase tracking-wide text-muted-foreground">
                      {r.label}
                    </dt>
                    <dd className="text-lg font-semibold text-foreground">{r.value ?? 0}</dd>
                  </div>
                ))}
              </dl>
            </>
          )}
        </div>
      </div>
    </div>
  )
}

interface SuppressionsSectionProps {
  suppressions: EmailSuppression[]
  isLoading: boolean
  error: Error | null
  onAdd: (email: string, reason: string) => void
  onRemove: (email: string) => void
  isMutating: boolean
}

function SuppressionsSection({
  suppressions,
  isLoading,
  error,
  onAdd,
  onRemove,
  isMutating,
}: SuppressionsSectionProps): React.ReactElement {
  const [email, setEmail] = useState('')
  const [reason, setReason] = useState('')

  const handleAdd = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault()
      if (email.trim()) {
        onAdd(email.trim(), reason.trim())
        setEmail('')
        setReason('')
      }
    },
    [email, reason, onAdd]
  )

  return (
    <section className="space-y-4" data-testid="suppressions-section">
      <h2 className="text-lg font-semibold text-foreground">Suppressions</h2>
      <p className="text-sm text-muted-foreground">
        Suppressed addresses are excluded from all campaign sends.
      </p>
      <form className="flex flex-wrap items-end gap-3" onSubmit={handleAdd}>
        <div className="flex flex-col gap-1">
          <label htmlFor="suppression-email" className="text-xs font-medium text-foreground">
            Email
          </label>
          <input
            id="suppression-email"
            type="email"
            className="rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="user@example.com"
            data-testid="suppression-email-input"
          />
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="suppression-reason" className="text-xs font-medium text-foreground">
            Reason
          </label>
          <input
            id="suppression-reason"
            type="text"
            className="rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Optional"
            data-testid="suppression-reason-input"
          />
        </div>
        <Button
          type="submit"
          disabled={isMutating || !email.trim()}
          data-testid="add-suppression-button"
        >
          Add
        </Button>
      </form>

      {isLoading ? (
        <div className="flex min-h-[80px] items-center justify-center">
          <LoadingSpinner size="small" label="Loading suppressions..." />
        </div>
      ) : error ? (
        <ErrorMessage error={error} />
      ) : suppressions.length === 0 ? (
        <div
          className="rounded-lg border border-border bg-card py-8 text-center text-muted-foreground"
          data-testid="suppressions-empty-state"
        >
          <p>No suppressed addresses.</p>
        </div>
      ) : (
        <ul
          className="divide-y divide-border rounded-lg border border-border bg-card"
          data-testid="suppressions-list"
        >
          {suppressions.map((s) => (
            <li key={s.id} className="flex items-center justify-between px-4 py-2 text-sm">
              <span className="text-foreground">
                {s.email}
                {s.reason ? <span className="ml-2 text-muted-foreground">({s.reason})</span> : null}
              </span>
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="border-destructive/30 text-destructive hover:bg-destructive/10"
                onClick={() => onRemove(s.email)}
                disabled={isMutating}
                aria-label={`Remove suppression ${s.email}`}
              >
                Remove
              </Button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export function CampaignsPage({
  testId = 'campaigns-page',
}: CampaignsPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { formatDate } = useI18n()
  const { keltaClient } = useApi()
  const { showToast } = useToast()

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingCampaign, setEditingCampaign] = useState<Campaign | undefined>(undefined)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [campaignToDelete, setCampaignToDelete] = useState<Campaign | null>(null)
  const [statsCampaign, setStatsCampaign] = useState<Campaign | null>(null)

  const {
    data: campaigns,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['campaigns'],
    queryFn: () => keltaClient.admin.campaigns.list(),
  })

  const { data: collectionsData } = useQuery({
    queryKey: ['campaign-collections'],
    queryFn: () => keltaClient.admin.collections.list(),
    staleTime: 5 * 60 * 1000,
  })

  const {
    data: statsData,
    isLoading: statsLoading,
    error: statsError,
  } = useQuery({
    queryKey: ['campaign-stats', statsCampaign?.id],
    queryFn: () => keltaClient.admin.campaigns.stats(statsCampaign!.id),
    enabled: !!statsCampaign,
  })

  const {
    data: suppressionsData,
    isLoading: suppressionsLoading,
    error: suppressionsError,
  } = useQuery({
    queryKey: ['campaign-suppressions'],
    queryFn: () => keltaClient.admin.campaigns.listSuppressions(),
  })

  const collections: CollectionOption[] = useMemo(() => {
    return (
      (collectionsData ?? []) as Array<{
        id?: string
        name: string
        displayName?: string
      }>
    ).map((c) => ({
      id: c.id ?? c.name,
      name: c.name,
      displayName: c.displayName ?? c.name,
    }))
  }, [collectionsData])

  const campaignList: Campaign[] = campaigns ?? []
  const suppressionList: EmailSuppression[] = suppressionsData ?? []

  const invalidateCampaigns = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['campaigns'] })
  }, [queryClient])

  const buildRequest = (data: CampaignFormData): CreateCampaignRequest => ({
    name: data.name,
    description: data.description || undefined,
    subject: data.subject,
    bodyHtml: data.bodyHtml || undefined,
    targetCollection: data.targetCollection,
    recipientEmailField: data.recipientEmailField,
    fromName: data.fromName || undefined,
    fromAddress: data.fromAddress || undefined,
    scheduledAt: data.scheduledAt ? new Date(data.scheduledAt).toISOString() : undefined,
  })

  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingCampaign(undefined)
  }, [])

  const createMutation = useMutation({
    mutationFn: (data: CampaignFormData) => keltaClient.admin.campaigns.create(buildRequest(data)),
    onSuccess: () => {
      invalidateCampaigns()
      showToast('Campaign created successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => showToast(err.message || 'An error occurred', 'error'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: CampaignFormData }) =>
      keltaClient.admin.campaigns.update(id, buildRequest(data)),
    onSuccess: () => {
      invalidateCampaigns()
      showToast('Campaign updated successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => showToast(err.message || 'An error occurred', 'error'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => keltaClient.admin.campaigns.delete(id),
    onSuccess: () => {
      invalidateCampaigns()
      showToast('Campaign deleted successfully', 'success')
      setDeleteDialogOpen(false)
      setCampaignToDelete(null)
    },
    onError: (err: Error) => showToast(err.message || 'An error occurred', 'error'),
  })

  const sendMutation = useMutation({
    mutationFn: (id: string) => keltaClient.admin.campaigns.send(id),
    onSuccess: () => {
      invalidateCampaigns()
      showToast('Campaign queued for sending', 'success')
    },
    onError: (err: Error) => showToast(err.message || 'An error occurred', 'error'),
  })

  const scheduleMutation = useMutation({
    mutationFn: ({ id, scheduledAt }: { id: string; scheduledAt: string }) =>
      keltaClient.admin.campaigns.schedule(id, scheduledAt),
    onSuccess: () => {
      invalidateCampaigns()
      showToast('Campaign scheduled', 'success')
    },
    onError: (err: Error) => showToast(err.message || 'An error occurred', 'error'),
  })

  const cancelMutation = useMutation({
    mutationFn: (id: string) => keltaClient.admin.campaigns.cancel(id),
    onSuccess: () => {
      invalidateCampaigns()
      showToast('Campaign cancelled', 'success')
    },
    onError: (err: Error) => showToast(err.message || 'An error occurred', 'error'),
  })

  const testMutation = useMutation({
    mutationFn: ({ id, email }: { id: string; email: string }) =>
      keltaClient.admin.campaigns.test(id, email),
    onSuccess: () => showToast('Test email sent', 'success'),
    onError: (err: Error) => showToast(err.message || 'An error occurred', 'error'),
  })

  const addSuppressionMutation = useMutation({
    mutationFn: ({ email, reason }: { email: string; reason: string }) =>
      keltaClient.admin.campaigns.addSuppression(email, reason || undefined),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['campaign-suppressions'] })
      showToast('Suppression added', 'success')
    },
    onError: (err: Error) => showToast(err.message || 'An error occurred', 'error'),
  })

  const removeSuppressionMutation = useMutation({
    mutationFn: (email: string) => keltaClient.admin.campaigns.removeSuppression(email),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['campaign-suppressions'] })
      showToast('Suppression removed', 'success')
    },
    onError: (err: Error) => showToast(err.message || 'An error occurred', 'error'),
  })

  const handleCreate = useCallback(() => {
    setEditingCampaign(undefined)
    setIsFormOpen(true)
  }, [])

  const handleEdit = useCallback((campaign: Campaign) => {
    setEditingCampaign(campaign)
    setIsFormOpen(true)
  }, [])

  const handleFormSubmit = useCallback(
    (data: CampaignFormData) => {
      if (editingCampaign) {
        updateMutation.mutate({ id: editingCampaign.id, data })
      } else {
        createMutation.mutate(data)
      }
    },
    [editingCampaign, createMutation, updateMutation]
  )

  const handleSchedule = useCallback(
    (campaign: Campaign) => {
      const input = window.prompt(
        'Schedule send date/time (ISO, e.g. 2026-08-01T09:00):',
        campaign.scheduledAt ?? ''
      )
      if (input) {
        const iso = new Date(input).toISOString()
        scheduleMutation.mutate({ id: campaign.id, scheduledAt: iso })
      }
    },
    [scheduleMutation]
  )

  const handleTest = useCallback(
    (campaign: Campaign) => {
      const email = window.prompt('Send a test to which email address?')
      if (email && email.trim()) {
        testMutation.mutate({ id: campaign.id, email: email.trim() })
      }
    },
    [testMutation]
  )

  const handleDeleteConfirm = useCallback(() => {
    if (campaignToDelete) {
      deleteMutation.mutate(campaignToDelete.id)
    }
  }, [campaignToDelete, deleteMutation])

  if (isLoading) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <div className="flex min-h-[400px] items-center justify-center">
          <LoadingSpinner size="large" label="Loading campaigns..." />
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
  const isSuppressionMutating =
    addSuppressionMutation.isPending || removeSuppressionMutation.isPending

  return (
    <div className="mx-auto max-w-[1400px] space-y-8 p-6 lg:p-8" data-testid={testId}>
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-foreground">Campaigns</h1>
        <Button
          type="button"
          onClick={handleCreate}
          aria-label="New Campaign"
          data-testid="add-campaign-button"
        >
          New Campaign
        </Button>
      </header>

      {campaignList.length === 0 ? (
        <div
          className="rounded-lg border border-border bg-card py-16 text-center text-muted-foreground"
          data-testid="empty-state"
        >
          <p>No campaigns found.</p>
        </div>
      ) : (
        <div
          className="overflow-x-auto rounded-lg border border-border bg-card"
          role="grid"
          aria-label="Campaigns"
          data-testid="campaigns-table"
        >
          <AdminDataTable
            tableId="campaigns"
            rows={campaignList}
            rowKey={(c) => c.id}
            columns={
              [
                { id: 'name', header: 'Name', accessor: (r) => r.name },
                {
                  id: 'targetCollection',
                  header: 'Target',
                  accessor: (r) => r.targetCollection,
                },
                {
                  id: 'status',
                  header: 'Status',
                  accessor: (r) => r.status,
                  cell: (r) => (
                    <span className="inline-block rounded-full bg-muted px-3 py-1 text-xs font-semibold text-muted-foreground">
                      {r.status}
                    </span>
                  ),
                },
                {
                  id: 'sent',
                  header: 'Sent',
                  accessor: (r) => r.sentCount ?? 0,
                  cell: (r) => String(r.sentCount ?? 0),
                },
                {
                  id: 'opens',
                  header: 'Opens',
                  accessor: (r) => r.openCount ?? 0,
                  cell: (r) => String(r.openCount ?? 0),
                },
                {
                  id: 'clicks',
                  header: 'Clicks',
                  accessor: (r) => r.clickCount ?? 0,
                  cell: (r) => String(r.clickCount ?? 0),
                },
                {
                  id: 'scheduledAt',
                  header: 'Scheduled',
                  accessor: (r) => r.scheduledAt ?? '',
                  cell: (r) =>
                    r.scheduledAt
                      ? formatDate(new Date(r.scheduledAt), {
                          year: 'numeric',
                          month: 'short',
                          day: 'numeric',
                          hour: '2-digit',
                          minute: '2-digit',
                        })
                      : '-',
                },
              ] as AdminColumn<Campaign>[]
            }
            renderActions={(campaign) => {
              const index = campaignList.indexOf(campaign)
              const editable = EDITABLE_STATUSES.has(campaign.status)
              const cancellable = campaign.status === 'SCHEDULED' || campaign.status === 'QUEUED'
              return (
                <div className="flex flex-wrap justify-end gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => sendMutation.mutate(campaign.id)}
                    aria-label={`Send ${campaign.name}`}
                    data-testid={`send-button-${index}`}
                  >
                    Send now
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => handleSchedule(campaign)}
                    aria-label={`Schedule ${campaign.name}`}
                    data-testid={`schedule-button-${index}`}
                  >
                    Schedule
                  </Button>
                  {cancellable && (
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => cancelMutation.mutate(campaign.id)}
                      aria-label={`Cancel ${campaign.name}`}
                      data-testid={`cancel-button-${index}`}
                    >
                      Cancel
                    </Button>
                  )}
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => setStatsCampaign(campaign)}
                    aria-label={`View stats for ${campaign.name}`}
                    data-testid={`stats-button-${index}`}
                  >
                    Stats
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => handleTest(campaign)}
                    aria-label={`Send test for ${campaign.name}`}
                    data-testid={`test-button-${index}`}
                  >
                    Send test
                  </Button>
                  {editable && (
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => handleEdit(campaign)}
                      aria-label={`Edit ${campaign.name}`}
                      data-testid={`edit-button-${index}`}
                    >
                      Edit
                    </Button>
                  )}
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="border-destructive/30 text-destructive hover:bg-destructive/10"
                    onClick={() => {
                      setCampaignToDelete(campaign)
                      setDeleteDialogOpen(true)
                    }}
                    aria-label={`Delete ${campaign.name}`}
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

      <SuppressionsSection
        suppressions={suppressionList}
        isLoading={suppressionsLoading}
        error={suppressionsError instanceof Error ? suppressionsError : null}
        onAdd={(email, reason) => addSuppressionMutation.mutate({ email, reason })}
        onRemove={(email) => removeSuppressionMutation.mutate(email)}
        isMutating={isSuppressionMutating}
      />

      {isFormOpen && (
        <CampaignForm
          campaign={editingCampaign}
          collections={collections}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={isSubmitting}
        />
      )}

      {statsCampaign && (
        <StatsModal
          campaignName={statsCampaign.name}
          stats={statsData}
          isLoading={statsLoading}
          error={statsError instanceof Error ? statsError : null}
          onClose={() => setStatsCampaign(null)}
        />
      )}

      <ConfirmDialog
        open={deleteDialogOpen}
        title="Delete Campaign"
        message="Are you sure you want to delete this campaign? This action cannot be undone."
        confirmLabel="Delete"
        cancelLabel="Cancel"
        onConfirm={handleDeleteConfirm}
        onCancel={() => {
          setDeleteDialogOpen(false)
          setCampaignToDelete(null)
        }}
        variant="danger"
      />
    </div>
  )
}

export default CampaignsPage
