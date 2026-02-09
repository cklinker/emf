import React, { useState, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useApi } from '../../context/ApiContext'
import { useI18n } from '../../context/I18nContext'
import { useToast } from '../../components/Toast'
import { ConfirmDialog } from '../../components'
import styles from './PermissionSetsPage.module.css'

interface PermissionSet {
  id: string
  name: string
  description?: string
  system: boolean
  objectPermissions?: ObjectPermission[]
  systemPermissions?: SystemPermissionEntry[]
  createdAt: string
  updatedAt: string
}

interface ObjectPermission {
  collectionId: string
  canCreate: boolean
  canRead: boolean
  canEdit: boolean
  canDelete: boolean
  canViewAll: boolean
  canModifyAll: boolean
}

interface SystemPermissionEntry {
  permissionKey: string
  granted: boolean
}

interface Collection {
  id: string
  name: string
  displayName: string
}

interface AssignedUser {
  id: string
  email: string
  firstName: string | null
  lastName: string | null
  username: string | null
  status: string
}

const SYSTEM_PERMISSION_KEYS = [
  { key: 'MANAGE_USERS', label: 'Manage Users' },
  { key: 'CUSTOMIZE_APPLICATION', label: 'Customize Application' },
  { key: 'MANAGE_SHARING', label: 'Manage Sharing' },
  { key: 'MANAGE_WORKFLOWS', label: 'Manage Workflows' },
  { key: 'MANAGE_REPORTS', label: 'Manage Reports' },
  { key: 'API_ACCESS', label: 'API Access' },
  { key: 'MANAGE_INTEGRATIONS', label: 'Manage Integrations' },
  { key: 'MANAGE_DATA', label: 'Manage Data' },
  { key: 'VIEW_SETUP', label: 'View Setup' },
  { key: 'MANAGE_SANDBOX', label: 'Manage Sandbox' },
  { key: 'VIEW_ALL_DATA', label: 'View All Data' },
  { key: 'MODIFY_ALL_DATA', label: 'Modify All Data' },
]

export interface PermissionSetsPageProps {
  testId?: string
}

function CreateForm({
  onSubmit,
  onCancel,
  isSubmitting,
}: {
  onSubmit: (data: { name: string; description: string }) => void
  onCancel: () => void
  isSubmitting: boolean
}) {
  const { t } = useI18n()
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')

  return (
    <div
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={(e) => {
        if (e.key === 'Escape') onCancel()
      }}
      role="presentation"
    >
      <div className={styles.modal} role="dialog" aria-modal="true">
        <h2>{t('permissionSets.create')}</h2>
        <form
          onSubmit={(e) => {
            e.preventDefault()
            if (name.trim()) onSubmit({ name: name.trim(), description: description.trim() })
          }}
        >
          <div className={styles.formGroup}>
            <label htmlFor="psName">{t('permissionSets.name')} *</label>
            <input
              id="psName"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
          </div>
          <div className={styles.formGroup}>
            <label htmlFor="psDesc">{t('permissionSets.description')}</label>
            <textarea
              id="psDesc"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={3}
            />
          </div>
          <div className={styles.formActions}>
            <button type="button" className={styles.btnSecondary} onClick={onCancel}>
              {t('common.cancel')}
            </button>
            <button type="submit" className={styles.btnPrimary} disabled={isSubmitting}>
              {isSubmitting ? t('common.saving') : t('common.create')}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export function PermissionSetsPage({ testId = 'permission-sets-page' }: PermissionSetsPageProps) {
  const queryClient = useQueryClient()
  const { t } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [selectedSet, setSelectedSet] = useState<PermissionSet | null>(null)
  const [activeTab, setActiveTab] = useState<'object' | 'system' | 'users'>('object')
  const [assignUserId, setAssignUserId] = useState('')
  const [unassignDialogOpen, setUnassignDialogOpen] = useState(false)
  const [userToUnassign, setUserToUnassign] = useState<AssignedUser | null>(null)

  const { data: permSets, isLoading } = useQuery({
    queryKey: ['permission-sets'],
    queryFn: () => apiClient.get<PermissionSet[]>('/control/permission-sets'),
  })

  const { data: collections } = useQuery({
    queryKey: ['collections'],
    queryFn: () => apiClient.get<Collection[]>('/control/collections'),
  })

  const { data: setDetail } = useQuery({
    queryKey: ['permission-sets', selectedSet?.id],
    queryFn: () => apiClient.get<PermissionSet>(`/control/permission-sets/${selectedSet?.id}`),
    enabled: !!selectedSet?.id,
  })

  const { data: assignedUsers } = useQuery({
    queryKey: ['permission-set-users', selectedSet?.id],
    queryFn: () =>
      apiClient.get<AssignedUser[]>(`/control/permission-sets/${selectedSet?.id}/assigned-users`),
    enabled: !!selectedSet?.id && activeTab === 'users',
  })

  const { data: allUsersResponse } = useQuery({
    queryKey: ['users-for-assignment'],
    queryFn: () =>
      apiClient.get<{ content: AssignedUser[] } | AssignedUser[]>('/control/users?size=1000'),
    enabled: activeTab === 'users',
  })

  const allUsers: AssignedUser[] = Array.isArray(allUsersResponse)
    ? allUsersResponse
    : (allUsersResponse?.content ?? [])

  const assignMutation = useMutation({
    mutationFn: async (userId: string) => {
      const resp = await apiClient.fetch(
        `/control/permission-sets/${selectedSet?.id}/assign/${userId}`,
        { method: 'POST' }
      )
      if (!resp.ok) throw new Error(`API request failed: ${resp.statusText}`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['permission-set-users', selectedSet?.id] })
      showToast(t('permissionSets.userAssigned'), 'success')
      setAssignUserId('')
    },
    onError: (err: Error) => {
      showToast(err.message || t('errors.generic'), 'error')
    },
  })

  const unassignMutation = useMutation({
    mutationFn: (userId: string) =>
      apiClient.delete(`/control/permission-sets/${selectedSet?.id}/assign/${userId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['permission-set-users', selectedSet?.id] })
      showToast(t('permissionSets.userUnassigned'), 'success')
      setUnassignDialogOpen(false)
      setUserToUnassign(null)
    },
    onError: (err: Error) => {
      showToast(err.message || t('errors.generic'), 'error')
    },
  })

  const createMutation = useMutation({
    mutationFn: (data: { name: string; description: string }) =>
      apiClient.post<PermissionSet>('/control/permission-sets', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['permission-sets'] })
      showToast(t('permissionSets.createSuccess'), 'success')
      setIsFormOpen(false)
    },
    onError: (err: Error) => {
      showToast(err.message || t('errors.generic'), 'error')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/control/permission-sets/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['permission-sets'] })
      showToast(t('permissionSets.deleteSuccess'), 'success')
      setSelectedSet(null)
    },
    onError: (err: Error) => {
      showToast(err.message || t('errors.generic'), 'error')
    },
  })

  const setObjectPermsMutation = useMutation({
    mutationFn: ({
      psId,
      collectionId,
      perms,
    }: {
      psId: string
      collectionId: string
      perms: ObjectPermission
    }) =>
      apiClient.put(`/control/permission-sets/${psId}/object-permissions/${collectionId}`, perms),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['permission-sets', selectedSet?.id] })
      showToast(t('permissionSets.permissionsSaved'), 'success')
    },
    onError: (err: Error) => {
      showToast(err.message || t('errors.generic'), 'error')
    },
  })

  const setSystemPermsMutation = useMutation({
    mutationFn: ({
      psId,
      perms,
    }: {
      psId: string
      perms: { permissionKey: string; granted: boolean }[]
    }) => apiClient.put(`/control/permission-sets/${psId}/system-permissions`, perms),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['permission-sets', selectedSet?.id] })
      showToast(t('permissionSets.permissionsSaved'), 'success')
    },
    onError: (err: Error) => {
      showToast(err.message || t('errors.generic'), 'error')
    },
  })

  const handleToggleObjectPerm = useCallback(
    (collectionId: string, field: keyof ObjectPermission, current: boolean) => {
      if (!selectedSet) return
      const existing = setDetail?.objectPermissions?.find((p) => p.collectionId === collectionId)
      const perms: ObjectPermission = {
        collectionId,
        canCreate: existing?.canCreate ?? false,
        canRead: existing?.canRead ?? false,
        canEdit: existing?.canEdit ?? false,
        canDelete: existing?.canDelete ?? false,
        canViewAll: existing?.canViewAll ?? false,
        canModifyAll: existing?.canModifyAll ?? false,
        [field]: !current,
      }
      setObjectPermsMutation.mutate({ psId: selectedSet.id, collectionId, perms })
    },
    [selectedSet, setDetail, setObjectPermsMutation]
  )

  const handleToggleSystemPerm = useCallback(
    (permissionKey: string, currentlyGranted: boolean) => {
      if (!selectedSet) return
      setSystemPermsMutation.mutate({
        psId: selectedSet.id,
        perms: [{ permissionKey, granted: !currentlyGranted }],
      })
    },
    [selectedSet, setSystemPermsMutation]
  )

  const setList = permSets ?? []
  const collectionList = collections ?? []

  return (
    <div className={styles.container} data-testid={testId}>
      <header className={styles.header}>
        <h1>{t('permissionSets.title')}</h1>
        <button className={styles.btnPrimary} onClick={() => setIsFormOpen(true)}>
          {t('permissionSets.create')}
        </button>
      </header>

      <div className={styles.layout}>
        <div className={styles.sidebar}>
          {isLoading ? (
            <div className={styles.loadingState}>{t('common.loading')}</div>
          ) : setList.length === 0 ? (
            <div className={styles.emptyState}>{t('permissionSets.noSets')}</div>
          ) : (
            <ul className={styles.itemList}>
              {setList.map((ps) => (
                <li key={ps.id}>
                  <button
                    className={`${styles.listItem} ${selectedSet?.id === ps.id ? styles.listItemActive : ''}`}
                    onClick={() => setSelectedSet(ps)}
                  >
                    <span className={styles.itemName}>{ps.name}</span>
                    {ps.system && <span className={styles.systemBadge}>System</span>}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className={styles.content}>
          {!selectedSet ? (
            <div className={styles.emptyState}>{t('permissionSets.selectSet')}</div>
          ) : (
            <>
              <div className={styles.itemHeader}>
                <div>
                  <h2>{selectedSet.name}</h2>
                  {selectedSet.description && <p>{selectedSet.description}</p>}
                </div>
                {!selectedSet.system && (
                  <button
                    className={styles.btnDanger}
                    onClick={() => deleteMutation.mutate(selectedSet.id)}
                    disabled={deleteMutation.isPending}
                  >
                    {t('common.delete')}
                  </button>
                )}
              </div>

              <div className={styles.tabs}>
                <button
                  className={`${styles.tab} ${activeTab === 'object' ? styles.tabActive : ''}`}
                  onClick={() => setActiveTab('object')}
                >
                  {t('profiles.objectPermissions')}
                </button>
                <button
                  className={`${styles.tab} ${activeTab === 'system' ? styles.tabActive : ''}`}
                  onClick={() => setActiveTab('system')}
                >
                  {t('profiles.systemPermissions')}
                </button>
                <button
                  className={`${styles.tab} ${activeTab === 'users' ? styles.tabActive : ''}`}
                  onClick={() => setActiveTab('users')}
                >
                  {t('permissionSets.assignedUsers')}
                </button>
              </div>

              {activeTab === 'object' && (
                <div className={styles.card}>
                  {collectionList.length === 0 ? (
                    <p>{t('profiles.noCollections')}</p>
                  ) : (
                    <table className={styles.table}>
                      <thead>
                        <tr>
                          <th>{t('profiles.collection')}</th>
                          <th>{t('profiles.create')}</th>
                          <th>{t('profiles.read')}</th>
                          <th>{t('profiles.edit')}</th>
                          <th>{t('profiles.delete')}</th>
                          <th>{t('profiles.viewAll')}</th>
                          <th>{t('profiles.modifyAll')}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {collectionList.map((col) => {
                          const perm = setDetail?.objectPermissions?.find(
                            (p) => p.collectionId === col.id
                          )
                          return (
                            <tr key={col.id}>
                              <td>{col.displayName || col.name}</td>
                              {(
                                [
                                  'canCreate',
                                  'canRead',
                                  'canEdit',
                                  'canDelete',
                                  'canViewAll',
                                  'canModifyAll',
                                ] as const
                              ).map((field) => (
                                <td key={field}>
                                  <input
                                    type="checkbox"
                                    checked={perm?.[field] ?? false}
                                    onChange={() =>
                                      handleToggleObjectPerm(col.id, field, perm?.[field] ?? false)
                                    }
                                  />
                                </td>
                              ))}
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  )}
                </div>
              )}

              {activeTab === 'system' && (
                <div className={styles.card}>
                  <div className={styles.permissionList}>
                    {SYSTEM_PERMISSION_KEYS.map(({ key, label }) => {
                      const perm = setDetail?.systemPermissions?.find(
                        (p) => p.permissionKey === key
                      )
                      const granted = perm?.granted ?? false
                      return (
                        <label key={key} className={styles.permissionItem}>
                          <input
                            type="checkbox"
                            checked={granted}
                            onChange={() => handleToggleSystemPerm(key, granted)}
                          />
                          <span>{label}</span>
                        </label>
                      )
                    })}
                  </div>
                </div>
              )}

              {activeTab === 'users' && (
                <div className={styles.card}>
                  <div className={styles.assignForm}>
                    <select
                      className={styles.assignSelect}
                      value={assignUserId}
                      onChange={(e) => setAssignUserId(e.target.value)}
                    >
                      <option value="">{t('permissionSets.selectUser')}</option>
                      {allUsers
                        .filter((u) => !(assignedUsers ?? []).some((au) => au.id === u.id))
                        .map((u) => (
                          <option key={u.id} value={u.id}>
                            {u.firstName && u.lastName
                              ? `${u.firstName} ${u.lastName} (${u.email})`
                              : u.email}
                          </option>
                        ))}
                    </select>
                    <button
                      className={styles.btnPrimary}
                      onClick={() => {
                        if (assignUserId) assignMutation.mutate(assignUserId)
                      }}
                      disabled={!assignUserId || assignMutation.isPending}
                    >
                      {t('permissionSets.assign')}
                    </button>
                  </div>

                  {(assignedUsers ?? []).length === 0 ? (
                    <p className={styles.emptyState}>{t('permissionSets.noAssignedUsers')}</p>
                  ) : (
                    <table className={styles.table}>
                      <thead>
                        <tr>
                          <th>{t('common.name')}</th>
                          <th>{t('users.email')}</th>
                          <th>{t('users.status')}</th>
                          <th>{t('common.actions')}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {(assignedUsers ?? []).map((user) => (
                          <tr key={user.id}>
                            <td>
                              {user.firstName && user.lastName
                                ? `${user.firstName} ${user.lastName}`
                                : user.username || '-'}
                            </td>
                            <td>{user.email}</td>
                            <td>{user.status}</td>
                            <td>
                              <button
                                className={styles.btnDanger}
                                onClick={() => {
                                  setUserToUnassign(user)
                                  setUnassignDialogOpen(true)
                                }}
                                disabled={unassignMutation.isPending}
                              >
                                {t('permissionSets.unassign')}
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </div>
              )}
            </>
          )}
        </div>
      </div>

      {isFormOpen && (
        <CreateForm
          onSubmit={(data) => createMutation.mutate(data)}
          onCancel={() => setIsFormOpen(false)}
          isSubmitting={createMutation.isPending}
        />
      )}

      <ConfirmDialog
        open={unassignDialogOpen}
        title={t('permissionSets.unassign')}
        message={`Are you sure you want to unassign ${userToUnassign?.email ?? 'this user'}?`}
        confirmLabel={t('permissionSets.unassign')}
        cancelLabel={t('common.cancel')}
        onConfirm={() => {
          if (userToUnassign) unassignMutation.mutate(userToUnassign.id)
        }}
        onCancel={() => {
          setUnassignDialogOpen(false)
          setUserToUnassign(null)
        }}
        variant="danger"
      />
    </div>
  )
}
