/**
 * PermissionSetsPage Component
 *
 * Displays a list of permission sets with create, edit, and delete functionality.
 * System permission sets cannot be edited or deleted. Clicking a row navigates
 * to the detail page. Uses TanStack Query for data fetching and modal forms.
 */

import React, { useState, useCallback, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { Shield, Plus, Lock, Pencil, Copy, Trash2 } from 'lucide-react'
import { useApi } from '../../context/ApiContext'
import { useTenant } from '../../context/TenantContext'
import { useI18n } from '../../context/I18nContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'

interface PermissionSet {
  id: string
  name: string
  description: string | null
  system: boolean
  createdAt: string
  updatedAt: string
}

interface PermissionSetFormData {
  name: string
  description: string
}

interface PermissionSetFormErrors {
  name?: string
}

export interface PermissionSetsPageProps {
  testId?: string
}

function validateForm(data: PermissionSetFormData): PermissionSetFormErrors {
  const errors: PermissionSetFormErrors = {}
  if (!data.name.trim()) {
    errors.name = 'Permission set name is required'
  } else if (data.name.length > 255) {
    errors.name = 'Name must be 255 characters or fewer'
  }
  return errors
}

interface PermissionSetFormModalProps {
  permissionSet?: PermissionSet
  onSubmit: (data: PermissionSetFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function PermissionSetFormModal({
  permissionSet,
  onSubmit,
  onCancel,
  isSubmitting,
}: PermissionSetFormModalProps) {
  const nameInputRef = useRef<HTMLInputElement>(null)
  const [formData, setFormData] = useState<PermissionSetFormData>({
    name: permissionSet?.name ?? '',
    description: permissionSet?.description ?? '',
  })
  const [errors, setErrors] = useState<PermissionSetFormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})

  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onCancel()
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [onCancel])

  const validate = useCallback(
    (data: PermissionSetFormData): PermissionSetFormErrors => validateForm(data),
    []
  )

  const handleChange = useCallback(
    (field: keyof PermissionSetFormData, value: string) => {
      const updated = { ...formData, [field]: value }
      setFormData(updated)
      if (touched[field]) {
        const newErrors = validate(updated)
        setErrors((prev) => ({
          ...prev,
          [field]: newErrors[field as keyof PermissionSetFormErrors],
        }))
      }
    },
    [formData, touched, validate]
  )

  const handleBlur = useCallback(
    (field: keyof PermissionSetFormData) => {
      setTouched((prev) => ({ ...prev, [field]: true }))
      const newErrors = validate(formData)
      setErrors((prev) => ({
        ...prev,
        [field]: newErrors[field as keyof PermissionSetFormErrors],
      }))
    },
    [formData, validate]
  )

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault()
      const newErrors = validate(formData)
      setErrors(newErrors)
      setTouched({ name: true, description: true })
      if (Object.keys(newErrors).length === 0) {
        onSubmit(formData)
      }
    },
    [formData, onSubmit, validate]
  )

  const inputClassName = (field: keyof PermissionSetFormErrors) =>
    cn(
      'w-full rounded-md border bg-background px-3 py-2 text-sm',
      touched[field] && errors[field] ? 'border-destructive' : 'border-border'
    )

  return (
    <div
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/50"
      role="dialog"
      aria-modal="true"
      aria-labelledby="permission-set-form-title"
      data-testid="permission-set-form-modal"
    >
      <div className="w-full max-w-[480px] rounded-lg border border-border bg-card shadow-xl">
        <div className="flex items-center justify-between border-b border-border px-6 py-4">
          <h2 id="permission-set-form-title" className="text-lg font-semibold text-foreground">
            {permissionSet ? 'Edit Permission Set' : 'New Permission Set'}
          </h2>
          <button
            type="button"
            className="rounded p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
            onClick={onCancel}
            aria-label="Close"
            data-testid="permission-set-form-close"
          >
            ×
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="flex flex-col gap-4 px-6 py-4">
            <div className="flex flex-col gap-1">
              <label htmlFor="permission-set-name" className="text-sm font-medium text-foreground">
                Name <span className="text-destructive">*</span>
              </label>
              <input
                ref={nameInputRef}
                id="permission-set-name"
                type="text"
                className={inputClassName('name')}
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                onBlur={() => handleBlur('name')}
                aria-invalid={touched.name && !!errors.name}
                aria-describedby={errors.name ? 'permission-set-name-error' : undefined}
                disabled={isSubmitting}
                placeholder="Enter permission set name"
                data-testid="permission-set-name-input"
              />
              {touched.name && errors.name && (
                <span
                  id="permission-set-name-error"
                  className="text-xs text-destructive"
                  role="alert"
                  data-testid="permission-set-name-error"
                >
                  {errors.name}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-1">
              <label
                htmlFor="permission-set-description"
                className="text-sm font-medium text-foreground"
              >
                Description
              </label>
              <textarea
                id="permission-set-description"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                disabled={isSubmitting}
                placeholder="Enter permission set description"
                rows={3}
                data-testid="permission-set-description-input"
              />
            </div>
          </div>

          <div className="flex justify-end gap-3 border-t border-border px-6 py-4">
            <Button
              type="button"
              variant="outline"
              onClick={onCancel}
              disabled={isSubmitting}
              data-testid="permission-set-form-cancel"
            >
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting} data-testid="permission-set-form-submit">
              {isSubmitting ? 'Saving...' : 'Save'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}

export function PermissionSetsPage({
  testId = 'permission-sets-page',
}: PermissionSetsPageProps): React.ReactElement {
  const { formatDate } = useI18n()
  const { apiClient } = useApi()
  const { tenantSlug } = useTenant()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { showToast } = useToast()

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingPermissionSet, setEditingPermissionSet] = useState<PermissionSet | undefined>(
    undefined
  )
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [permissionSetToDelete, setPermissionSetToDelete] = useState<PermissionSet | null>(null)

  const {
    data: permissionSets,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['permission-sets'],
    queryFn: () => apiClient.getList<PermissionSet>('/api/permission-sets'),
  })

  const permissionSetList = permissionSets ?? []

  // Mutations
  const createMutation = useMutation({
    mutationFn: (data: PermissionSetFormData) =>
      apiClient.postResource<PermissionSet>('/api/permission-sets', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['permission-sets'] })
      showToast('Permission set created successfully', 'success')
      handleCloseForm()
    },
    onError: (error: Error) => {
      showToast(error.message || 'Failed to create permission set', 'error')
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: PermissionSetFormData }) =>
      apiClient.putResource<PermissionSet>(`/api/permission-sets/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['permission-sets'] })
      showToast('Permission set updated successfully', 'success')
      handleCloseForm()
    },
    onError: (error: Error) => {
      showToast(error.message || 'Failed to update permission set', 'error')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.deleteResource(`/api/permission-sets/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['permission-sets'] })
      showToast('Permission set deleted successfully', 'success')
      setDeleteDialogOpen(false)
      setPermissionSetToDelete(null)
    },
    onError: (error: Error) => {
      showToast(error.message || 'Failed to delete permission set', 'error')
    },
  })

  const cloneMutation = useMutation({
    mutationFn: (id: string) =>
      apiClient.postResource<PermissionSet>(`/api/permission-sets/${id}/clone`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['permission-sets'] })
      showToast('Permission set cloned successfully', 'success')
    },
    onError: (error: Error) => {
      showToast(error.message || 'Failed to clone permission set', 'error')
    },
  })

  // Handlers
  const handleCreate = useCallback(() => {
    setEditingPermissionSet(undefined)
    setIsFormOpen(true)
  }, [])

  const handleEdit = useCallback((ps: PermissionSet) => {
    setEditingPermissionSet(ps)
    setIsFormOpen(true)
  }, [])

  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingPermissionSet(undefined)
  }, [])

  const handleFormSubmit = useCallback(
    (data: PermissionSetFormData) => {
      if (editingPermissionSet) {
        updateMutation.mutate({ id: editingPermissionSet.id, data })
      } else {
        createMutation.mutate(data)
      }
    },
    [editingPermissionSet, createMutation, updateMutation]
  )

  const handleDeleteClick = useCallback((ps: PermissionSet) => {
    setPermissionSetToDelete(ps)
    setDeleteDialogOpen(true)
  }, [])

  const handleDeleteConfirm = useCallback(() => {
    if (permissionSetToDelete) {
      deleteMutation.mutate(permissionSetToDelete.id)
    }
  }, [permissionSetToDelete, deleteMutation])

  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setPermissionSetToDelete(null)
  }, [])

  const handleClone = useCallback(
    (ps: PermissionSet) => {
      cloneMutation.mutate(ps.id)
    },
    [cloneMutation]
  )

  const handleRowClick = useCallback(
    (ps: PermissionSet) => {
      navigate(`/${tenantSlug}/permission-sets/${ps.id}`)
    },
    [navigate, tenantSlug]
  )

  if (isLoading) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <div className="flex min-h-[400px] items-center justify-center">
          <LoadingSpinner size="large" label="Loading..." />
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <ErrorMessage
          error={error instanceof Error ? error : new Error('Failed to load permission sets')}
          onRetry={() => refetch()}
        />
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
      <header className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Shield className="h-6 w-6 text-muted-foreground" />
          <h1 className="text-2xl font-semibold text-foreground">Permission Sets</h1>
        </div>
        <Button type="button" onClick={handleCreate} data-testid="new-permission-set-button">
          <Plus className="mr-2 h-4 w-4" />
          New Permission Set
        </Button>
      </header>

      {permissionSetList.length === 0 ? (
        <div
          className="rounded-lg border border-border bg-card py-16 text-center text-muted-foreground"
          data-testid="empty-state"
        >
          <Shield className="mx-auto mb-4 h-12 w-12 text-muted-foreground/50" />
          <p className="text-lg font-medium">No permission sets found</p>
          <p className="mt-1 text-sm">Create a permission set to manage access controls.</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border bg-card">
          <table
            className="w-full border-collapse text-sm"
            role="grid"
            aria-label="Permission Sets"
            data-testid="permission-sets-table"
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
                  Description
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  System
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
                  className="border-b border-border px-4 py-3 text-right text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Actions
                </th>
              </tr>
            </thead>
            <tbody>
              {permissionSetList.map((ps, index) => (
                <tr
                  key={ps.id}
                  role="row"
                  className="cursor-pointer border-b border-border transition-colors last:border-b-0 hover:bg-muted/50"
                  onClick={() => handleRowClick(ps)}
                  data-testid={`permission-set-row-${index}`}
                >
                  <td role="gridcell" className="px-4 py-3 font-medium text-foreground">
                    <div className="flex items-center gap-2">
                      {ps.system ? (
                        <Lock className="h-4 w-4 text-muted-foreground" />
                      ) : (
                        <Shield className="h-4 w-4 text-muted-foreground" />
                      )}
                      {ps.name}
                    </div>
                  </td>
                  <td
                    role="gridcell"
                    className="max-w-[400px] truncate px-4 py-3 text-muted-foreground"
                  >
                    {ps.description || '\u2014'}
                  </td>
                  <td role="gridcell" className="px-4 py-3">
                    {ps.system ? (
                      <span
                        className={cn(
                          'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium',
                          'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300'
                        )}
                      >
                        System
                      </span>
                    ) : (
                      <span className="text-sm text-muted-foreground">{'\u2014'}</span>
                    )}
                  </td>
                  <td role="gridcell" className="whitespace-nowrap px-4 py-3 text-muted-foreground">
                    {formatDate(new Date(ps.createdAt), {
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric',
                    })}
                  </td>
                  <td role="gridcell" className="w-[1%] whitespace-nowrap px-4 py-3">
                    <div
                      role="toolbar"
                      className="flex justify-end gap-2"
                      onClick={(e) => e.stopPropagation()}
                      onKeyDown={(e) => e.stopPropagation()}
                    >
                      {!ps.system && (
                        <button
                          type="button"
                          className="inline-flex items-center gap-1 rounded border border-border px-2 py-1 text-xs font-medium text-primary hover:border-primary hover:bg-muted"
                          onClick={() => handleEdit(ps)}
                          data-testid={`edit-button-${index}`}
                          title="Edit permission set"
                        >
                          <Pencil size={12} />
                          Edit
                        </button>
                      )}
                      <button
                        type="button"
                        className="inline-flex items-center gap-1 rounded border border-border px-2 py-1 text-xs font-medium text-primary hover:border-primary hover:bg-muted"
                        onClick={() => handleClone(ps)}
                        data-testid={`clone-button-${index}`}
                        title="Clone permission set"
                      >
                        <Copy size={12} />
                        Clone
                      </button>
                      {!ps.system && (
                        <button
                          type="button"
                          className="inline-flex items-center gap-1 rounded border border-border px-2 py-1 text-xs font-medium text-destructive hover:border-destructive hover:bg-destructive/10"
                          onClick={() => handleDeleteClick(ps)}
                          data-testid={`delete-button-${index}`}
                          title="Delete permission set"
                        >
                          <Trash2 size={12} />
                          Delete
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Create/Edit Modal */}
      {isFormOpen && (
        <PermissionSetFormModal
          permissionSet={editingPermissionSet}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={createMutation.isPending || updateMutation.isPending}
        />
      )}

      {/* Delete Confirmation Dialog */}
      <ConfirmDialog
        open={deleteDialogOpen}
        title="Delete Permission Set"
        message={`Are you sure you want to delete "${permissionSetToDelete?.name}"? This action cannot be undone.`}
        confirmLabel="Delete"
        cancelLabel="Cancel"
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  )
}

export default PermissionSetsPage
