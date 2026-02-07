import React, { useState, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useApi } from '../../context/ApiContext'
import { useI18n } from '../../context/I18nContext'
import { useToast } from '../../components/Toast'
import styles from './ProfilesPage.module.css'

interface Profile {
  id: string
  name: string
  description?: string
  system: boolean
  objectPermissions?: ObjectPermission[]
  fieldPermissions?: FieldPermissionEntry[]
  systemPermissions?: SystemPermissionEntry[]
  createdAt: string
  updatedAt: string
}

interface ObjectPermission {
  id?: string
  collectionId: string
  canCreate: boolean
  canRead: boolean
  canEdit: boolean
  canDelete: boolean
  canViewAll: boolean
  canModifyAll: boolean
}

interface FieldPermissionEntry {
  id?: string
  fieldId: string
  visibility: 'VISIBLE' | 'READ_ONLY' | 'HIDDEN'
}

interface SystemPermissionEntry {
  id?: string
  permissionKey: string
  granted: boolean
}

interface Collection {
  id: string
  name: string
  displayName: string
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

export interface ProfilesPageProps {
  testId?: string
}

function ProfileForm({
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

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault()
      if (name.trim()) {
        onSubmit({ name: name.trim(), description: description.trim() })
      }
    },
    [name, description, onSubmit]
  )

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
        <h2>{t('profiles.createProfile')}</h2>
        <form onSubmit={handleSubmit}>
          <div className={styles.formGroup}>
            <label htmlFor="profileName">{t('profiles.name')} *</label>
            <input
              id="profileName"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
          </div>
          <div className={styles.formGroup}>
            <label htmlFor="profileDesc">{t('profiles.description')}</label>
            <textarea
              id="profileDesc"
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

export function ProfilesPage({ testId = 'profiles-page' }: ProfilesPageProps) {
  const queryClient = useQueryClient()
  const { t } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [selectedProfile, setSelectedProfile] = useState<Profile | null>(null)
  const [activeTab, setActiveTab] = useState<'object' | 'system' | 'field'>('object')

  const { data: profiles, isLoading } = useQuery({
    queryKey: ['profiles'],
    queryFn: () => apiClient.get<Profile[]>('/control/profiles'),
  })

  const { data: collections } = useQuery({
    queryKey: ['collections'],
    queryFn: () => apiClient.get<Collection[]>('/control/collections'),
  })

  const { data: profileDetail } = useQuery({
    queryKey: ['profiles', selectedProfile?.id],
    queryFn: () => apiClient.get<Profile>(`/control/profiles/${selectedProfile?.id}`),
    enabled: !!selectedProfile?.id,
  })

  const createMutation = useMutation({
    mutationFn: (data: { name: string; description: string }) =>
      apiClient.post<Profile>('/control/profiles', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profiles'] })
      showToast(t('profiles.createSuccess'), 'success')
      setIsFormOpen(false)
    },
    onError: (err: Error) => {
      showToast(err.message || t('errors.generic'), 'error')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/control/profiles/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profiles'] })
      showToast(t('profiles.deleteSuccess'), 'success')
      setSelectedProfile(null)
    },
    onError: (err: Error) => {
      showToast(err.message || t('errors.generic'), 'error')
    },
  })

  const setObjectPermsMutation = useMutation({
    mutationFn: ({
      profileId,
      collectionId,
      perms,
    }: {
      profileId: string
      collectionId: string
      perms: ObjectPermission
    }) => apiClient.put(`/control/profiles/${profileId}/object-permissions/${collectionId}`, perms),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profiles', selectedProfile?.id] })
      showToast(t('profiles.permissionsSaved'), 'success')
    },
    onError: (err: Error) => {
      showToast(err.message || t('errors.generic'), 'error')
    },
  })

  const setSystemPermsMutation = useMutation({
    mutationFn: ({
      profileId,
      perms,
    }: {
      profileId: string
      perms: { permissionKey: string; granted: boolean }[]
    }) => apiClient.put(`/control/profiles/${profileId}/system-permissions`, perms),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profiles', selectedProfile?.id] })
      showToast(t('profiles.permissionsSaved'), 'success')
    },
    onError: (err: Error) => {
      showToast(err.message || t('errors.generic'), 'error')
    },
  })

  const handleToggleObjectPerm = useCallback(
    (collectionId: string, field: keyof ObjectPermission, current: boolean) => {
      if (!selectedProfile) return

      const existing = profileDetail?.objectPermissions?.find(
        (p) => p.collectionId === collectionId
      )

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

      setObjectPermsMutation.mutate({
        profileId: selectedProfile.id,
        collectionId,
        perms,
      })
    },
    [selectedProfile, profileDetail, setObjectPermsMutation]
  )

  const handleToggleSystemPerm = useCallback(
    (permissionKey: string, currentlyGranted: boolean) => {
      if (!selectedProfile) return

      setSystemPermsMutation.mutate({
        profileId: selectedProfile.id,
        perms: [{ permissionKey, granted: !currentlyGranted }],
      })
    },
    [selectedProfile, setSystemPermsMutation]
  )

  const profileList = profiles ?? []
  const collectionList = collections ?? []

  return (
    <div className={styles.container} data-testid={testId}>
      <header className={styles.header}>
        <h1>{t('profiles.title')}</h1>
        <button className={styles.btnPrimary} onClick={() => setIsFormOpen(true)}>
          {t('profiles.createProfile')}
        </button>
      </header>

      <div className={styles.layout}>
        <div className={styles.sidebar}>
          {isLoading ? (
            <div className={styles.loadingState}>{t('common.loading')}</div>
          ) : profileList.length === 0 ? (
            <div className={styles.emptyState}>{t('profiles.noProfiles')}</div>
          ) : (
            <ul className={styles.profileList}>
              {profileList.map((profile) => (
                <li key={profile.id}>
                  <button
                    className={`${styles.profileItem} ${selectedProfile?.id === profile.id ? styles.profileItemActive : ''}`}
                    onClick={() => setSelectedProfile(profile)}
                  >
                    <span className={styles.profileName}>{profile.name}</span>
                    {profile.system && <span className={styles.systemBadge}>System</span>}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className={styles.content}>
          {!selectedProfile ? (
            <div className={styles.emptyState}>{t('profiles.selectProfile')}</div>
          ) : (
            <>
              <div className={styles.profileHeader}>
                <div>
                  <h2>{selectedProfile.name}</h2>
                  {selectedProfile.description && <p>{selectedProfile.description}</p>}
                </div>
                {!selectedProfile.system && (
                  <button
                    className={styles.btnDanger}
                    onClick={() => deleteMutation.mutate(selectedProfile.id)}
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
                  className={`${styles.tab} ${activeTab === 'field' ? styles.tabActive : ''}`}
                  onClick={() => setActiveTab('field')}
                >
                  {t('profiles.fieldPermissions')}
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
                          const perm = profileDetail?.objectPermissions?.find(
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
                      const perm = profileDetail?.systemPermissions?.find(
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

              {activeTab === 'field' && (
                <div className={styles.card}>
                  <p className={styles.fieldPermInfo}>{t('profiles.fieldPermissionsInfo')}</p>
                  {profileDetail?.fieldPermissions && profileDetail.fieldPermissions.length > 0 ? (
                    <table className={styles.table}>
                      <thead>
                        <tr>
                          <th>{t('profiles.fieldId')}</th>
                          <th>{t('profiles.visibility')}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {profileDetail.fieldPermissions.map((fp) => (
                          <tr key={fp.fieldId}>
                            <td>{fp.fieldId}</td>
                            <td>{fp.visibility}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  ) : (
                    <p>{t('profiles.noFieldPermissions')}</p>
                  )}
                </div>
              )}
            </>
          )}
        </div>
      </div>

      {isFormOpen && (
        <ProfileForm
          onSubmit={(data) => createMutation.mutate(data)}
          onCancel={() => setIsFormOpen(false)}
          isSubmitting={createMutation.isPending}
        />
      )}
    </div>
  )
}
