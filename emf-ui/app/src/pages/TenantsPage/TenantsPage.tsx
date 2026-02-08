/**
 * TenantsPage Component
 *
 * Platform admin page for managing tenants. Provides CRUD operations,
 * status management (suspend/activate), and displays tenant metadata.
 *
 * Requirements:
 * - A13: Tenant Administration UI
 */

import React, { useState, useCallback, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import styles from './TenantsPage.module.css'

/**
 * Tenant interface matching the API response
 */
export interface Tenant {
  id: string
  slug: string
  name: string
  edition: string
  status: string
  settings?: string
  limits?: string
  createdAt: string
  updatedAt: string
}

/**
 * Form data for creating/editing a tenant
 */
interface TenantFormData {
  slug: string
  name: string
  edition: string
}

/**
 * Form validation errors
 */
interface FormErrors {
  slug?: string
  name?: string
  edition?: string
}

/**
 * Props for TenantsPage component
 */
export interface TenantsPageProps {
  testId?: string
}

const VALID_EDITIONS = ['FREE', 'PROFESSIONAL', 'ENTERPRISE', 'UNLIMITED']

function validateForm(data: TenantFormData, isEditing: boolean): FormErrors {
  const errors: FormErrors = {}

  if (!isEditing) {
    if (!data.slug.trim()) {
      errors.slug = 'Tenant slug is required'
    } else if (data.slug.length > 50) {
      errors.slug = 'Slug must be 50 characters or less'
    } else if (!/^[a-z][a-z0-9-]*$/.test(data.slug)) {
      errors.slug =
        'Slug must be lowercase, start with a letter, and contain only letters, numbers, and hyphens'
    }
  }

  if (!data.name.trim()) {
    errors.name = 'Tenant name is required'
  } else if (data.name.length > 100) {
    errors.name = 'Name must be 100 characters or less'
  }

  if (!VALID_EDITIONS.includes(data.edition)) {
    errors.edition = 'Invalid edition'
  }

  return errors
}

/**
 * TenantForm Component — Modal form for creating and editing tenants.
 */
interface TenantFormProps {
  tenant?: Tenant
  onSubmit: (data: TenantFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function TenantForm({
  tenant,
  onSubmit,
  onCancel,
  isSubmitting,
}: TenantFormProps): React.ReactElement {
  const isEditing = !!tenant
  const [formData, setFormData] = useState<TenantFormData>({
    slug: tenant?.slug ?? '',
    name: tenant?.name ?? '',
    edition: tenant?.edition ?? 'PROFESSIONAL',
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const nameInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    (field: keyof TenantFormData, value: string) => {
      setFormData((prev) => ({ ...prev, [field]: value }))
      if (errors[field]) {
        setErrors((prev) => ({ ...prev, [field]: undefined }))
      }
    },
    [errors]
  )

  const handleBlur = useCallback(
    (field: keyof TenantFormData) => {
      setTouched((prev) => ({ ...prev, [field]: true }))
      const validationErrors = validateForm(formData, isEditing)
      if (validationErrors[field]) {
        setErrors((prev) => ({ ...prev, [field]: validationErrors[field] }))
      }
    },
    [formData, isEditing]
  )

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault()
      const validationErrors = validateForm(formData, isEditing)
      setErrors(validationErrors)
      setTouched({ slug: true, name: true, edition: true })
      if (Object.keys(validationErrors).length === 0) {
        onSubmit(formData)
      }
    },
    [formData, onSubmit, isEditing]
  )

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Escape') {
        onCancel()
      }
    },
    [onCancel]
  )

  const title = isEditing ? 'Edit Tenant' : 'Create Tenant'

  return (
    <div
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="tenant-form-overlay"
      role="presentation"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="tenant-form-title"
        data-testid="tenant-form-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="tenant-form-title" className={styles.modalTitle}>
            {title}
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onCancel}
            aria-label="Close"
            data-testid="tenant-form-close"
          >
            ×
          </button>
        </div>
        <div className={styles.modalBody}>
          <form className={styles.form} onSubmit={handleSubmit} noValidate>
            {!isEditing && (
              <div className={styles.formGroup}>
                <label htmlFor="tenant-slug" className={styles.formLabel}>
                  Slug
                  <span className={styles.required} aria-hidden="true">
                    *
                  </span>
                </label>
                <input
                  id="tenant-slug"
                  type="text"
                  className={`${styles.formInput} ${touched.slug && errors.slug ? styles.hasError : ''}`}
                  value={formData.slug}
                  onChange={(e) => handleChange('slug', e.target.value)}
                  onBlur={() => handleBlur('slug')}
                  placeholder="e.g., acme-corp"
                  aria-required="true"
                  aria-invalid={touched.slug && !!errors.slug}
                  disabled={isSubmitting}
                  data-testid="tenant-slug-input"
                />
                {touched.slug && errors.slug && (
                  <span className={styles.formError} role="alert">
                    {errors.slug}
                  </span>
                )}
              </div>
            )}

            <div className={styles.formGroup}>
              <label htmlFor="tenant-name" className={styles.formLabel}>
                Name
                <span className={styles.required} aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="tenant-name"
                type="text"
                className={`${styles.formInput} ${touched.name && errors.name ? styles.hasError : ''}`}
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                onBlur={() => handleBlur('name')}
                placeholder="e.g., Acme Corporation"
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                disabled={isSubmitting}
                data-testid="tenant-name-input"
              />
              {touched.name && errors.name && (
                <span className={styles.formError} role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="tenant-edition" className={styles.formLabel}>
                Edition
              </label>
              <select
                id="tenant-edition"
                className={styles.formInput}
                value={formData.edition}
                onChange={(e) => handleChange('edition', e.target.value)}
                disabled={isSubmitting}
                data-testid="tenant-edition-select"
              >
                {VALID_EDITIONS.map((edition) => (
                  <option key={edition} value={edition}>
                    {edition}
                  </option>
                ))}
              </select>
            </div>

            <div className={styles.formActions}>
              <button
                type="button"
                className={styles.cancelButton}
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="tenant-form-cancel"
              >
                Cancel
              </button>
              <button
                type="submit"
                className={styles.submitButton}
                disabled={isSubmitting}
                data-testid="tenant-form-submit"
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

/**
 * Status badge component
 */
function StatusBadge({ status }: { status: string }): React.ReactElement {
  const className = `${styles.statusBadge} ${
    status === 'ACTIVE'
      ? styles.statusActive
      : status === 'SUSPENDED'
        ? styles.statusSuspended
        : status === 'PROVISIONING'
          ? styles.statusProvisioning
          : styles.statusDefault
  }`
  return <span className={className}>{status}</span>
}

/**
 * TenantsPage Component
 *
 * Main page for managing tenants in the EMF Platform Admin UI.
 */
export function TenantsPage({ testId = 'tenants-page' }: TenantsPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingTenant, setEditingTenant] = useState<Tenant | undefined>(undefined)
  const [suspendDialogOpen, setSuspendDialogOpen] = useState(false)
  const [activateDialogOpen, setActivateDialogOpen] = useState(false)
  const [targetTenant, setTargetTenant] = useState<Tenant | null>(null)

  // Fetch tenants
  const {
    data: tenantsPage,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['tenants'],
    queryFn: () =>
      apiClient.get<{ content: Tenant[]; totalElements: number }>(
        '/platform/tenants?page=0&size=100'
      ),
  })

  const tenants = tenantsPage?.content ?? []

  // Create mutation
  const createMutation = useMutation({
    mutationFn: (data: TenantFormData) =>
      apiClient.post<Tenant>('/platform/tenants', {
        slug: data.slug,
        name: data.name,
        edition: data.edition,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tenants'] })
      showToast('Tenant created successfully.', 'success')
      handleCloseForm()
    },
    onError: (error: Error) => {
      showToast(error.message || 'Failed to create tenant.', 'error')
    },
  })

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: TenantFormData }) =>
      apiClient.put<Tenant>(`/platform/tenants/${id}`, {
        name: data.name,
        edition: data.edition,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tenants'] })
      showToast('Tenant updated successfully.', 'success')
      handleCloseForm()
    },
    onError: (error: Error) => {
      showToast(error.message || 'Failed to update tenant.', 'error')
    },
  })

  // Suspend mutation
  const suspendMutation = useMutation({
    mutationFn: (id: string) => apiClient.post(`/platform/tenants/${id}/suspend`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tenants'] })
      showToast('Tenant suspended.', 'success')
      setSuspendDialogOpen(false)
      setTargetTenant(null)
    },
    onError: (error: Error) => {
      showToast(error.message || 'Failed to suspend tenant.', 'error')
    },
  })

  // Activate mutation
  const activateMutation = useMutation({
    mutationFn: (id: string) => apiClient.post(`/platform/tenants/${id}/activate`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tenants'] })
      showToast('Tenant activated.', 'success')
      setActivateDialogOpen(false)
      setTargetTenant(null)
    },
    onError: (error: Error) => {
      showToast(error.message || 'Failed to activate tenant.', 'error')
    },
  })

  const handleCreate = useCallback(() => {
    setEditingTenant(undefined)
    setIsFormOpen(true)
  }, [])

  const handleEdit = useCallback((tenant: Tenant) => {
    setEditingTenant(tenant)
    setIsFormOpen(true)
  }, [])

  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingTenant(undefined)
  }, [])

  const handleFormSubmit = useCallback(
    (data: TenantFormData) => {
      if (editingTenant) {
        updateMutation.mutate({ id: editingTenant.id, data })
      } else {
        createMutation.mutate(data)
      }
    },
    [editingTenant, createMutation, updateMutation]
  )

  const handleSuspendClick = useCallback((tenant: Tenant) => {
    setTargetTenant(tenant)
    setSuspendDialogOpen(true)
  }, [])

  const handleActivateClick = useCallback((tenant: Tenant) => {
    setTargetTenant(tenant)
    setActivateDialogOpen(true)
  }, [])

  const handleSuspendConfirm = useCallback(() => {
    if (targetTenant) {
      suspendMutation.mutate(targetTenant.id)
    }
  }, [targetTenant, suspendMutation])

  const handleActivateConfirm = useCallback(() => {
    if (targetTenant) {
      activateMutation.mutate(targetTenant.id)
    }
  }, [targetTenant, activateMutation])

  if (isLoading) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label="Loading tenants..." />
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className={styles.container} data-testid={testId}>
        <ErrorMessage
          error={error instanceof Error ? error : new Error('Failed to load tenants.')}
          onRetry={() => refetch()}
        />
      </div>
    )
  }

  const isSubmitting = createMutation.isPending || updateMutation.isPending

  return (
    <div className={styles.container} data-testid={testId}>
      <header className={styles.header}>
        <h1 className={styles.title}>Tenants</h1>
        <button
          type="button"
          className={styles.createButton}
          onClick={handleCreate}
          aria-label="Create Tenant"
          data-testid="create-tenant-button"
        >
          Create Tenant
        </button>
      </header>

      {tenants.length === 0 ? (
        <div className={styles.emptyState} data-testid="empty-state">
          <p>No tenants found.</p>
        </div>
      ) : (
        <div className={styles.tableContainer}>
          <table
            className={styles.table}
            role="grid"
            aria-label="Tenants"
            data-testid="tenants-table"
          >
            <thead>
              <tr role="row">
                <th role="columnheader" scope="col">
                  Slug
                </th>
                <th role="columnheader" scope="col">
                  Name
                </th>
                <th role="columnheader" scope="col">
                  Edition
                </th>
                <th role="columnheader" scope="col">
                  Status
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
              {tenants.map((tenant, index) => (
                <tr
                  key={tenant.id}
                  role="row"
                  className={styles.tableRow}
                  data-testid={`tenant-row-${index}`}
                >
                  <td role="gridcell" className={styles.nameCell}>
                    {tenant.slug}
                  </td>
                  <td role="gridcell">{tenant.name}</td>
                  <td role="gridcell">{tenant.edition}</td>
                  <td role="gridcell">
                    <StatusBadge status={tenant.status} />
                  </td>
                  <td role="gridcell" className={styles.dateCell}>
                    {formatDate(new Date(tenant.createdAt), {
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
                        onClick={() => handleEdit(tenant)}
                        aria-label={`Edit ${tenant.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        Edit
                      </button>
                      {tenant.status === 'ACTIVE' ? (
                        <button
                          type="button"
                          className={`${styles.actionButton} ${styles.suspendButton}`}
                          onClick={() => handleSuspendClick(tenant)}
                          aria-label={`Suspend ${tenant.name}`}
                          data-testid={`suspend-button-${index}`}
                        >
                          Suspend
                        </button>
                      ) : tenant.status === 'SUSPENDED' ? (
                        <button
                          type="button"
                          className={`${styles.actionButton} ${styles.activateButton}`}
                          onClick={() => handleActivateClick(tenant)}
                          aria-label={`Activate ${tenant.name}`}
                          data-testid={`activate-button-${index}`}
                        >
                          Activate
                        </button>
                      ) : null}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {isFormOpen && (
        <TenantForm
          tenant={editingTenant}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={isSubmitting}
        />
      )}

      <ConfirmDialog
        open={suspendDialogOpen}
        title="Suspend Tenant"
        message={`Are you sure you want to suspend "${targetTenant?.name}"? Users will not be able to access this tenant's data.`}
        confirmLabel="Suspend"
        cancelLabel="Cancel"
        onConfirm={handleSuspendConfirm}
        onCancel={() => {
          setSuspendDialogOpen(false)
          setTargetTenant(null)
        }}
        variant="danger"
      />

      <ConfirmDialog
        open={activateDialogOpen}
        title="Activate Tenant"
        message={`Are you sure you want to activate "${targetTenant?.name}"?`}
        confirmLabel="Activate"
        cancelLabel="Cancel"
        onConfirm={handleActivateConfirm}
        onCancel={() => {
          setActivateDialogOpen(false)
          setTargetTenant(null)
        }}
      />
    </div>
  )
}

export default TenantsPage
