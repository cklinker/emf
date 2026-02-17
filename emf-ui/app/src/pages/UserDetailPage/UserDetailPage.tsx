import React, { useState, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import { useApi } from '../../context/ApiContext'
import { useI18n } from '../../context/I18nContext'
import { getTenantSlug } from '../../context/TenantContext'
import { useToast } from '../../components/Toast'
import { cn } from '@/lib/utils'

interface PlatformUser {
  id: string
  email: string
  firstName: string
  lastName: string
  username?: string
  status: 'ACTIVE' | 'INACTIVE' | 'LOCKED' | 'PENDING_ACTIVATION'
  locale: string
  timezone: string
  profileId?: string
  managerId?: string
  lastLoginAt?: string
  loginCount: number
  mfaEnabled: boolean
  createdAt: string
  updatedAt: string
}

interface LoginHistoryEntry {
  id: string
  userId: string
  loginTime: string
  sourceIp: string
  loginType: 'UI' | 'API' | 'OAUTH' | 'SERVICE_ACCOUNT'
  status: 'SUCCESS' | 'FAILED' | 'LOCKED_OUT'
  userAgent: string
}

interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

interface UpdateFormData {
  firstName: string
  lastName: string
  username: string
  locale: string
  timezone: string
}

export interface UserDetailPageProps {
  testId?: string
}

function StatusBadge({ status }: { status: string }) {
  const colorMap: Record<string, string> = {
    ACTIVE: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300',
    INACTIVE: 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-300',
    LOCKED: 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300',
    PENDING_ACTIVATION: 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
  }

  return (
    <span
      className={cn(
        'inline-block rounded-full px-2 py-0.5 text-xs font-medium',
        colorMap[status] || ''
      )}
    >
      {status}
    </span>
  )
}

function LoginStatusLabel({ status }: { status: string }) {
  const colorMap: Record<string, string> = {
    SUCCESS: 'font-medium text-emerald-700 dark:text-emerald-300',
    FAILED: 'font-medium text-red-700 dark:text-red-300',
    LOCKED_OUT: 'font-medium text-amber-700 dark:text-amber-300',
  }

  return <span className={colorMap[status] || ''}>{status}</span>
}

export function UserDetailPage({ testId = 'user-detail-page' }: UserDetailPageProps) {
  const { id } = useParams<{ id: string }>()
  const queryClient = useQueryClient()
  const { t, formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()
  const navigate = useNavigate()

  const [activeTab, setActiveTab] = useState<'details' | 'loginHistory'>('details')
  const [isEditing, setIsEditing] = useState(false)
  const [formData, setFormData] = useState<UpdateFormData>({
    firstName: '',
    lastName: '',
    username: '',
    locale: '',
    timezone: '',
  })
  const [historyPage, setHistoryPage] = useState(0)

  const {
    data: user,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['users', id],
    queryFn: async () => {
      const result = await apiClient.get<PlatformUser>(`/control/users/${id}`)
      setFormData({
        firstName: result.firstName,
        lastName: result.lastName,
        username: result.username || '',
        locale: result.locale,
        timezone: result.timezone,
      })
      return result
    },
    enabled: !!id,
  })

  const { data: loginHistory, isLoading: historyLoading } = useQuery({
    queryKey: ['users', id, 'login-history', historyPage],
    queryFn: async () => {
      const params = new URLSearchParams()
      params.append('page', historyPage.toString())
      params.append('size', '20')
      return apiClient.get<PageResponse<LoginHistoryEntry>>(
        `/control/users/${id}/login-history?${params}`
      )
    },
    enabled: !!id && activeTab === 'loginHistory',
  })

  const updateMutation = useMutation({
    mutationFn: (data: UpdateFormData) => apiClient.put<PlatformUser>(`/control/users/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users', id] })
      queryClient.invalidateQueries({ queryKey: ['users'] })
      showToast(t('users.updateSuccess'), 'success')
      setIsEditing(false)
    },
    onError: (err: Error) => {
      showToast(err.message || t('errors.generic'), 'error')
    },
  })

  const statusMutation = useMutation({
    mutationFn: (action: 'deactivate' | 'activate') =>
      apiClient.post(`/control/users/${id}/${action}`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users', id] })
      queryClient.invalidateQueries({ queryKey: ['users'] })
      showToast(t('users.statusUpdateSuccess'), 'success')
    },
    onError: (err: Error) => {
      showToast(err.message || t('errors.generic'), 'error')
    },
  })

  const handleSave = useCallback(() => {
    updateMutation.mutate(formData)
  }, [formData, updateMutation])

  const handleCancel = useCallback(() => {
    if (user) {
      setFormData({
        firstName: user.firstName,
        lastName: user.lastName,
        username: user.username || '',
        locale: user.locale,
        timezone: user.timezone,
      })
    }
    setIsEditing(false)
  }, [user])

  if (error) {
    return (
      <div className="mx-auto max-w-[1200px] space-y-6 p-6" data-testid={testId}>
        <div className="flex flex-col items-center justify-center p-12 text-muted-foreground">
          <p>{t('errors.generic')}</p>
          <button
            onClick={() => refetch()}
            className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
          >
            {t('common.retry')}
          </button>
        </div>
      </div>
    )
  }

  if (isLoading || !user) {
    return (
      <div className="mx-auto max-w-[1200px] space-y-6 p-6" data-testid={testId}>
        <div className="flex flex-col items-center justify-center p-12 text-muted-foreground">
          {t('common.loading')}
        </div>
      </div>
    )
  }

  const historyEntries = loginHistory?.content ?? []
  const historyTotalPages = loginHistory?.totalPages ?? 0

  return (
    <div className="mx-auto max-w-[1200px] space-y-6 p-6" data-testid={testId}>
      <header className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <button
            className="rounded-md border border-border bg-transparent px-3 py-2 text-sm text-muted-foreground hover:bg-muted"
            onClick={() => navigate(`/${getTenantSlug()}/users`)}
          >
            {t('common.back')}
          </button>
          <h1 className="m-0 text-2xl font-semibold text-foreground">
            {user.firstName} {user.lastName}
          </h1>
          <StatusBadge status={user.status} />
        </div>
        <div className="flex gap-2">
          {user.status === 'ACTIVE' ? (
            <button
              className="rounded-md bg-destructive px-4 py-2 text-sm font-medium text-destructive-foreground hover:bg-destructive/90"
              onClick={() => statusMutation.mutate('deactivate')}
              disabled={statusMutation.isPending}
            >
              {t('users.deactivate')}
            </button>
          ) : (
            <button
              className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
              onClick={() => statusMutation.mutate('activate')}
              disabled={statusMutation.isPending}
            >
              {t('users.activate')}
            </button>
          )}
        </div>
      </header>

      <div className="-mb-[2px] flex border-b-2 border-border">
        <button
          className={cn(
            '-mb-[2px] border-b-2 border-transparent bg-transparent px-6 py-3 text-sm font-medium text-muted-foreground hover:text-foreground',
            activeTab === 'details' && 'border-primary text-primary'
          )}
          onClick={() => setActiveTab('details')}
        >
          {t('users.details')}
        </button>
        <button
          className={cn(
            '-mb-[2px] border-b-2 border-transparent bg-transparent px-6 py-3 text-sm font-medium text-muted-foreground hover:text-foreground',
            activeTab === 'loginHistory' && 'border-primary text-primary'
          )}
          onClick={() => setActiveTab('loginHistory')}
        >
          {t('users.loginHistory')}
        </button>
      </div>

      {activeTab === 'details' && (
        <div className="rounded-lg border border-border bg-card p-6">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <label className="mb-1 block text-sm font-medium text-muted-foreground">
                {t('users.email')}
              </label>
              <input
                type="email"
                value={user.email}
                disabled
                className="w-full rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground"
              />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-muted-foreground">
                {t('users.username')}
              </label>
              <input
                type="text"
                value={formData.username}
                onChange={(e) => setFormData({ ...formData, username: e.target.value })}
                disabled={!isEditing}
                className={cn(
                  'w-full rounded-md border border-border px-3 py-2 text-sm text-foreground',
                  !isEditing && 'bg-muted text-muted-foreground'
                )}
              />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-muted-foreground">
                {t('users.firstName')}
              </label>
              <input
                type="text"
                value={formData.firstName}
                onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
                disabled={!isEditing}
                className={cn(
                  'w-full rounded-md border border-border px-3 py-2 text-sm text-foreground',
                  !isEditing && 'bg-muted text-muted-foreground'
                )}
              />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-muted-foreground">
                {t('users.lastName')}
              </label>
              <input
                type="text"
                value={formData.lastName}
                onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
                disabled={!isEditing}
                className={cn(
                  'w-full rounded-md border border-border px-3 py-2 text-sm text-foreground',
                  !isEditing && 'bg-muted text-muted-foreground'
                )}
              />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-muted-foreground">
                {t('users.locale')}
              </label>
              <input
                type="text"
                value={formData.locale}
                onChange={(e) => setFormData({ ...formData, locale: e.target.value })}
                disabled={!isEditing}
                className={cn(
                  'w-full rounded-md border border-border px-3 py-2 text-sm text-foreground',
                  !isEditing && 'bg-muted text-muted-foreground'
                )}
              />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-muted-foreground">
                {t('users.timezone')}
              </label>
              <input
                type="text"
                value={formData.timezone}
                onChange={(e) => setFormData({ ...formData, timezone: e.target.value })}
                disabled={!isEditing}
                className={cn(
                  'w-full rounded-md border border-border px-3 py-2 text-sm text-foreground',
                  !isEditing && 'bg-muted text-muted-foreground'
                )}
              />
            </div>
          </div>

          <div className="mt-4 flex gap-8 border-t border-border pt-4 text-sm text-muted-foreground">
            <div className="flex flex-col gap-0.5">
              <span className="font-medium text-muted-foreground">{t('users.lastLogin')}</span>
              <span>
                {user.lastLoginAt ? formatDate(new Date(user.lastLoginAt)) : t('users.never')}
              </span>
            </div>
            <div className="flex flex-col gap-0.5">
              <span className="font-medium text-muted-foreground">{t('users.loginCount')}</span>
              <span>{user.loginCount}</span>
            </div>
            <div className="flex flex-col gap-0.5">
              <span className="font-medium text-muted-foreground">{t('users.mfaEnabled')}</span>
              <span>{user.mfaEnabled ? t('common.yes') : t('common.no')}</span>
            </div>
            <div className="flex flex-col gap-0.5">
              <span className="font-medium text-muted-foreground">{t('users.created')}</span>
              <span>{formatDate(new Date(user.createdAt))}</span>
            </div>
          </div>

          <div className="mt-6 flex justify-end gap-2">
            {isEditing ? (
              <>
                <button
                  className="rounded-md border border-border bg-secondary px-4 py-2 text-sm text-foreground hover:bg-muted"
                  onClick={handleCancel}
                >
                  {t('common.cancel')}
                </button>
                <button
                  className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
                  onClick={handleSave}
                  disabled={updateMutation.isPending}
                >
                  {updateMutation.isPending ? t('common.saving') : t('common.save')}
                </button>
              </>
            ) : (
              <button
                className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
                onClick={() => setIsEditing(true)}
              >
                {t('common.edit')}
              </button>
            )}
          </div>
        </div>
      )}

      {activeTab === 'loginHistory' && (
        <div className="rounded-lg border border-border bg-card p-6">
          {historyLoading ? (
            <div className="flex flex-col items-center justify-center p-12 text-muted-foreground">
              {t('common.loading')}
            </div>
          ) : historyEntries.length === 0 ? (
            <div className="flex flex-col items-center justify-center p-12 text-muted-foreground">
              <p>{t('users.noLoginHistory')}</p>
            </div>
          ) : (
            <>
              <table className="w-full border-collapse text-sm">
                <thead>
                  <tr>
                    <th className="border-b-2 border-border px-4 py-3 text-left font-semibold text-foreground">
                      {t('users.loginTime')}
                    </th>
                    <th className="border-b-2 border-border px-4 py-3 text-left font-semibold text-foreground">
                      {t('users.loginType')}
                    </th>
                    <th className="border-b-2 border-border px-4 py-3 text-left font-semibold text-foreground">
                      {t('users.loginStatus')}
                    </th>
                    <th className="border-b-2 border-border px-4 py-3 text-left font-semibold text-foreground">
                      {t('users.sourceIp')}
                    </th>
                    <th className="border-b-2 border-border px-4 py-3 text-left font-semibold text-foreground">
                      {t('users.userAgent')}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {historyEntries.map((entry) => (
                    <tr key={entry.id} className="border-b border-border hover:bg-muted/50">
                      <td className="px-4 py-3 text-foreground">
                        {formatDate(new Date(entry.loginTime))}
                      </td>
                      <td className="px-4 py-3 text-foreground">{entry.loginType}</td>
                      <td className="px-4 py-3">
                        <LoginStatusLabel status={entry.status} />
                      </td>
                      <td className="px-4 py-3 text-foreground">{entry.sourceIp}</td>
                      <td className="max-w-[200px] truncate px-4 py-3 text-foreground">
                        {entry.userAgent}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              {historyTotalPages > 1 && (
                <div className="mt-4 flex items-center justify-center gap-4 border-t border-border pt-4 text-muted-foreground">
                  <button
                    disabled={historyPage === 0}
                    onClick={() => setHistoryPage((p) => Math.max(0, p - 1))}
                    className="rounded-md border border-border bg-card px-2 py-1 text-xs text-foreground hover:bg-muted disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {t('common.previous')}
                  </button>
                  <span>
                    {t('common.pageOf', { current: historyPage + 1, total: historyTotalPages })}
                  </span>
                  <button
                    disabled={historyPage >= historyTotalPages - 1}
                    onClick={() => setHistoryPage((p) => p + 1)}
                    className="rounded-md border border-border bg-card px-2 py-1 text-xs text-foreground hover:bg-muted disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {t('common.next')}
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      )}
    </div>
  )
}
