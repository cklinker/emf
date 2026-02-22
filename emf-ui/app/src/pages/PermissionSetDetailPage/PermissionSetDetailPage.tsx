/**
 * PermissionSetDetailPage Component
 *
 * Displays detailed information about a permission set including basic info,
 * system permissions (editable for non-system permission sets), object permissions,
 * and user/group assignments.
 * Provides edit, clone, and delete functionality for non-system permission sets.
 */

import React, { useState, useCallback, useMemo } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Shield, Lock, ArrowLeft, Trash2, Users, UserCircle, Pencil, Save, X } from 'lucide-react'
import { useApi } from '../../context/ApiContext'
import { useTenant } from '../../context/TenantContext'
import { useI18n } from '../../context/I18nContext'
import { useToast, ConfirmDialog, LoadingSpinner, ErrorMessage } from '../../components'
import { SystemPermissionChecklist } from '@/components/SecurityEditor'
import { ObjectPermissionMatrix } from '@/components/SecurityEditor'
import type { ObjectPermission } from '@/components/SecurityEditor'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'

interface PermsetSystemPermission {
  id: string
  permissionSetId: string
  permissionName: string
  granted: boolean
}

interface PermsetObjectPermission {
  id: string
  permissionSetId: string
  collectionId: string
  canCreate: boolean
  canRead: boolean
  canEdit: boolean
  canDelete: boolean
  canViewAll: boolean
  canModifyAll: boolean
}

interface PermissionSetDetail {
  id: string
  name: string
  description: string | null
  system: boolean
  createdAt: string
  updatedAt: string
}

interface UserAssignment {
  id: string
  userId: string
  permissionSetId: string
  createdAt: string
}

interface GroupAssignment {
  id: string
  groupId: string
  permissionSetId: string
  createdAt: string
}

interface Assignments {
  users: UserAssignment[]
  groups: GroupAssignment[]
}

interface CollectionSummary {
  id: string
  name: string
  displayName?: string
}

interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export interface PermissionSetDetailPageProps {
  testId?: string
}

export function PermissionSetDetailPage({
  testId = 'permission-set-detail-page',
}: PermissionSetDetailPageProps): React.ReactElement {
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

  // Fetch permission set detail
  const {
    data: permissionSet,
    isLoading: isLoadingPermSet,
    error: permSetError,
    refetch,
  } = useQuery({
    queryKey: ['permission-set', id],
    queryFn: () => apiClient.get<PermissionSetDetail>(`/control/permission-sets/${id}`),
    enabled: !!id,
  })

  // Fetch system permissions
  const {
    data: systemPermissions,
    isLoading: isLoadingSysPerms,
    error: sysPermsError,
  } = useQuery({
    queryKey: ['permission-set-system-permissions', id],
    queryFn: () =>
      apiClient.get<PermsetSystemPermission[]>(`/control/permission-sets/${id}/system-permissions`),
    enabled: !!id,
  })

  // Fetch object permissions
  const { data: objectPermissions } = useQuery({
    queryKey: ['permission-set-object-permissions', id],
    queryFn: () =>
      apiClient.get<PermsetObjectPermission[]>(`/control/permission-sets/${id}/object-permissions`),
    enabled: !!id,
  })

  // Fetch collections for name resolution
  const { data: collectionsData } = useQuery({
    queryKey: ['collections-summary'],
    queryFn: () => apiClient.get<PageResponse<CollectionSummary>>('/control/collections?size=1000'),
  })

  // Fetch assignments
  const { data: assignments } = useQuery({
    queryKey: ['permission-set-assignments', id],
    queryFn: () => apiClient.get<Assignments>(`/control/permission-sets/${id}/assignments`),
    enabled: !!id,
  })

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
    const collections = collectionsData?.content ?? []
    const collectionMap = new Map(collections.map((c) => [c.id, c.displayName || c.name]))
    return objectPermissions.map((op) => ({
      collectionId: op.collectionId,
      collectionName: collectionMap.get(op.collectionId) ?? op.collectionId,
      canCreate: op.canCreate,
      canRead: op.canRead,
      canEdit: op.canEdit,
      canDelete: op.canDelete,
      canViewAll: op.canViewAll,
      canModifyAll: op.canModifyAll,
    }))
  }, [objectPermissions, collectionsData])

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
      apiClient.put(`/control/permission-sets/${id}/system-permissions`, permissions),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['permission-set-system-permissions', id] })
      showToast('System permissions saved successfully', 'success')
      setIsEditingPermissions(false)
    },
    onError: (error: Error) => {
      showToast(error.message || 'Failed to save permissions', 'error')
    },
  })

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: () => apiClient.delete(`/control/permission-sets/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['permission-sets'] })
      showToast('Permission set deleted successfully', 'success')
      navigate(`/${tenantSlug}/permission-sets`)
    },
    onError: (error: Error) => {
      showToast(error.message || 'Failed to delete permission set', 'error')
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
  const isLoading = isLoadingPermSet || isLoadingSysPerms
  const error = permSetError || sysPermsError

  if (isLoading) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <div className="flex min-h-[400px] items-center justify-center">
          <LoadingSpinner size="large" label="Loading permission set..." />
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <ErrorMessage
          error={error instanceof Error ? error : new Error('Failed to load permission set')}
          onRetry={() => refetch()}
        />
      </div>
    )
  }

  if (!permissionSet) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <div className="text-center text-muted-foreground">Permission set not found.</div>
      </div>
    )
  }

  const userAssignments = assignments?.users ?? []
  const groupAssignments = assignments?.groups ?? []

  return (
    <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
      {/* Back link */}
      <Link
        to={`/${tenantSlug}/permission-sets`}
        className="inline-flex items-center gap-1 text-sm text-primary hover:underline"
        data-testid="back-link"
      >
        <ArrowLeft size={14} />
        Back to Permission Sets
      </Link>

      {/* Header */}
      <header className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          {permissionSet.system ? (
            <Lock className="h-6 w-6 text-muted-foreground" />
          ) : (
            <Shield className="h-6 w-6 text-muted-foreground" />
          )}
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-semibold text-foreground">{permissionSet.name}</h1>
              {permissionSet.system && (
                <span
                  className={cn(
                    'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium',
                    'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300'
                  )}
                >
                  System
                </span>
              )}
            </div>
            {permissionSet.description && (
              <p className="mt-1 text-sm text-muted-foreground">{permissionSet.description}</p>
            )}
          </div>
        </div>
        {!permissionSet.system && (
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
          {formatDate(new Date(permissionSet.createdAt), {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
          })}
        </span>
        <span>
          Updated:{' '}
          {formatDate(new Date(permissionSet.updatedAt), {
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
          {!permissionSet.system && (
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
          readOnly={permissionSet.system || !isEditingPermissions}
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

      {/* Assignments */}
      <section data-testid="assignments-section">
        <h2 className="mb-4 text-lg font-semibold text-foreground">Assignments</h2>

        <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
          {/* User Assignments */}
          <div className="rounded-lg border border-border bg-card">
            <div className="flex items-center gap-2 border-b border-border bg-muted px-4 py-3">
              <UserCircle size={16} className="text-muted-foreground" />
              <h3 className="text-sm font-semibold text-foreground">
                Assigned Users ({userAssignments.length})
              </h3>
            </div>
            <div className="px-4 py-3">
              {userAssignments.length === 0 ? (
                <p className="text-sm text-muted-foreground" data-testid="no-user-assignments">
                  No users assigned to this permission set.
                </p>
              ) : (
                <ul className="space-y-2" data-testid="user-assignments-list">
                  {userAssignments.map((ua) => (
                    <li
                      key={ua.id}
                      className="flex items-center justify-between text-sm text-foreground"
                    >
                      <span className="font-mono text-xs">{ua.userId}</span>
                      <span className="text-xs text-muted-foreground">
                        {formatDate(new Date(ua.createdAt), {
                          year: 'numeric',
                          month: 'short',
                          day: 'numeric',
                        })}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>

          {/* Group Assignments */}
          <div className="rounded-lg border border-border bg-card">
            <div className="flex items-center gap-2 border-b border-border bg-muted px-4 py-3">
              <Users size={16} className="text-muted-foreground" />
              <h3 className="text-sm font-semibold text-foreground">
                Assigned Groups ({groupAssignments.length})
              </h3>
            </div>
            <div className="px-4 py-3">
              {groupAssignments.length === 0 ? (
                <p className="text-sm text-muted-foreground" data-testid="no-group-assignments">
                  No groups assigned to this permission set.
                </p>
              ) : (
                <ul className="space-y-2" data-testid="group-assignments-list">
                  {groupAssignments.map((ga) => (
                    <li
                      key={ga.id}
                      className="flex items-center justify-between text-sm text-foreground"
                    >
                      <span className="font-mono text-xs">{ga.groupId}</span>
                      <span className="text-xs text-muted-foreground">
                        {formatDate(new Date(ga.createdAt), {
                          year: 'numeric',
                          month: 'short',
                          day: 'numeric',
                        })}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        </div>
      </section>

      {/* Delete Confirmation Dialog */}
      <ConfirmDialog
        open={deleteDialogOpen}
        title="Delete Permission Set"
        message={`Are you sure you want to delete "${permissionSet.name}"? This action cannot be undone.`}
        confirmLabel="Delete"
        cancelLabel="Cancel"
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  )
}

export default PermissionSetDetailPage
