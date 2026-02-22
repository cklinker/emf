/**
 * ProfileDetailPage Component
 *
 * Displays detailed information about a security profile including basic info,
 * system permissions (editable for non-system profiles), and object permissions (read-only).
 * Provides edit and delete functionality for non-system profiles.
 */

import React, { useState, useCallback, useMemo } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Shield, Lock, ArrowLeft, Trash2, Pencil, Save, X } from 'lucide-react'
import { useApi } from '../../context/ApiContext'
import { useTenant } from '../../context/TenantContext'
import { useI18n } from '../../context/I18nContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import { SystemPermissionChecklist } from '@/components/SecurityEditor'
import { ObjectPermissionMatrix } from '@/components/SecurityEditor'
import type { ObjectPermission } from '@/components/SecurityEditor'
import { useCollectionSummaries } from '@/hooks/useCollectionSummaries'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'

interface ProfileDetail {
  id: string
  name: string
  description: string | null
  system: boolean
  createdAt: string
  updatedAt: string
}

interface ProfileSystemPermission {
  id: string
  profileId: string
  permissionName: string
  granted: boolean
}

interface ProfileObjectPermission {
  id: string
  profileId: string
  collectionId: string
  canCreate: boolean
  canRead: boolean
  canEdit: boolean
  canDelete: boolean
  canViewAll: boolean
  canModifyAll: boolean
}

export interface ProfileDetailPageProps {
  testId?: string
}

export function ProfileDetailPage({
  testId = 'profile-detail-page',
}: ProfileDetailPageProps): React.ReactElement {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { apiClient } = useApi()
  const { tenantSlug } = useTenant()
  const { formatDate } = useI18n()
  const queryClient = useQueryClient()
  const { showToast } = useToast()

  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [isEditingPermissions, setIsEditingPermissions] = useState(false)
  const [localPermissions, setLocalPermissions] = useState<Record<string, boolean>>({})

  // Fetch profile detail
  const {
    data: profile,
    isLoading: isLoadingProfile,
    error: profileError,
    refetch: refetchProfile,
  } = useQuery({
    queryKey: ['profile', id],
    queryFn: () => apiClient.get<ProfileDetail>(`/control/profiles/${id}`),
    enabled: !!id,
  })

  // Fetch system permissions
  const {
    data: systemPermissions,
    isLoading: isLoadingSysPerms,
    error: sysPermsError,
  } = useQuery({
    queryKey: ['profile-system-permissions', id],
    queryFn: () =>
      apiClient.get<ProfileSystemPermission[]>(`/control/profiles/${id}/system-permissions`),
    enabled: !!id,
  })

  // Fetch object permissions
  const { data: objectPermissions } = useQuery({
    queryKey: ['profile-object-permissions', id],
    queryFn: () =>
      apiClient.get<ProfileObjectPermission[]>(`/control/profiles/${id}/object-permissions`),
    enabled: !!id,
  })

  // Fetch collections for name resolution
  const { summaryMap: collectionSummaryMap } = useCollectionSummaries()

  // Convert system permissions array → Record<string, boolean> for checklist
  const permissionsMap = useMemo(() => {
    const map: Record<string, boolean> = {}
    if (systemPermissions) {
      systemPermissions.forEach((sp) => {
        map[sp.permissionName] = sp.granted
      })
    }
    return map
  }, [systemPermissions])

  // The displayed permissions: use localPermissions when editing, permissionsMap when not
  const displayedPermissions = isEditingPermissions ? localPermissions : permissionsMap

  // Merge object permissions with collection names
  const objectPermissionsWithNames: ObjectPermission[] = useMemo(() => {
    if (!objectPermissions) return []
    return objectPermissions.map((op) => ({
      collectionId: op.collectionId,
      collectionName:
        collectionSummaryMap[op.collectionId]?.displayName ??
        collectionSummaryMap[op.collectionId]?.name ??
        op.collectionId,
      canCreate: op.canCreate,
      canRead: op.canRead,
      canEdit: op.canEdit,
      canDelete: op.canDelete,
      canViewAll: op.canViewAll,
      canModifyAll: op.canModifyAll,
    }))
  }, [objectPermissions, collectionSummaryMap])

  // Check if permissions have been modified
  const hasPermissionChanges = useMemo(() => {
    const keys = new Set([...Object.keys(permissionsMap), ...Object.keys(localPermissions)])
    for (const key of keys) {
      if ((permissionsMap[key] ?? false) !== (localPermissions[key] ?? false)) {
        return true
      }
    }
    return false
  }, [permissionsMap, localPermissions])

  // Save system permissions mutation
  const savePermissionsMutation = useMutation({
    mutationFn: (permissions: Record<string, boolean>) =>
      apiClient.put(`/control/profiles/${id}/system-permissions`, permissions),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile-system-permissions', id] })
      showToast('System permissions saved successfully', 'success')
      setIsEditingPermissions(false)
    },
    onError: (error: Error) => {
      showToast(error.message || 'Failed to save permissions', 'error')
    },
  })

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: () => apiClient.delete(`/control/profiles/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profiles'] })
      showToast('Profile deleted successfully', 'success')
      navigate(`/${tenantSlug}/profiles`)
    },
    onError: (error: Error) => {
      showToast(error.message || 'Failed to delete profile', 'error')
    },
  })

  // Handlers
  const handlePermissionChange = useCallback((name: string, granted: boolean) => {
    setLocalPermissions((prev) => ({ ...prev, [name]: granted }))
  }, [])

  const handleEditPermissions = useCallback(() => {
    setLocalPermissions(permissionsMap)
    setIsEditingPermissions(true)
  }, [permissionsMap])

  const handleCancelEdit = useCallback(() => {
    setLocalPermissions(permissionsMap)
    setIsEditingPermissions(false)
  }, [permissionsMap])

  const handleSavePermissions = useCallback(() => {
    savePermissionsMutation.mutate(localPermissions)
  }, [savePermissionsMutation, localPermissions])

  const handleDeleteClick = useCallback(() => {
    setDeleteDialogOpen(true)
  }, [])

  const handleDeleteConfirm = useCallback(() => {
    deleteMutation.mutate()
  }, [deleteMutation])

  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
  }, [])

  // Loading state
  const isLoading = isLoadingProfile || isLoadingSysPerms
  const error = profileError || sysPermsError

  if (isLoading) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <div className="flex min-h-[400px] items-center justify-center">
          <LoadingSpinner size="large" label="Loading profile..." />
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <ErrorMessage
          error={error instanceof Error ? error : new Error('Failed to load profile')}
          onRetry={() => refetchProfile()}
        />
      </div>
    )
  }

  if (!profile) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <div className="text-center text-muted-foreground">Profile not found.</div>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
      {/* Back link */}
      <Link
        to={`/${tenantSlug}/profiles`}
        className="inline-flex items-center gap-1 text-sm text-primary hover:underline"
        data-testid="back-link"
      >
        <ArrowLeft size={14} />
        Back to Profiles
      </Link>

      {/* Header */}
      <header className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          {profile.system ? (
            <Lock className="h-6 w-6 text-muted-foreground" />
          ) : (
            <Shield className="h-6 w-6 text-muted-foreground" />
          )}
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-semibold text-foreground">{profile.name}</h1>
              {profile.system && (
                <span
                  className={cn(
                    'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium',
                    'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300'
                  )}
                  data-testid="system-badge"
                >
                  System
                </span>
              )}
            </div>
            {profile.description && (
              <p className="mt-1 text-sm text-muted-foreground">{profile.description}</p>
            )}
          </div>
        </div>
        {!profile.system && (
          <div className="flex gap-2">
            <Button variant="destructive" onClick={handleDeleteClick} data-testid="delete-button">
              <Trash2 size={16} className="mr-1" />
              Delete
            </Button>
          </div>
        )}
      </header>

      {/* Metadata */}
      <div className="flex gap-6 text-sm text-muted-foreground">
        <span>
          Created:{' '}
          {formatDate(new Date(profile.createdAt), {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
          })}
        </span>
        <span>
          Updated:{' '}
          {formatDate(new Date(profile.updatedAt), {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
          })}
        </span>
      </div>

      {/* System Permissions */}
      <section data-testid="system-permissions-section">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-foreground">System Permissions</h2>
          {!profile.system && (
            <div className="flex gap-2">
              {isEditingPermissions ? (
                <>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={handleCancelEdit}
                    disabled={savePermissionsMutation.isPending}
                    data-testid="cancel-edit-button"
                  >
                    <X size={14} className="mr-1" />
                    Cancel
                  </Button>
                  <Button
                    size="sm"
                    onClick={handleSavePermissions}
                    disabled={!hasPermissionChanges || savePermissionsMutation.isPending}
                    data-testid="save-permissions-button"
                  >
                    <Save size={14} className="mr-1" />
                    {savePermissionsMutation.isPending ? 'Saving...' : 'Save'}
                  </Button>
                </>
              ) : (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={handleEditPermissions}
                  data-testid="edit-permissions-button"
                >
                  <Pencil size={14} className="mr-1" />
                  Edit Permissions
                </Button>
              )}
            </div>
          )}
        </div>
        <SystemPermissionChecklist
          permissions={displayedPermissions}
          onChange={handlePermissionChange}
          readOnly={profile.system || !isEditingPermissions}
          testId="system-permissions"
        />
      </section>

      {/* Object Permissions */}
      {objectPermissionsWithNames.length > 0 && (
        <section data-testid="object-permissions-section">
          <h2 className="mb-4 text-lg font-semibold text-foreground">Object Permissions</h2>
          <ObjectPermissionMatrix
            permissions={objectPermissionsWithNames}
            onChange={() => {}}
            readOnly={true}
            testId="object-permissions"
          />
        </section>
      )}

      {/* Delete Confirmation Dialog */}
      <ConfirmDialog
        open={deleteDialogOpen}
        title="Delete Profile"
        message={`Are you sure you want to delete "${profile.name}"? This action cannot be undone.`}
        confirmLabel="Delete"
        cancelLabel="Cancel"
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  )
}

export default ProfileDetailPage
