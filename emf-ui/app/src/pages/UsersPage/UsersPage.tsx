import React, { useState, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
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

interface CreateUserFormData {
  email: string
  firstName: string
  lastName: string
  username: string
}

interface FormErrors {
  email?: string
  firstName?: string
  lastName?: string
}

export interface UsersPageProps {
  testId?: string
}

function StatusBadge({ status }: { status: string }) {
  return (
    <span
      className={cn(
        'inline-block rounded-full px-2 py-0.5 text-xs font-medium',
        status === 'ACTIVE' &&
          'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300',
        status === 'INACTIVE' && 'bg-muted text-foreground',
        status === 'LOCKED' && 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300',
        status === 'PENDING_ACTIVATION' &&
          'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300'
      )}
    >
      {status}
    </span>
  )
}

function UserForm({
  onSubmit,
  onCancel,
  isSubmitting,
}: {
  onSubmit: (data: CreateUserFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}) {
  const { t } = useI18n()
  const [formData, setFormData] = useState<CreateUserFormData>({
    email: '',
    firstName: '',
    lastName: '',
    username: '',
  })
  const [errors, setErrors] = useState<FormErrors>({})

  const validate = useCallback((): FormErrors => {
    const errs: FormErrors = {}
    if (!formData.email.trim()) errs.email = 'Email is required'
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) errs.email = 'Invalid email'
    if (!formData.firstName.trim()) errs.firstName = 'First name is required'
    if (!formData.lastName.trim()) errs.lastName = 'Last name is required'
    return errs
  }, [formData])

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault()
      const errs = validate()
      setErrors(errs)
      if (Object.keys(errs).length === 0) {
        onSubmit(formData)
      }
    },
    [formData, validate, onSubmit]
  )

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={(e) => {
        if (e.key === 'Escape') onCancel()
      }}
      role="presentation"
    >
      <div
        className="w-full max-w-[480px] rounded-lg bg-card p-6 shadow-xl"
        role="dialog"
        aria-modal="true"
      >
        <h2 className="mb-4 text-xl font-semibold">{t('users.createUser')}</h2>
        <form onSubmit={handleSubmit}>
          <div className="mb-4">
            <label htmlFor="email" className="mb-1 block text-sm font-medium text-foreground">
              {t('users.email')} *
            </label>
            <input
              id="email"
              type="email"
              value={formData.email}
              onChange={(e) => setFormData({ ...formData, email: e.target.value })}
              className={cn(
                'w-full rounded-md border border-border bg-background px-3 py-2 text-sm',
                errors.email && 'border-destructive'
              )}
            />
            {errors.email && (
              <span className="mt-1 block text-xs text-destructive">{errors.email}</span>
            )}
          </div>
          <div className="mb-4">
            <label htmlFor="firstName" className="mb-1 block text-sm font-medium text-foreground">
              {t('users.firstName')} *
            </label>
            <input
              id="firstName"
              type="text"
              value={formData.firstName}
              onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
              className={cn(
                'w-full rounded-md border border-border bg-background px-3 py-2 text-sm',
                errors.firstName && 'border-destructive'
              )}
            />
            {errors.firstName && (
              <span className="mt-1 block text-xs text-destructive">{errors.firstName}</span>
            )}
          </div>
          <div className="mb-4">
            <label htmlFor="lastName" className="mb-1 block text-sm font-medium text-foreground">
              {t('users.lastName')} *
            </label>
            <input
              id="lastName"
              type="text"
              value={formData.lastName}
              onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
              className={cn(
                'w-full rounded-md border border-border bg-background px-3 py-2 text-sm',
                errors.lastName && 'border-destructive'
              )}
            />
            {errors.lastName && (
              <span className="mt-1 block text-xs text-destructive">{errors.lastName}</span>
            )}
          </div>
          <div className="mb-4">
            <label htmlFor="username" className="mb-1 block text-sm font-medium text-foreground">
              {t('users.username')}
            </label>
            <input
              id="username"
              type="text"
              value={formData.username}
              onChange={(e) => setFormData({ ...formData, username: e.target.value })}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div className="mt-6 flex justify-end gap-2">
            <button
              type="button"
              className="cursor-pointer rounded-md border border-border bg-muted px-4 py-2 text-sm text-foreground"
              onClick={onCancel}
            >
              {t('common.cancel')}
            </button>
            <button
              type="submit"
              className="cursor-pointer rounded-md border-none bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:cursor-not-allowed disabled:opacity-50"
              disabled={isSubmitting}
            >
              {isSubmitting ? t('common.saving') : t('common.create')}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export function UsersPage({ testId = 'users-page' }: UsersPageProps) {
  const queryClient = useQueryClient()
  const { t, formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()
  const navigate = useNavigate()

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [filter, setFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [page, setPage] = useState(0)

  const [confirmDialog, setConfirmDialog] = useState<{
    open: boolean
    userId: string
    action: 'deactivate' | 'activate'
    userName: string
  }>({ open: false, userId: '', action: 'deactivate', userName: '' })

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['users', filter, statusFilter, page],
    queryFn: async () => {
      const params = new URLSearchParams()
      if (filter) params.append('filter', filter)
      if (statusFilter) params.append('status', statusFilter)
      params.append('page[number]', page.toString())
      params.append('page[size]', '20')
      return apiClient.getPage<PlatformUser>(`/api/users?${params}`)
    },
  })

  const createMutation = useMutation({
    mutationFn: (formData: CreateUserFormData) =>
      apiClient.postResource<PlatformUser>('/api/users', formData),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      showToast(t('users.createSuccess'), 'success')
      setIsFormOpen(false)
    },
    onError: (err: Error) => {
      showToast(err.message || t('errors.generic'), 'error')
    },
  })

  const statusMutation = useMutation({
    mutationFn: ({ userId, action }: { userId: string; action: 'deactivate' | 'activate' }) =>
      apiClient.patchResource(`/api/users/${userId}`, {
        status: action === 'activate' ? 'ACTIVE' : 'INACTIVE',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      showToast(
        confirmDialog.action === 'deactivate'
          ? t('users.deactivateSuccess')
          : t('users.activateSuccess'),
        'success'
      )
      setConfirmDialog({ open: false, userId: '', action: 'deactivate', userName: '' })
    },
    onError: (err: Error) => {
      showToast(err.message || t('errors.generic'), 'error')
    },
  })

  const handleStatusAction = useCallback(
    (user: PlatformUser, action: 'deactivate' | 'activate') => {
      setConfirmDialog({
        open: true,
        userId: user.id,
        action,
        userName: `${user.firstName} ${user.lastName}`,
      })
    },
    []
  )

  const confirmAction = useCallback(() => {
    statusMutation.mutate({
      userId: confirmDialog.userId,
      action: confirmDialog.action,
    })
  }, [confirmDialog, statusMutation])

  const users = data?.content ?? []
  const totalPages = data?.totalPages ?? 0

  if (error) {
    return (
      <div className="mx-auto max-w-[1200px] p-6" data-testid={testId}>
        <div className="flex flex-col items-center justify-center p-12 text-muted-foreground">
          <p>{t('errors.generic')}</p>
          <button
            onClick={() => refetch()}
            className="cursor-pointer rounded-md border-none bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
          >
            {t('common.retry')}
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-[1200px] p-6" data-testid={testId}>
      <header className="mb-6 flex items-center justify-between">
        <h1 className="m-0 text-2xl font-semibold">{t('users.title')}</h1>
        <button
          className="cursor-pointer rounded-md border-none bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
          onClick={() => setIsFormOpen(true)}
        >
          {t('users.createUser')}
        </button>
      </header>

      <div className="mb-4 flex gap-4">
        <input
          type="text"
          placeholder={t('users.searchPlaceholder')}
          value={filter}
          onChange={(e) => {
            setFilter(e.target.value)
            setPage(0)
          }}
          className="flex-1 rounded-md border border-border bg-background px-3 py-2 text-sm"
        />
        <select
          value={statusFilter}
          onChange={(e) => {
            setStatusFilter(e.target.value)
            setPage(0)
          }}
          className="min-w-[140px] rounded-md border border-border bg-background px-3 py-2 text-sm"
        >
          <option value="">{t('users.allStatuses')}</option>
          <option value="ACTIVE">{t('users.statusActive')}</option>
          <option value="INACTIVE">{t('users.statusInactive')}</option>
          <option value="LOCKED">{t('users.statusLocked')}</option>
        </select>
      </div>

      {isLoading ? (
        <div className="flex flex-col items-center justify-center p-12 text-muted-foreground">
          {t('common.loading')}
        </div>
      ) : users.length === 0 ? (
        <div className="flex flex-col items-center justify-center p-12 text-muted-foreground">
          <p>{t('common.noResults')}</p>
        </div>
      ) : (
        <>
          <div className="overflow-x-auto rounded-lg border border-border bg-card">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr>
                  <th className="border-b-2 border-border p-3 text-left font-semibold text-foreground">
                    {t('users.name')}
                  </th>
                  <th className="border-b-2 border-border p-3 text-left font-semibold text-foreground">
                    {t('users.email')}
                  </th>
                  <th className="border-b-2 border-border p-3 text-left font-semibold text-foreground">
                    {t('users.username')}
                  </th>
                  <th className="border-b-2 border-border p-3 text-left font-semibold text-foreground">
                    {t('users.status')}
                  </th>
                  <th className="border-b-2 border-border p-3 text-left font-semibold text-foreground">
                    {t('users.lastLogin')}
                  </th>
                  <th className="border-b-2 border-border p-3 text-left font-semibold text-foreground">
                    {t('common.actions')}
                  </th>
                </tr>
              </thead>
              <tbody>
                {users.map((user) => (
                  <tr key={user.id} className="hover:bg-muted">
                    <td className="border-b border-border p-3">
                      <button
                        className="cursor-pointer border-none bg-transparent p-0 text-left font-medium text-primary hover:underline"
                        onClick={() => navigate(`/${getTenantSlug()}/users/${user.id}`)}
                      >
                        {user.firstName} {user.lastName}
                      </button>
                    </td>
                    <td className="border-b border-border p-3">{user.email}</td>
                    <td className="border-b border-border p-3">{user.username || '\u2014'}</td>
                    <td className="border-b border-border p-3">
                      <StatusBadge status={user.status} />
                    </td>
                    <td className="border-b border-border p-3">
                      {user.lastLoginAt ? formatDate(user.lastLoginAt) : t('users.never')}
                    </td>
                    <td className="border-b border-border p-3">
                      <div className="flex gap-2">
                        <button
                          className="cursor-pointer rounded border border-border bg-card px-2 py-1 text-xs hover:bg-muted disabled:cursor-not-allowed disabled:opacity-50"
                          onClick={() => navigate(`/${getTenantSlug()}/users/${user.id}`)}
                        >
                          {t('common.edit')}
                        </button>
                        {user.status === 'ACTIVE' ? (
                          <button
                            className="cursor-pointer rounded border-none bg-destructive px-2 py-1 text-xs text-destructive-foreground hover:bg-destructive/90"
                            onClick={() => handleStatusAction(user, 'deactivate')}
                          >
                            {t('users.deactivate')}
                          </button>
                        ) : user.status !== 'ACTIVE' ? (
                          <button
                            className="cursor-pointer rounded border-none bg-emerald-500 px-2 py-1 text-xs text-white hover:bg-emerald-600"
                            onClick={() => handleStatusAction(user, 'activate')}
                          >
                            {t('users.activate')}
                          </button>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div className="mt-4 flex items-center justify-center gap-4 border-t border-border pt-4">
              <button
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="cursor-pointer rounded border border-border bg-card px-2 py-1 text-xs hover:bg-muted disabled:cursor-not-allowed disabled:opacity-50"
              >
                {t('common.previous')}
              </button>
              <span className="text-sm">
                {t('common.pageOf', { current: page + 1, total: totalPages })}
              </span>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
                className="cursor-pointer rounded border border-border bg-card px-2 py-1 text-xs hover:bg-muted disabled:cursor-not-allowed disabled:opacity-50"
              >
                {t('common.next')}
              </button>
            </div>
          )}
        </>
      )}

      {isFormOpen && (
        <UserForm
          onSubmit={(data) => createMutation.mutate(data)}
          onCancel={() => setIsFormOpen(false)}
          isSubmitting={createMutation.isPending}
        />
      )}

      {confirmDialog.open && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
          onClick={(e) => {
            if (e.target === e.currentTarget) setConfirmDialog({ ...confirmDialog, open: false })
          }}
          onKeyDown={(e) => {
            if (e.key === 'Escape') setConfirmDialog({ ...confirmDialog, open: false })
          }}
          role="presentation"
        >
          <div
            className="w-full max-w-[480px] rounded-lg bg-card p-6 shadow-xl"
            role="dialog"
            aria-modal="true"
          >
            <h2 className="mb-4 text-xl font-semibold">{t('common.confirm')}</h2>
            <p>
              {confirmDialog.action === 'deactivate'
                ? t('users.deactivateConfirm', { name: confirmDialog.userName })
                : t('users.activateConfirm', { name: confirmDialog.userName })}
            </p>
            <div className="mt-6 flex justify-end gap-2">
              <button
                className="cursor-pointer rounded-md border border-border bg-muted px-4 py-2 text-sm text-foreground"
                onClick={() => setConfirmDialog({ ...confirmDialog, open: false })}
              >
                {t('common.cancel')}
              </button>
              <button
                className={cn(
                  'cursor-pointer rounded-md border-none px-4 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50',
                  confirmDialog.action === 'deactivate'
                    ? 'bg-destructive hover:bg-destructive/90'
                    : 'bg-primary hover:bg-primary/90'
                )}
                onClick={confirmAction}
                disabled={statusMutation.isPending}
              >
                {statusMutation.isPending ? t('common.saving') : t('common.confirm')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
