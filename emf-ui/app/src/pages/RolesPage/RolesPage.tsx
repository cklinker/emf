/**
 * RolesPage Component
 *
 * Displays a list of all authorization roles with create, edit, and delete actions.
 * Uses TanStack Query for data fetching and includes a modal form for role management.
 *
 * Requirements:
 * - 5.1: Display a list of all defined roles
 * - 5.2: Create role action with form
 * - 5.3: Create role via API and update list
 * - 5.4: Edit role with pre-populated form
 * - 5.5: Delete role with confirmation dialog
 */

import React, { useState, useCallback, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import styles from './RolesPage.module.css'

/**
 * Role interface matching the API response
 */
export interface Role {
  id: string
  name: string
  description?: string
  createdAt: string
}

/**
 * Form data for creating/editing a role
 */
interface RoleFormData {
  name: string
  description: string
}

/**
 * Form validation errors
 */
interface FormErrors {
  name?: string
  description?: string
}

/**
 * Props for RolesPage component
 */
export interface RolesPageProps {
  /** Optional test ID for testing */
  testId?: string
}

/**
 * Validate role form data
 */
function validateForm(data: RoleFormData, t: (key: string) => string): FormErrors {
  const errors: FormErrors = {}

  // Name validation
  if (!data.name.trim()) {
    errors.name = t('roles.validation.nameRequired')
  } else if (data.name.length > 50) {
    errors.name = t('roles.validation.nameTooLong')
  } else if (!/^[a-z][a-z0-9_]*$/.test(data.name)) {
    errors.name = t('roles.validation.nameFormat')
  }

  // Description validation (optional but has max length)
  if (data.description && data.description.length > 500) {
    errors.description = t('roles.validation.descriptionTooLong')
  }

  return errors
}

/**
 * RoleForm Component
 *
 * Modal form for creating and editing roles.
 */
interface RoleFormProps {
  role?: Role
  onSubmit: (data: RoleFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function RoleForm({ role, onSubmit, onCancel, isSubmitting }: RoleFormProps): React.ReactElement {
  const { t } = useI18n()
  const [formData, setFormData] = useState<RoleFormData>({
    name: role?.name ?? '',
    description: role?.description ?? '',
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const nameInputRef = useRef<HTMLInputElement>(null)

  // Focus name input on mount
  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    (field: keyof RoleFormData, value: string) => {
      setFormData((prev) => ({ ...prev, [field]: value }))
      // Clear error when user starts typing
      if (errors[field]) {
        setErrors((prev) => ({ ...prev, [field]: undefined }))
      }
    },
    [errors]
  )

  const handleBlur = useCallback(
    (field: keyof RoleFormData) => {
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
      setTouched({ name: true, description: true })

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

  const isEditing = !!role
  const title = isEditing ? t('authorization.editRole') : t('authorization.createRole')

  return (
    <div
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="role-form-overlay"
      role="presentation"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="role-form-title"
        data-testid="role-form-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="role-form-title" className={styles.modalTitle}>
            {title}
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onCancel}
            aria-label={t('common.close')}
            data-testid="role-form-close"
          >
            ×
          </button>
        </div>
        <div className={styles.modalBody}>
          <form className={styles.form} onSubmit={handleSubmit} noValidate>
            {/* Name Field */}
            <div className={styles.formGroup}>
              <label htmlFor="role-name" className={styles.formLabel}>
                {t('authorization.roleName')}
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="role-name"
                type="text"
                className={`${styles.formInput} ${touched.name && errors.name ? styles.hasError : ''}`}
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                onBlur={() => handleBlur('name')}
                placeholder={t('roles.namePlaceholder')}
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                aria-describedby={errors.name ? 'role-name-error' : undefined}
                disabled={isSubmitting}
                data-testid="role-name-input"
              />
              {touched.name && errors.name && (
                <span id="role-name-error" className={styles.formError} role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            {/* Description Field */}
            <div className={styles.formGroup}>
              <label htmlFor="role-description" className={styles.formLabel}>
                {t('collections.description')}
              </label>
              <textarea
                id="role-description"
                className={`${styles.formTextarea} ${touched.description && errors.description ? styles.hasError : ''}`}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                onBlur={() => handleBlur('description')}
                placeholder={t('roles.descriptionPlaceholder')}
                aria-invalid={touched.description && !!errors.description}
                aria-describedby={errors.description ? 'role-description-error' : undefined}
                disabled={isSubmitting}
                data-testid="role-description-input"
              />
              {touched.description && errors.description && (
                <span id="role-description-error" className={styles.formError} role="alert">
                  {errors.description}
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
                data-testid="role-form-cancel"
              >
                {t('common.cancel')}
              </button>
              <button
                type="submit"
                className={styles.submitButton}
                disabled={isSubmitting}
                data-testid="role-form-submit"
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
 * RolesPage Component
 *
 * Main page for managing authorization roles in the EMF Admin UI.
 * Provides listing and CRUD operations for roles.
 */
export function RolesPage({ testId = 'roles-page' }: RolesPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { t, formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  // Modal state
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingRole, setEditingRole] = useState<Role | undefined>(undefined)

  // Delete confirmation dialog state
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [roleToDelete, setRoleToDelete] = useState<Role | null>(null)

  // Fetch roles query
  const {
    data: roles = [],
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['roles'],
    queryFn: () => apiClient.get<Role[]>('/control/roles'),
  })

  // Create mutation
  const createMutation = useMutation({
    mutationFn: (data: RoleFormData) => apiClient.post<Role>('/control/roles', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['roles'] })
      showToast(t('success.created', { item: t('navigation.roles') }), 'success')
      handleCloseForm()
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: RoleFormData }) =>
      apiClient.put<Role>(`/control/roles/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['roles'] })
      showToast(t('success.updated', { item: t('navigation.roles') }), 'success')
      handleCloseForm()
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/control/roles/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['roles'] })
      showToast(t('success.deleted', { item: t('navigation.roles') }), 'success')
      setDeleteDialogOpen(false)
      setRoleToDelete(null)
    },
    onError: (error: Error) => {
      showToast(error.message || t('errors.generic'), 'error')
    },
  })

  // Handle create action
  const handleCreate = useCallback(() => {
    setEditingRole(undefined)
    setIsFormOpen(true)
  }, [])

  // Handle edit action
  const handleEdit = useCallback((role: Role) => {
    setEditingRole(role)
    setIsFormOpen(true)
  }, [])

  // Handle close form
  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingRole(undefined)
  }, [])

  // Handle form submit
  const handleFormSubmit = useCallback(
    (data: RoleFormData) => {
      if (editingRole) {
        updateMutation.mutate({ id: editingRole.id, data })
      } else {
        createMutation.mutate(data)
      }
    },
    [editingRole, createMutation, updateMutation]
  )

  // Handle delete action - open confirmation dialog
  const handleDeleteClick = useCallback((role: Role) => {
    setRoleToDelete(role)
    setDeleteDialogOpen(true)
  }, [])

  // Handle delete confirmation
  const handleDeleteConfirm = useCallback(() => {
    if (roleToDelete) {
      deleteMutation.mutate(roleToDelete.id)
    }
  }, [roleToDelete, deleteMutation])

  // Handle delete cancel
  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setRoleToDelete(null)
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
        <h1 className={styles.title}>{t('navigation.roles')}</h1>
        <button
          type="button"
          className={styles.createButton}
          onClick={handleCreate}
          aria-label={t('authorization.createRole')}
          data-testid="create-role-button"
        >
          {t('authorization.createRole')}
        </button>
      </header>

      {/* Roles Table */}
      {roles.length === 0 ? (
        <div className={styles.emptyState} data-testid="empty-state">
          <p>{t('common.noResults')}</p>
        </div>
      ) : (
        <div className={styles.tableContainer}>
          <table
            className={styles.table}
            role="grid"
            aria-label={t('navigation.roles')}
            data-testid="roles-table"
          >
            <thead>
              <tr role="row">
                <th role="columnheader" scope="col">
                  {t('authorization.roleName')}
                </th>
                <th role="columnheader" scope="col">
                  {t('collections.description')}
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
              {roles.map((role, index) => (
                <tr
                  key={role.id}
                  role="row"
                  className={styles.tableRow}
                  data-testid={`role-row-${index}`}
                >
                  <td role="gridcell" className={styles.nameCell}>
                    {role.name}
                  </td>
                  <td role="gridcell" className={styles.descriptionCell}>
                    {role.description || '—'}
                  </td>
                  <td role="gridcell" className={styles.dateCell}>
                    {formatDate(new Date(role.createdAt), {
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
                        onClick={() => handleEdit(role)}
                        aria-label={`${t('common.edit')} ${role.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        {t('common.edit')}
                      </button>
                      <button
                        type="button"
                        className={`${styles.actionButton} ${styles.deleteButton}`}
                        onClick={() => handleDeleteClick(role)}
                        aria-label={`${t('common.delete')} ${role.name}`}
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

      {/* Role Form Modal */}
      {isFormOpen && (
        <RoleForm
          role={editingRole}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={isSubmitting}
        />
      )}

      {/* Delete Confirmation Dialog */}
      <ConfirmDialog
        open={deleteDialogOpen}
        title={t('authorization.deleteRole')}
        message={t('roles.confirmDelete')}
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  )
}

export default RolesPage
