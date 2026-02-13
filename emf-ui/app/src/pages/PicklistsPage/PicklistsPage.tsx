import React, { useState, useCallback, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import { PicklistValuesEditor } from '../../components/PicklistValuesEditor'
import { getTenantId } from '../../hooks'
import styles from './PicklistsPage.module.css'

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
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="picklist-form-overlay"
      role="presentation"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="picklist-form-title"
        data-testid="picklist-form-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="picklist-form-title" className={styles.modalTitle}>
            {title}
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onCancel}
            aria-label={t('common.close')}
            data-testid="picklist-form-close"
          >
            &times;
          </button>
        </div>
        <div className={styles.modalBody}>
          <form className={styles.form} onSubmit={handleSubmit} noValidate>
            <div className={styles.formGroup}>
              <label htmlFor="picklist-name" className={styles.formLabel}>
                {t('picklists.picklistName')}
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="picklist-name"
                type="text"
                className={`${styles.formInput} ${touched.name && errors.name ? styles.hasError : ''}`}
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
                <span className={styles.formError} role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="picklist-description" className={styles.formLabel}>
                {t('picklists.description')}
              </label>
              <textarea
                id="picklist-description"
                className={`${styles.formInput} ${styles.formTextarea} ${touched.description && errors.description ? styles.hasError : ''}`}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                onBlur={() => handleBlur('description')}
                placeholder={t('picklists.descriptionPlaceholder')}
                disabled={isSubmitting}
                rows={3}
                data-testid="picklist-description-input"
              />
              {touched.description && errors.description && (
                <span className={styles.formError} role="alert">
                  {errors.description}
                </span>
              )}
            </div>

            <div className={styles.checkboxGroup}>
              <input
                id="picklist-sorted"
                type="checkbox"
                checked={formData.sorted}
                onChange={(e) => handleChange('sorted', e.target.checked)}
                disabled={isSubmitting}
                data-testid="picklist-sorted-input"
              />
              <label htmlFor="picklist-sorted" className={styles.formLabel}>
                {t('picklists.sorted')}
              </label>
            </div>

            <div className={styles.checkboxGroup}>
              <input
                id="picklist-restricted"
                type="checkbox"
                checked={formData.restricted}
                onChange={(e) => handleChange('restricted', e.target.checked)}
                disabled={isSubmitting}
                data-testid="picklist-restricted-input"
              />
              <label htmlFor="picklist-restricted" className={styles.formLabel}>
                {t('picklists.restricted')}
              </label>
            </div>

            <div className={styles.formActions}>
              <button
                type="button"
                className={styles.cancelButton}
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="picklist-form-cancel"
              >
                {t('common.cancel')}
              </button>
              <button
                type="submit"
                className={styles.submitButton}
                disabled={isSubmitting}
                data-testid="picklist-form-submit"
              >
                {isSubmitting ? t('common.loading') : t('common.save')}
              </button>
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
    queryFn: () =>
      apiClient.get<GlobalPicklist[]>(`/control/picklists/global?tenantId=${getTenantId()}`),
  })

  const picklistList = picklists ?? []

  const createMutation = useMutation({
    mutationFn: (data: PicklistFormData) =>
      apiClient.post<GlobalPicklist>(`/control/picklists/global?tenantId=${getTenantId()}`, data),
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
      apiClient.put<GlobalPicklist>(`/control/picklists/global/${id}`, data),
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
    mutationFn: (id: string) => apiClient.delete(`/control/picklists/global/${id}`),
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
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label={t('common.loading')} />
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className={styles.container} data-testid={testId}>
        <ErrorMessage
          error={error instanceof Error ? error : new Error(t('errors.generic'))}
          onRetry={() => refetch()}
        />
      </div>
    )
  }

  const isSubmitting = createMutation.isPending || updateMutation.isPending

  return (
    <div className={styles.container} data-testid={testId}>
      <header className={styles.header}>
        <h1 className={styles.title}>{t('picklists.title')}</h1>
        <button
          type="button"
          className={styles.createButton}
          onClick={handleCreate}
          aria-label={t('picklists.addPicklist')}
          data-testid="add-picklist-button"
        >
          {t('picklists.addPicklist')}
        </button>
      </header>

      {picklistList.length === 0 ? (
        <div className={styles.emptyState} data-testid="empty-state">
          <p>{t('common.noResults')}</p>
        </div>
      ) : (
        <div className={styles.tableContainer}>
          <table
            className={styles.table}
            role="grid"
            aria-label={t('picklists.title')}
            data-testid="picklists-table"
          >
            <thead>
              <tr role="row">
                <th role="columnheader" scope="col">
                  {t('picklists.picklistName')}
                </th>
                <th role="columnheader" scope="col">
                  {t('picklists.description')}
                </th>
                <th role="columnheader" scope="col">
                  {t('picklists.sorted')}
                </th>
                <th role="columnheader" scope="col">
                  {t('picklists.restricted')}
                </th>
                <th role="columnheader" scope="col">
                  {t('collections.created')}
                </th>
                <th role="columnheader" scope="col">
                  {t('common.actions')}
                </th>
              </tr>
            </thead>
            <tbody>
              {picklistList.map((pl, index) => (
                <tr
                  key={pl.id}
                  role="row"
                  className={styles.tableRow}
                  data-testid={`picklist-row-${index}`}
                >
                  <td role="gridcell">{pl.name}</td>
                  <td role="gridcell" className={styles.descriptionCell}>
                    {pl.description || '-'}
                  </td>
                  <td role="gridcell">
                    <span
                      className={`${styles.boolBadge} ${pl.sorted ? styles.boolTrue : styles.boolFalse}`}
                    >
                      {pl.sorted ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td role="gridcell">
                    <span
                      className={`${styles.boolBadge} ${pl.restricted ? styles.boolTrue : styles.boolFalse}`}
                    >
                      {pl.restricted ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td role="gridcell">
                    {formatDate(new Date(pl.createdAt), {
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
                        onClick={() => handleManageValues(pl)}
                        aria-label={`${t('picklists.manageValues')} ${pl.name}`}
                        data-testid={`values-button-${index}`}
                      >
                        {t('picklists.manageValues')}
                      </button>
                      <button
                        type="button"
                        className={styles.actionButton}
                        onClick={() => handleEdit(pl)}
                        aria-label={`${t('common.edit')} ${pl.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        {t('common.edit')}
                      </button>
                      <button
                        type="button"
                        className={`${styles.actionButton} ${styles.deleteButton}`}
                        onClick={() => handleDeleteClick(pl)}
                        aria-label={`${t('common.delete')} ${pl.name}`}
                        data-testid={`delete-button-${index}`}
                      >
                        {t('common.delete')}
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
