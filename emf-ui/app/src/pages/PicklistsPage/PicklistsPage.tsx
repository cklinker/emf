import React, { useState, useCallback, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import { PicklistValuesEditor } from '../../components/PicklistValuesEditor'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'

interface GlobalPicklist {
  id: string
  tenantId: string
  name: string
  description: string | null
  sorted: boolean
  restricted: boolean
  createdAt: string
  updatedAt: string
}

interface PicklistFormData {
  name: string
  description: string
  sorted: boolean
  restricted: boolean
}

interface FormErrors {
  name?: string
  description?: string
}

export interface PicklistsPageProps {
  testId?: string
}

function validateForm(data: PicklistFormData, t: (key: string) => string): FormErrors {
  const errors: FormErrors = {}
  if (!data.name.trim()) {
    errors.name = t('picklists.validation.nameRequired')
  } else if (data.name.length > 100) {
    errors.name = t('picklists.validation.nameTooLong')
  }
  if (data.description && data.description.length > 500) {
    errors.description = t('picklists.validation.descriptionTooLong')
  }
  return errors
}

interface PicklistFormProps {
  picklist?: GlobalPicklist
  onSubmit: (data: PicklistFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function PicklistForm({
  picklist,
  onSubmit,
  onCancel,
  isSubmitting,
}: PicklistFormProps): React.ReactElement {
  const { t } = useI18n()
  const isEditing = !!picklist
  const [formData, setFormData] = useState<PicklistFormData>({
    name: picklist?.name ?? '',
    description: picklist?.description ?? '',
    sorted: picklist?.sorted ?? false,
    restricted: picklist?.restricted ?? true,
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const nameInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    (field: keyof PicklistFormData, value: string | boolean) => {
      setFormData((prev) => ({ ...prev, [field]: value }))
      if (typeof value === 'string' && errors[field as keyof FormErrors]) {
        setErrors((prev) => ({ ...prev, [field]: undefined }))
      }
    },
    [errors]
  )

  const handleBlur = useCallback(
    (field: keyof FormErrors) => {
      setTouched((prev) => ({ ...prev, [field]: true }))
      const validationErrors = validateForm(formData, t)
      if (validationErrors[field]) {
        setErrors((prev) => ({ ...prev, [field]: validationErrors[field] }))
      }
    },
    [formData, t]
  )

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault()
      const validationErrors = validateForm(formData, t)
      setErrors(validationErrors)
      setTouched({ name: true, description: true })
      if (Object.keys(validationErrors).length === 0) {
        onSubmit(formData)
      }
    },
    [formData, onSubmit, t]
  )

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Escape') {
        onCancel()
      }
    },
    [onCancel]
  )

  const title = isEditing ? t('picklists.editPicklist') : t('picklists.addPicklist')

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="picklist-form-overlay"
      role="presentation"
    >
      <div
        className="w-full max-w-[600px] max-h-[90vh] overflow-y-auto rounded-lg bg-background shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="picklist-form-title"
        data-testid="picklist-form-modal"
      >
        <div className="flex items-center justify-between border-b border-border p-6">
          <h2 id="picklist-form-title" className="text-lg font-semibold text-foreground">
            {title}
          </h2>
          <button
            type="button"
            className="rounded p-2 text-2xl leading-none text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            onClick={onCancel}
            aria-label={t('common.close')}
            data-testid="picklist-form-close"
          >
            &times;
          </button>
        </div>
        <div className="p-6">
          <form className="flex flex-col gap-5" onSubmit={handleSubmit} noValidate>
            <div className="flex flex-col gap-2">
              <label htmlFor="picklist-name" className="text-sm font-medium text-foreground">
                {t('picklists.picklistName')}
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="picklist-name"
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
                placeholder={t('picklists.namePlaceholder')}
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                disabled={isSubmitting || isEditing}
                data-testid="picklist-name-input"
              />
              {touched.name && errors.name && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label htmlFor="picklist-description" className="text-sm font-medium text-foreground">
                {t('picklists.description')}
              </label>
              <textarea
                id="picklist-description"
                className={cn(
                  'min-h-[80px] resize-y rounded-md border border-border bg-background px-3 py-2.5 font-[inherit] text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.description && errors.description && 'border-destructive'
                )}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                onBlur={() => handleBlur('description')}
                placeholder={t('picklists.descriptionPlaceholder')}
                disabled={isSubmitting}
                rows={3}
                data-testid="picklist-description-input"
              />
              {touched.description && errors.description && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.description}
                </span>
              )}
            </div>

            <div className="flex items-center gap-2">
              <input
                id="picklist-sorted"
                type="checkbox"
                className="h-4 w-4 accent-primary"
                checked={formData.sorted}
                onChange={(e) => handleChange('sorted', e.target.checked)}
                disabled={isSubmitting}
                data-testid="picklist-sorted-input"
              />
              <label htmlFor="picklist-sorted" className="text-sm font-medium text-foreground">
                {t('picklists.sorted')}
              </label>
            </div>

            <div className="flex items-center gap-2">
              <input
                id="picklist-restricted"
                type="checkbox"
                className="h-4 w-4 accent-primary"
                checked={formData.restricted}
                onChange={(e) => handleChange('restricted', e.target.checked)}
                disabled={isSubmitting}
                data-testid="picklist-restricted-input"
              />
              <label htmlFor="picklist-restricted" className="text-sm font-medium text-foreground">
                {t('picklists.restricted')}
              </label>
            </div>

            <div className="mt-2 flex justify-end gap-3 border-t border-border pt-4">
              <Button
                type="button"
                variant="outline"
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="picklist-form-cancel"
              >
                {t('common.cancel')}
              </Button>
              <Button type="submit" disabled={isSubmitting} data-testid="picklist-form-submit">
                {isSubmitting ? t('common.loading') : t('common.save')}
              </Button>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}

export function PicklistsPage({
  testId = 'picklists-page',
}: PicklistsPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { t, formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingPicklist, setEditingPicklist] = useState<GlobalPicklist | undefined>(undefined)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [picklistToDelete, setPicklistToDelete] = useState<GlobalPicklist | null>(null)
  const [valuesPicklistId, setValuesPicklistId] = useState<string | null>(null)
  const [valuesPicklistName, setValuesPicklistName] = useState('')

  const {
    data: picklists,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['picklists'],
    queryFn: () => apiClient.getList<GlobalPicklist>('/api/global-picklists'),
  })

  const picklistList = picklists ?? []

  const createMutation = useMutation({
    mutationFn: (data: PicklistFormData) =>
      apiClient.postResource<GlobalPicklist>('/api/global-picklists', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['picklists'] })
      showToast(t('success.created', { item: t('picklists.title') }), 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || t('errors.generic'), 'error')
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: PicklistFormData }) =>
      apiClient.putResource<GlobalPicklist>(`/api/global-picklists/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['picklists'] })
      showToast(t('success.updated', { item: t('picklists.title') }), 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || t('errors.generic'), 'error')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/global-picklists/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['picklists'] })
      showToast(t('success.deleted', { item: t('picklists.title') }), 'success')
      setDeleteDialogOpen(false)
      setPicklistToDelete(null)
    },
    onError: (err: Error) => {
      showToast(err.message || t('errors.generic'), 'error')
    },
  })

  const handleCreate = useCallback(() => {
    setEditingPicklist(undefined)
    setIsFormOpen(true)
  }, [])

  const handleEdit = useCallback((pl: GlobalPicklist) => {
    setEditingPicklist(pl)
    setIsFormOpen(true)
  }, [])

  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingPicklist(undefined)
  }, [])

  const handleFormSubmit = useCallback(
    (data: PicklistFormData) => {
      if (editingPicklist) {
        updateMutation.mutate({ id: editingPicklist.id, data })
      } else {
        createMutation.mutate(data)
      }
    },
    [editingPicklist, createMutation, updateMutation]
  )

  const handleDeleteClick = useCallback((pl: GlobalPicklist) => {
    setPicklistToDelete(pl)
    setDeleteDialogOpen(true)
  }, [])

  const handleDeleteConfirm = useCallback(() => {
    if (picklistToDelete) {
      deleteMutation.mutate(picklistToDelete.id)
    }
  }, [picklistToDelete, deleteMutation])

  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setPicklistToDelete(null)
  }, [])

  const handleManageValues = useCallback((pl: GlobalPicklist) => {
    setValuesPicklistId(pl.id)
    setValuesPicklistName(pl.name)
  }, [])

  const handleCloseValues = useCallback(() => {
    setValuesPicklistId(null)
    setValuesPicklistName('')
  }, [])

  if (isLoading) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <div className="flex min-h-[400px] items-center justify-center">
          <LoadingSpinner size="large" label={t('common.loading')} />
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <ErrorMessage
          error={error instanceof Error ? error : new Error(t('errors.generic'))}
          onRetry={() => refetch()}
        />
      </div>
    )
  }

  const isSubmitting = createMutation.isPending || updateMutation.isPending

  return (
    <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-foreground">{t('picklists.title')}</h1>
        <Button
          type="button"
          onClick={handleCreate}
          aria-label={t('picklists.addPicklist')}
          data-testid="add-picklist-button"
        >
          {t('picklists.addPicklist')}
        </Button>
      </header>

      {picklistList.length === 0 ? (
        <div
          className="rounded-lg border border-border bg-card py-16 text-center text-muted-foreground"
          data-testid="empty-state"
        >
          <p>{t('common.noResults')}</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border bg-card">
          <table
            className="w-full border-collapse"
            role="grid"
            aria-label={t('picklists.title')}
            data-testid="picklists-table"
          >
            <thead>
              <tr role="row" className="bg-muted">
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  {t('picklists.picklistName')}
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  {t('picklists.description')}
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  {t('picklists.sorted')}
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  {t('picklists.restricted')}
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  {t('collections.created')}
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  {t('common.actions')}
                </th>
              </tr>
            </thead>
            <tbody>
              {picklistList.map((pl, index) => (
                <tr
                  key={pl.id}
                  role="row"
                  className="border-b border-border transition-colors last:border-b-0 hover:bg-muted/50"
                  data-testid={`picklist-row-${index}`}
                >
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    {pl.name}
                  </td>
                  <td
                    role="gridcell"
                    className="max-w-[300px] truncate px-4 py-3 text-sm text-muted-foreground"
                  >
                    {pl.description || '-'}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    <span
                      className={cn(
                        'inline-block rounded-full px-3 py-1 text-xs font-semibold',
                        pl.sorted
                          ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                          : 'bg-muted text-muted-foreground'
                      )}
                    >
                      {pl.sorted ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    <span
                      className={cn(
                        'inline-block rounded-full px-3 py-1 text-xs font-semibold',
                        pl.restricted
                          ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                          : 'bg-muted text-muted-foreground'
                      )}
                    >
                      {pl.restricted ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    {formatDate(new Date(pl.createdAt), {
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
                        onClick={() => handleManageValues(pl)}
                        aria-label={`${t('picklists.manageValues')} ${pl.name}`}
                        data-testid={`values-button-${index}`}
                      >
                        {t('picklists.manageValues')}
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => handleEdit(pl)}
                        aria-label={`${t('common.edit')} ${pl.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        {t('common.edit')}
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        className="border-destructive/30 text-destructive hover:bg-destructive/10"
                        onClick={() => handleDeleteClick(pl)}
                        aria-label={`${t('common.delete')} ${pl.name}`}
                        data-testid={`delete-button-${index}`}
                      >
                        {t('common.delete')}
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
        <PicklistForm
          picklist={editingPicklist}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={isSubmitting}
        />
      )}

      <ConfirmDialog
        open={deleteDialogOpen}
        title={t('picklists.deletePicklist')}
        message={t('picklists.confirmDelete')}
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />

      {valuesPicklistId && (
        <PicklistValuesEditor
          picklistId={valuesPicklistId}
          picklistName={valuesPicklistName}
          onClose={handleCloseValues}
        />
      )}
    </div>
  )
}

export default PicklistsPage
