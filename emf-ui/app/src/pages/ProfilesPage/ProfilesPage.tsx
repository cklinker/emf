/**
 * ProfilesPage Component
 *
 * Displays a list of security profiles with create, edit, clone, and delete
 * functionality. System profiles can only be cloned (not edited or deleted).
 * Uses TanStack Query for data fetching and modal forms for management.
 */

import React, { useState, useCallback, useEffect, useRef, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useApi } from '../../context/ApiContext'
import { useI18n } from '../../context/I18nContext'
import { useToast, ConfirmDialog } from '../../components'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Lock, ChevronDown, ChevronRight, Plus, Shield, Pencil, Copy, Trash2 } from 'lucide-react'

interface ObjectPermission {
  objectName: string
  canRead: boolean
  canCreate: boolean
  canEdit: boolean
  canDelete: boolean
}

interface ProfileSystemPermission {
  id: string
  profileId: string
  permissionName: string
  granted: boolean
}

interface SecurityProfile {
  id: string
  name: string
  description: string | null
  system: boolean
  systemPermissions: ProfileSystemPermission[]
  objectPermissions: ObjectPermission[]
  createdAt: string
  updatedAt: string
}

interface ProfileFormData {
  name: string
  description: string
}

interface ProfileFormErrors {
  name?: string
}

export interface ProfilesPageProps {
  testId?: string
}

function validateProfileForm(data: ProfileFormData): ProfileFormErrors {
  const errors: ProfileFormErrors = {}
  if (!data.name.trim()) {
    errors.name = 'Profile name is required'
  } else if (data.name.length > 255) {
    errors.name = 'Name must be 255 characters or fewer'
  }
  return errors
}

function SystemBadge() {
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800 dark:bg-amber-950 dark:text-amber-300">
      <Lock size={12} />
      System
    </span>
  )
}

function ProfileDetail({ profile }: { profile: SecurityProfile }) {
  const grantedPermissions = useMemo(() => {
    if (!profile.systemPermissions) return []
    return profile.systemPermissions.filter((sp) => sp.granted).map((sp) => sp.permissionName)
  }, [profile.systemPermissions])

  return (
    <div className="border-t border-border bg-muted/30 px-6 py-4">
      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        {/* System Permissions */}
        <div>
          <h4 className="mb-2 text-sm font-semibold text-foreground">System Permissions</h4>
          {grantedPermissions.length === 0 ? (
            <p className="text-sm text-muted-foreground">No system permissions assigned</p>
          ) : (
            <ul className="space-y-1">
              {grantedPermissions.map((perm) => (
                <li key={perm} className="flex items-center gap-2 text-sm text-foreground">
                  <Shield size={14} className="text-primary" />
                  {perm}
                </li>
              ))}
            </ul>
          )}
        </div>

        {/* Object Permissions */}
        <div>
          <h4 className="mb-2 text-sm font-semibold text-foreground">Object Permissions</h4>
          {profile.objectPermissions.length === 0 ? (
            <p className="text-sm text-muted-foreground">No object permissions assigned</p>
          ) : (
            <div className="overflow-x-auto rounded border border-border">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-muted">
                    <th className="px-3 py-1.5 text-left text-xs font-medium text-muted-foreground">
                      Object
                    </th>
                    <th className="px-3 py-1.5 text-center text-xs font-medium text-muted-foreground">
                      Read
                    </th>
                    <th className="px-3 py-1.5 text-center text-xs font-medium text-muted-foreground">
                      Create
                    </th>
                    <th className="px-3 py-1.5 text-center text-xs font-medium text-muted-foreground">
                      Edit
                    </th>
                    <th className="px-3 py-1.5 text-center text-xs font-medium text-muted-foreground">
                      Delete
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {profile.objectPermissions.map((op) => (
                    <tr key={op.objectName} className="border-t border-border">
                      <td className="px-3 py-1.5 font-medium text-foreground">{op.objectName}</td>
                      <td className="px-3 py-1.5 text-center">
                        {op.canRead ? (
                          <span className="text-emerald-600">Yes</span>
                        ) : (
                          <span className="text-muted-foreground">No</span>
                        )}
                      </td>
                      <td className="px-3 py-1.5 text-center">
                        {op.canCreate ? (
                          <span className="text-emerald-600">Yes</span>
                        ) : (
                          <span className="text-muted-foreground">No</span>
                        )}
                      </td>
                      <td className="px-3 py-1.5 text-center">
                        {op.canEdit ? (
                          <span className="text-emerald-600">Yes</span>
                        ) : (
                          <span className="text-muted-foreground">No</span>
                        )}
                      </td>
                      <td className="px-3 py-1.5 text-center">
                        {op.canDelete ? (
                          <span className="text-emerald-600">Yes</span>
                        ) : (
                          <span className="text-muted-foreground">No</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

interface ProfileFormModalProps {
  profile?: SecurityProfile
  onSubmit: (data: ProfileFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function ProfileFormModal({ profile, onSubmit, onCancel, isSubmitting }: ProfileFormModalProps) {
  const nameInputRef = useRef<HTMLInputElement>(null)
  const [formData, setFormData] = useState<ProfileFormData>({
    name: profile?.name ?? '',
    description: profile?.description ?? '',
  })
  const [errors, setErrors] = useState<ProfileFormErrors>({})
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
    (data: ProfileFormData): ProfileFormErrors => validateProfileForm(data),
    []
  )

  const handleChange = useCallback(
    (field: keyof ProfileFormData, value: string) => {
      const updated = { ...formData, [field]: value }
      setFormData(updated)
      if (touched[field]) {
        const newErrors = validate(updated)
        setErrors((prev) => ({ ...prev, [field]: newErrors[field as keyof ProfileFormErrors] }))
      }
    },
    [formData, touched, validate]
  )

  const handleBlur = useCallback(
    (field: keyof ProfileFormData) => {
      setTouched((prev) => ({ ...prev, [field]: true }))
      const newErrors = validate(formData)
      setErrors((prev) => ({ ...prev, [field]: newErrors[field as keyof ProfileFormErrors] }))
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

  const inputClassName = (field: keyof ProfileFormErrors) =>
    cn(
      'w-full rounded-md border bg-background px-3 py-2 text-sm',
      touched[field] && errors[field] ? 'border-destructive' : 'border-border'
    )

  return (
    <div
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/50"
      role="dialog"
      aria-modal="true"
      aria-labelledby="profile-form-title"
      data-testid="profile-form-modal"
    >
      <div className="w-full max-w-[480px] rounded-lg border border-border bg-card shadow-xl">
        <div className="flex items-center justify-between border-b border-border px-6 py-4">
          <h2 id="profile-form-title" className="text-lg font-semibold text-foreground">
            {profile ? 'Edit Profile' : 'New Profile'}
          </h2>
          <button
            type="button"
            className="rounded p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
            onClick={onCancel}
            aria-label="Close"
            data-testid="profile-form-close"
          >
            ×
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="flex flex-col gap-4 px-6 py-4">
            <div className="flex flex-col gap-1">
              <label htmlFor="profile-name" className="text-sm font-medium text-foreground">
                Name <span className="text-destructive">*</span>
              </label>
              <input
                ref={nameInputRef}
                id="profile-name"
                type="text"
                className={inputClassName('name')}
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                onBlur={() => handleBlur('name')}
                aria-invalid={touched.name && !!errors.name}
                aria-describedby={errors.name ? 'profile-name-error' : undefined}
                disabled={isSubmitting}
                placeholder="Enter profile name"
                data-testid="profile-name-input"
              />
              {touched.name && errors.name && (
                <span
                  id="profile-name-error"
                  className="text-xs text-destructive"
                  role="alert"
                  data-testid="profile-name-error"
                >
                  {errors.name}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-1">
              <label htmlFor="profile-description" className="text-sm font-medium text-foreground">
                Description
              </label>
              <textarea
                id="profile-description"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                disabled={isSubmitting}
                placeholder="Enter profile description"
                rows={3}
                data-testid="profile-description-input"
              />
            </div>
          </div>

          <div className="flex justify-end gap-3 border-t border-border px-6 py-4">
            <Button
              type="button"
              variant="outline"
              onClick={onCancel}
              disabled={isSubmitting}
              data-testid="profile-form-cancel"
            >
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting} data-testid="profile-form-submit">
              {isSubmitting ? 'Saving...' : 'Save'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}

export function ProfilesPage({ testId = 'profiles-page' }: ProfilesPageProps): React.ReactElement {
  const { apiClient } = useApi()
  const { formatDate } = useI18n()
  const queryClient = useQueryClient()
  const { showToast } = useToast()

  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingProfile, setEditingProfile] = useState<SecurityProfile | undefined>(undefined)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [profileToDelete, setProfileToDelete] = useState<SecurityProfile | null>(null)

  const {
    data: profiles,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['profiles'],
    queryFn: () => apiClient.get<SecurityProfile[]>('/control/profiles'),
  })

  const profileList = profiles ?? []

  // Mutations
  const createMutation = useMutation({
    mutationFn: (data: ProfileFormData) =>
      apiClient.post<SecurityProfile>('/control/profiles', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profiles'] })
      showToast('Profile created successfully', 'success')
      handleCloseForm()
    },
    onError: (error: Error) => {
      showToast(error.message || 'Failed to create profile', 'error')
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: ProfileFormData }) =>
      apiClient.put<SecurityProfile>(`/control/profiles/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profiles'] })
      showToast('Profile updated successfully', 'success')
      handleCloseForm()
    },
    onError: (error: Error) => {
      showToast(error.message || 'Failed to update profile', 'error')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/control/profiles/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profiles'] })
      showToast('Profile deleted successfully', 'success')
      setDeleteDialogOpen(false)
      setProfileToDelete(null)
      if (profileToDelete && expandedId === profileToDelete.id) {
        setExpandedId(null)
      }
    },
    onError: (error: Error) => {
      showToast(error.message || 'Failed to delete profile', 'error')
    },
  })

  const cloneMutation = useMutation({
    mutationFn: (id: string) =>
      apiClient.post<SecurityProfile>(`/control/profiles/${id}/clone`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profiles'] })
      showToast('Profile cloned successfully', 'success')
    },
    onError: (error: Error) => {
      showToast(error.message || 'Failed to clone profile', 'error')
    },
  })

  // Handlers
  const handleToggleExpand = useCallback((id: string) => {
    setExpandedId((prev) => (prev === id ? null : id))
  }, [])

  const handleCreate = useCallback(() => {
    setEditingProfile(undefined)
    setIsFormOpen(true)
  }, [])

  const handleEdit = useCallback((profile: SecurityProfile) => {
    setEditingProfile(profile)
    setIsFormOpen(true)
  }, [])

  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingProfile(undefined)
  }, [])

  const handleFormSubmit = useCallback(
    (data: ProfileFormData) => {
      if (editingProfile) {
        updateMutation.mutate({ id: editingProfile.id, data })
      } else {
        createMutation.mutate(data)
      }
    },
    [editingProfile, createMutation, updateMutation]
  )

  const handleDeleteClick = useCallback((profile: SecurityProfile) => {
    setProfileToDelete(profile)
    setDeleteDialogOpen(true)
  }, [])

  const handleDeleteConfirm = useCallback(() => {
    if (profileToDelete) {
      deleteMutation.mutate(profileToDelete.id)
    }
  }, [profileToDelete, deleteMutation])

  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setProfileToDelete(null)
  }, [])

  const handleClone = useCallback(
    (profile: SecurityProfile) => {
      cloneMutation.mutate(profile.id)
    },
    [cloneMutation]
  )

  if (isLoading) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <div className="flex min-h-[400px] items-center justify-center text-muted-foreground">
          Loading...
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <div className="flex flex-col items-center justify-center gap-4 p-12 text-muted-foreground">
          <p>Failed to load profiles.</p>
          <Button variant="outline" onClick={() => refetch()}>
            Retry
          </Button>
        </div>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-foreground">Profiles</h1>
        <Button onClick={handleCreate} data-testid="new-profile-button">
          <Plus size={16} />
          New Profile
        </Button>
      </header>

      {profileList.length === 0 ? (
        <div
          className="rounded-lg border border-border bg-card py-16 text-center text-muted-foreground"
          data-testid="empty-state"
        >
          <p>No profiles found. Create your first profile to get started.</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border bg-card">
          <table
            className="w-full border-collapse"
            role="grid"
            aria-label="Profiles"
            data-testid="profiles-table"
          >
            <thead>
              <tr role="row" className="bg-muted">
                <th
                  role="columnheader"
                  scope="col"
                  className="w-8 border-b border-border px-4 py-3"
                >
                  <span className="sr-only">Expand</span>
                </th>
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
              {profileList.map((profile, index) => {
                const isExpanded = expandedId === profile.id
                return (
                  <React.Fragment key={profile.id}>
                    <tr
                      role="row"
                      className={cn(
                        'cursor-pointer border-b border-border transition-colors hover:bg-muted/50',
                        isExpanded && 'bg-muted/30'
                      )}
                      onClick={() => handleToggleExpand(profile.id)}
                      data-testid={`profile-row-${index}`}
                    >
                      <td role="gridcell" className="px-4 py-3 text-muted-foreground">
                        {isExpanded ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                      </td>
                      <td role="gridcell" className="px-4 py-3 text-sm font-medium text-foreground">
                        {profile.name}
                      </td>
                      <td
                        role="gridcell"
                        className="max-w-[300px] truncate px-4 py-3 text-sm text-muted-foreground"
                      >
                        {profile.description || '\u2014'}
                      </td>
                      <td role="gridcell" className="px-4 py-3 text-sm">
                        {profile.system && <SystemBadge />}
                      </td>
                      <td role="gridcell" className="px-4 py-3 text-sm text-muted-foreground">
                        {formatDate(new Date(profile.createdAt), {
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
                          {!profile.system && (
                            <button
                              type="button"
                              className="inline-flex items-center gap-1 rounded border border-border px-2 py-1 text-xs font-medium text-primary hover:border-primary hover:bg-muted"
                              onClick={() => handleEdit(profile)}
                              data-testid={`edit-button-${index}`}
                              title="Edit profile"
                            >
                              <Pencil size={12} />
                              Edit
                            </button>
                          )}
                          <button
                            type="button"
                            className="inline-flex items-center gap-1 rounded border border-border px-2 py-1 text-xs font-medium text-primary hover:border-primary hover:bg-muted"
                            onClick={() => handleClone(profile)}
                            data-testid={`clone-button-${index}`}
                            title="Clone profile"
                          >
                            <Copy size={12} />
                            Clone
                          </button>
                          {!profile.system && (
                            <button
                              type="button"
                              className="inline-flex items-center gap-1 rounded border border-border px-2 py-1 text-xs font-medium text-destructive hover:border-destructive hover:bg-destructive/10"
                              onClick={() => handleDeleteClick(profile)}
                              data-testid={`delete-button-${index}`}
                              title="Delete profile"
                            >
                              <Trash2 size={12} />
                              Delete
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                    {isExpanded && (
                      <tr>
                        <td colSpan={6} className="p-0">
                          <ProfileDetail profile={profile} />
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Create/Edit Modal */}
      {isFormOpen && (
        <ProfileFormModal
          profile={editingProfile}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={createMutation.isPending || updateMutation.isPending}
        />
      )}

      {/* Delete Confirmation Dialog */}
      <ConfirmDialog
        open={deleteDialogOpen}
        title="Delete Profile"
        message={`Are you sure you want to delete "${profileToDelete?.name}"? This action cannot be undone.`}
        confirmLabel="Delete"
        cancelLabel="Cancel"
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  )
}

export default ProfilesPage
