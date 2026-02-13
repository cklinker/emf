import React, { useState, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { useApi } from '../../context/ApiContext'
import { useI18n } from '../../context/I18nContext'
import { getTenantSlug } from '../../context/TenantContext'
import { useToast } from '../../components/Toast'
import styles from './UsersPage.module.css'

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

interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
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
  const colorMap: Record<string, string> = {
    ACTIVE: styles.statusActive,
    INACTIVE: styles.statusInactive,
    LOCKED: styles.statusLocked,
    PENDING_ACTIVATION: styles.statusPending,
  }

  return <span className={`${styles.statusBadge} ${colorMap[status] || ''}`}>{status}</span>
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
      className={styles.modalOverlay}
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={(e) => {
        if (e.key === 'Escape') onCancel()
      }}
      role="presentation"
    >
      <div className={styles.modal} role="dialog" aria-modal="true">
        <h2>{t('users.createUser')}</h2>
        <form onSubmit={handleSubmit}>
          <div className={styles.formGroup}>
            <label htmlFor="email">{t('users.email')} *</label>
            <input
              id="email"
              type="email"
              value={formData.email}
              onChange={(e) => setFormData({ ...formData, email: e.target.value })}
              className={errors.email ? styles.inputError : ''}
            />
            {errors.email && <span className={styles.errorText}>{errors.email}</span>}
          </div>
          <div className={styles.formGroup}>
            <label htmlFor="firstName">{t('users.firstName')} *</label>
            <input
              id="firstName"
              type="text"
              value={formData.firstName}
              onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
              className={errors.firstName ? styles.inputError : ''}
            />
            {errors.firstName && <span className={styles.errorText}>{errors.firstName}</span>}
          </div>
          <div className={styles.formGroup}>
            <label htmlFor="lastName">{t('users.lastName')} *</label>
            <input
              id="lastName"
              type="text"
              value={formData.lastName}
              onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
              className={errors.lastName ? styles.inputError : ''}
            />
            {errors.lastName && <span className={styles.errorText}>{errors.lastName}</span>}
          </div>
          <div className={styles.formGroup}>
            <label htmlFor="username">{t('users.username')}</label>
            <input
              id="username"
              type="text"
              value={formData.username}
              onChange={(e) => setFormData({ ...formData, username: e.target.value })}
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
      params.append('page', page.toString())
      params.append('size', '20')
      return apiClient.get<PageResponse<PlatformUser>>(`/control/users?${params}`)
    },
  })

  const createMutation = useMutation({
    mutationFn: (formData: CreateUserFormData) =>
      apiClient.post<PlatformUser>('/control/users', formData),
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
      apiClient.post(`/control/users/${userId}/${action}`, {}),
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
      <div className={styles.container} data-testid={testId}>
        <div className={styles.errorState}>
          <p>{t('errors.generic')}</p>
          <button onClick={() => refetch()} className={styles.btnPrimary}>
            {t('common.retry')}
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className={styles.container} data-testid={testId}>
      <header className={styles.header}>
        <h1>{t('users.title')}</h1>
        <button className={styles.btnPrimary} onClick={() => setIsFormOpen(true)}>
          {t('users.createUser')}
        </button>
      </header>

      <div className={styles.toolbar}>
        <input
          type="text"
          placeholder={t('users.searchPlaceholder')}
          value={filter}
          onChange={(e) => {
            setFilter(e.target.value)
            setPage(0)
          }}
          className={styles.searchInput}
        />
        <select
          value={statusFilter}
          onChange={(e) => {
            setStatusFilter(e.target.value)
            setPage(0)
          }}
          className={styles.statusSelect}
        >
          <option value="">{t('users.allStatuses')}</option>
          <option value="ACTIVE">{t('users.statusActive')}</option>
          <option value="INACTIVE">{t('users.statusInactive')}</option>
          <option value="LOCKED">{t('users.statusLocked')}</option>
        </select>
      </div>

      {isLoading ? (
        <div className={styles.loadingState}>{t('common.loading')}</div>
      ) : users.length === 0 ? (
        <div className={styles.emptyState}>
          <p>{t('common.noResults')}</p>
        </div>
      ) : (
        <>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>{t('users.name')}</th>
                <th>{t('users.email')}</th>
                <th>{t('users.username')}</th>
                <th>{t('users.status')}</th>
                <th>{t('users.lastLogin')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => (
                <tr key={user.id}>
                  <td>
                    <button
                      className={styles.linkButton}
                      onClick={() => navigate(`/${getTenantSlug()}/users/${user.id}`)}
                    >
                      {user.firstName} {user.lastName}
                    </button>
                  </td>
                  <td>{user.email}</td>
                  <td>{user.username || '\u2014'}</td>
                  <td>
                    <StatusBadge status={user.status} />
                  </td>
                  <td>{user.lastLoginAt ? formatDate(user.lastLoginAt) : t('users.never')}</td>
                  <td>
                    <div className={styles.actionButtons}>
                      <button
                        className={styles.btnSmall}
                        onClick={() => navigate(`/${getTenantSlug()}/users/${user.id}`)}
                      >
                        {t('common.edit')}
                      </button>
                      {user.status === 'ACTIVE' ? (
                        <button
                          className={`${styles.btnSmall} ${styles.btnDanger}`}
                          onClick={() => handleStatusAction(user, 'deactivate')}
                        >
                          {t('users.deactivate')}
                        </button>
                      ) : user.status !== 'ACTIVE' ? (
                        <button
                          className={`${styles.btnSmall} ${styles.btnSuccess}`}
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

          {totalPages > 1 && (
            <div className={styles.pagination}>
              <button
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className={styles.btnSmall}
              >
                {t('common.previous')}
              </button>
              <span>{t('common.pageOf', { current: page + 1, total: totalPages })}</span>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
                className={styles.btnSmall}
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
          className={styles.modalOverlay}
          onClick={(e) => {
            if (e.target === e.currentTarget) setConfirmDialog({ ...confirmDialog, open: false })
          }}
          onKeyDown={(e) => {
            if (e.key === 'Escape') setConfirmDialog({ ...confirmDialog, open: false })
          }}
          role="presentation"
        >
          <div className={styles.modal} role="dialog" aria-modal="true">
            <h2>{t('common.confirm')}</h2>
            <p>
              {confirmDialog.action === 'deactivate'
                ? t('users.deactivateConfirm', { name: confirmDialog.userName })
                : t('users.activateConfirm', { name: confirmDialog.userName })}
            </p>
            <div className={styles.formActions}>
              <button
                className={styles.btnSecondary}
                onClick={() => setConfirmDialog({ ...confirmDialog, open: false })}
              >
                {t('common.cancel')}
              </button>
              <button
                className={
                  confirmDialog.action === 'deactivate' ? styles.btnDanger : styles.btnPrimary
                }
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
