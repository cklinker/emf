/**
 * PoliciesPage Component
 *
 * Displays a list of all authorization policies with create, edit, and delete actions.
 * Uses TanStack Query for data fetching and includes a modal form for policy management.
 *
 * Requirements:
 * - 5.6: Display a list of all authorization policies
 * - 5.7: Create policy action with form (name and expression)
 * - 5.8: Edit and delete policy actions
 */

import React, { useState, useCallback, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import styles from './PoliciesPage.module.css'

/**
 * Policy interface matching the API response
 */
export interface Policy {
  id: string
  name: string
  description?: string
  expression: string
  createdAt: string
}

/**
 * Form data for creating/editing a policy
 */
interface PolicyFormData {
  name: string
  description: string
  expression: string
}

/**
 * Form validation errors
 */
interface FormErrors {
  name?: string
  description?: string
  expression?: string
}

/**
 * Props for PoliciesPage component
 */
export interface PoliciesPageProps {
  /** Optional test ID for testing */
  testId?: string
}

/**
 * Validate policy form data
 */
function validateForm(data: PolicyFormData, t: (key: string) => string): FormErrors {
  const errors: FormErrors = {}

  // Name validation
  if (!data.name.trim()) {
    errors.name = t('policies.validation.nameRequired')
  } else if (data.name.length > 50) {
    errors.name = t('policies.validation.nameTooLong')
  } else if (!/^[a-z][a-z0-9_]*$/.test(data.name)) {
    errors.name = t('policies.validation.nameFormat')
  }

  // Description validation (optional but has max length)
  if (data.description && data.description.length > 500) {
    errors.description = t('policies.validation.descriptionTooLong')
  }

  // Expression validation
  if (!data.expression.trim()) {
    errors.expression = t('policies.validation.expressionRequired')
  } else if (data.expression.length > 2000) {
    errors.expression = t('policies.validation.expressionTooLong')
  }

  return errors
}

/**
 * PolicyForm Component
 *
 * Modal form for creating and editing policies.
 */
interface PolicyFormProps {
  policy?: Policy
  onSubmit: (data: PolicyFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function PolicyForm({
  policy,
  onSubmit,
  onCancel,
  isSubmitting,
}: PolicyFormProps): React.ReactElement {
  const { t } = useI18n()
  const [formData, setFormData] = useState<PolicyFormData>({
    name: policy?.name ?? '',
    description: policy?.description ?? '',
    expression: policy?.expression ?? '',
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const nameInputRef = useRef<HTMLInputElement>(null)

  // Focus name input on mount
  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    (field: keyof PolicyFormData, value: string) => {
      setFormData((prev) => ({ ...prev, [field]: value }))
      // Clear error when user starts typing
      if (errors[field]) {
        setErrors((prev) => ({ ...prev, [field]: undefined }))
      }
    },
    [errors]
  )

  const handleBlur = useCallback(
    (field: keyof PolicyFormData) => {
      setTouched((prev) => ({ ...prev, [field]: true }))
      // Validate on blur
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

      // Validate all fields
      const validationErrors = validateForm(formData, t)
      setErrors(validationErrors)
      setTouched({ name: true, description: true, expression: true })

      // If no errors, submit
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

  const isEditing = !!policy
  const title = isEditing ? t('authorization.editPolicy') : t('authorization.createPolicy')

  return (
    <div
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="policy-form-overlay"
      role="presentation"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="policy-form-title"
        data-testid="policy-form-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="policy-form-title" className={styles.modalTitle}>
            {title}
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onCancel}
            aria-label={t('common.close')}
            data-testid="policy-form-close"
          >
            ×
          </button>
        </div>
        <div className={styles.modalBody}>
          <form className={styles.form} onSubmit={handleSubmit} noValidate>
            {/* Name Field */}
            <div className={styles.formGroup}>
              <label htmlFor="policy-name" className={styles.formLabel}>
                {t('authorization.policyName')}
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="policy-name"
                type="text"
                className={`${styles.formInput} ${touched.name && errors.name ? styles.hasError : ''}`}
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                onBlur={() => handleBlur('name')}
                placeholder={t('policies.namePlaceholder')}
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                aria-describedby={errors.name ? 'policy-name-error' : undefined}
                disabled={isSubmitting}
                data-testid="policy-name-input"
              />
              {touched.name && errors.name && (
                <span id="policy-name-error" className={styles.formError} role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            {/* Description Field */}
            <div className={styles.formGroup}>
              <label htmlFor="policy-description" className={styles.formLabel}>
                {t('collections.description')}
              </label>
              <textarea
                id="policy-description"
                className={`${styles.formTextarea} ${touched.description && errors.description ? styles.hasError : ''}`}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                onBlur={() => handleBlur('description')}
                placeholder={t('policies.descriptionPlaceholder')}
                aria-invalid={touched.description && !!errors.description}
                aria-describedby={errors.description ? 'policy-description-error' : undefined}
                disabled={isSubmitting}
                data-testid="policy-description-input"
              />
              {touched.description && errors.description && (
                <span id="policy-description-error" className={styles.formError} role="alert">
                  {errors.description}
                </span>
              )}
            </div>

            {/* Expression Field */}
            <div className={styles.formGroup}>
              <label htmlFor="policy-expression" className={styles.formLabel}>
                {t('policies.expression')}
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <textarea
                id="policy-expression"
                className={`${styles.formTextarea} ${styles.expressionTextarea} ${touched.expression && errors.expression ? styles.hasError : ''}`}
                value={formData.expression}
                onChange={(e) => handleChange('expression', e.target.value)}
                onBlur={() => handleBlur('expression')}
                placeholder={t('policies.expressionPlaceholder')}
                aria-required="true"
                aria-invalid={touched.expression && !!errors.expression}
                aria-describedby={
                  errors.expression ? 'policy-expression-error' : 'policy-expression-hint'
                }
                disabled={isSubmitting}
                data-testid="policy-expression-input"
              />
              <span id="policy-expression-hint" className={styles.formHint}>
                {t('policies.expressionHint')}
              </span>
              {touched.expression && errors.expression && (
                <span id="policy-expression-error" className={styles.formError} role="alert">
                  {errors.expression}
                </span>
              )}
            </div>

            {/* Form Actions */}
            <div className={styles.formActions}>
              <button
                type="button"
                className={styles.cancelButton}
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="policy-form-cancel"
              >
                {t('common.cancel')}
              </button>
              <button
                type="submit"
                className={styles.submitButton}
                disabled={isSubmitting}
                data-testid="policy-form-submit"
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

/**
 * PoliciesPage Component
 *
 * Main page for managing authorization policies in the EMF Admin UI.
 * Provides listing and CRUD operations for policies.
 */
export function PoliciesPage({ testId = 'policies-page' }: PoliciesPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { t, formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  // Modal state
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingPolicy, setEditingPolicy] = useState<Policy | undefined>(undefined)

  // Delete confirmation dialog state
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [policyToDelete, setPolicyToDelete] = useState<Policy | null>(null)

  // Fetch policies query
  const {
    data: policies = [],
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['policies'],
    queryFn: () => apiClient.get<Policy[]>('/control/policies'),
  })

  // Create mutation
  const createMutation = useMutation({
    mutationFn: (data: PolicyFormData) => apiClient.post<Policy>('/control/policies', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['policies'] })
      showToast(t('success.created', { item: t('navigation.policies') }), 'success')
      handleCloseForm()
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: PolicyFormData }) =>
      apiClient.put<Policy>(`/control/policies/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['policies'] })
      showToast(t('success.updated', { item: t('navigation.policies') }), 'success')
      handleCloseForm()
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/control/policies/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['policies'] })
      showToast(t('success.deleted', { item: t('navigation.policies') }), 'success')
      setDeleteDialogOpen(false)
      setPolicyToDelete(null)
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  // Handle create action
  const handleCreate = useCallback(() => {
    setEditingPolicy(undefined)
    setIsFormOpen(true)
  }, [])

  // Handle edit action
  const handleEdit = useCallback((policy: Policy) => {
    setEditingPolicy(policy)
    setIsFormOpen(true)
  }, [])

  // Handle close form
  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingPolicy(undefined)
  }, [])

  // Handle form submit
  const handleFormSubmit = useCallback(
    (data: PolicyFormData) => {
      if (editingPolicy) {
        updateMutation.mutate({ id: editingPolicy.id, data })
      } else {
        createMutation.mutate(data)
      }
    },
    [editingPolicy, createMutation, updateMutation]
  )

  // Handle delete action - open confirmation dialog
  const handleDeleteClick = useCallback((policy: Policy) => {
    setPolicyToDelete(policy)
    setDeleteDialogOpen(true)
  }, [])

  // Handle delete confirmation
  const handleDeleteConfirm = useCallback(() => {
    if (policyToDelete) {
      deleteMutation.mutate(policyToDelete.id)
    }
  }, [policyToDelete, deleteMutation])

  // Handle delete cancel
  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setPolicyToDelete(null)
  }, [])

  // Render loading state
  if (isLoading) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label={t('common.loading')} />
        </div>
      </div>
    )
  }

  // Render error state
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
      {/* Page Header */}
      <header className={styles.header}>
        <h1 className={styles.title}>{t('navigation.policies')}</h1>
        <button
          type="button"
          className={styles.createButton}
          onClick={handleCreate}
          aria-label={t('authorization.createPolicy')}
          data-testid="create-policy-button"
        >
          {t('authorization.createPolicy')}
        </button>
      </header>

      {/* Policies Table */}
      {policies.length === 0 ? (
        <div className={styles.emptyState} data-testid="empty-state">
          <p>{t('common.noResults')}</p>
        </div>
      ) : (
        <div className={styles.tableContainer}>
          <table
            className={styles.table}
            role="grid"
            aria-label={t('navigation.policies')}
            data-testid="policies-table"
          >
            <thead>
              <tr role="row">
                <th role="columnheader" scope="col">
                  {t('authorization.policyName')}
                </th>
                <th role="columnheader" scope="col">
                  {t('collections.description')}
                </th>
                <th role="columnheader" scope="col">
                  {t('policies.expression')}
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
              {policies.map((policy, index) => (
                <tr
                  key={policy.id}
                  role="row"
                  className={styles.tableRow}
                  data-testid={`policy-row-${index}`}
                >
                  <td role="gridcell" className={styles.nameCell}>
                    {policy.name}
                  </td>
                  <td role="gridcell" className={styles.descriptionCell}>
                    {policy.description || '—'}
                  </td>
                  <td role="gridcell" className={styles.expressionCell}>
                    <code className={styles.expressionCode}>{policy.expression}</code>
                  </td>
                  <td role="gridcell" className={styles.dateCell}>
                    {formatDate(new Date(policy.createdAt), {
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
                        onClick={() => handleEdit(policy)}
                        aria-label={`${t('common.edit')} ${policy.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        {t('common.edit')}
                      </button>
                      <button
                        type="button"
                        className={`${styles.actionButton} ${styles.deleteButton}`}
                        onClick={() => handleDeleteClick(policy)}
                        aria-label={`${t('common.delete')} ${policy.name}`}
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

      {/* Policy Form Modal */}
      {isFormOpen && (
        <PolicyForm
          policy={editingPolicy}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={isSubmitting}
        />
      )}

      {/* Delete Confirmation Dialog */}
      <ConfirmDialog
        open={deleteDialogOpen}
        title={t('authorization.deletePolicy')}
        message={t('policies.confirmDelete')}
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  )
}

export default PoliciesPage
