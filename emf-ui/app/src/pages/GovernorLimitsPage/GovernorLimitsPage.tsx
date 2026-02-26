import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../components/Toast'
import { LoadingSpinner, ErrorMessage } from '../../components'
import { cn } from '@/lib/utils'

interface GovernorLimits {
  apiCallsPerDay: number
  storageGb: number
  maxUsers: number
  maxCollections: number
  maxFieldsPerCollection: number
  maxWorkflows: number
  maxReports: number
}

interface GovernorLimitsStatus {
  limits: GovernorLimits
  apiCallsUsed: number
  apiCallsLimit: number
  usersUsed: number
  usersLimit: number
  collectionsUsed: number
  collectionsLimit: number
}

export interface GovernorLimitsPageProps {
  className?: string
}

export function GovernorLimitsPage({ className }: GovernorLimitsPageProps): React.ReactElement {
  const { t } = useI18n()
  const { apiClient } = useApi()
  const { user } = useAuth()
  const { showToast } = useToast()
  const queryClient = useQueryClient()

  const isPlatformAdmin = user?.roles?.includes('PLATFORM_ADMIN') ?? false
  const [isEditing, setIsEditing] = useState(false)
  const [editLimits, setEditLimits] = useState<GovernorLimits | null>(null)

  const {
    data: status,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['governor-limits'],
    queryFn: () => apiClient.get<GovernorLimitsStatus>('/api/governor-limits'),
    refetchInterval: 60000,
  })

  const saveMutation = useMutation({
    mutationFn: (limits: GovernorLimits) =>
      apiClient.put<GovernorLimits>('/api/governor-limits', limits),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['governor-limits'] })
      showToast(t('governorLimits.saveSuccess'), 'success')
      setIsEditing(false)
      setEditLimits(null)
    },
    onError: () => {
      showToast(t('governorLimits.saveError'), 'error')
    },
  })

  const handleEdit = () => {
    if (status) {
      setEditLimits({ ...status.limits })
      setIsEditing(true)
    }
  }

  const handleCancel = () => {
    setIsEditing(false)
    setEditLimits(null)
  }

  const handleSave = () => {
    if (editLimits) {
      saveMutation.mutate(editLimits)
    }
  }

  const updateField = (field: keyof GovernorLimits, value: string) => {
    if (editLimits) {
      const num = parseInt(value, 10)
      if (!isNaN(num) && num >= 0) {
        setEditLimits({ ...editLimits, [field]: num })
      }
    }
  }

  if (isLoading) return <LoadingSpinner />
  if (error) return <ErrorMessage error={t('governorLimits.loadError')} />

  if (!status)
    return (
      <div className="p-12 text-center text-muted-foreground">{t('governorLimits.noData')}</div>
    )

  const metrics = [
    {
      label: t('governorLimits.apiCalls'),
      used: status.apiCallsUsed,
      limit: status.apiCallsLimit,
    },
    {
      label: t('governorLimits.users'),
      used: status.usersUsed,
      limit: status.usersLimit,
    },
    {
      label: t('governorLimits.collections'),
      used: status.collectionsUsed,
      limit: status.collectionsLimit,
    },
  ]

  const limitRows: { key: keyof GovernorLimits; labelKey: string; suffix?: string }[] = [
    { key: 'apiCallsPerDay', labelKey: 'governorLimits.apiCallsPerDay' },
    { key: 'storageGb', labelKey: 'governorLimits.storageGb', suffix: ' GB' },
    { key: 'maxUsers', labelKey: 'governorLimits.maxUsers' },
    { key: 'maxCollections', labelKey: 'governorLimits.maxCollections' },
    { key: 'maxFieldsPerCollection', labelKey: 'governorLimits.maxFieldsPerCollection' },
    { key: 'maxWorkflows', labelKey: 'governorLimits.maxWorkflows' },
    { key: 'maxReports', labelKey: 'governorLimits.maxReports' },
  ]

  return (
    <div className={cn('mx-auto max-w-[1200px] p-6', className)}>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="m-0 text-2xl font-semibold text-foreground">{t('governorLimits.title')}</h1>
        {isPlatformAdmin && !isEditing && (
          <button
            onClick={handleEdit}
            className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
          >
            {t('governorLimits.editLimits')}
          </button>
        )}
        {isEditing && (
          <div className="flex gap-2">
            <button
              onClick={handleCancel}
              className="rounded-md border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-muted"
            >
              {t('common.cancel')}
            </button>
            <button
              onClick={handleSave}
              disabled={saveMutation.isPending}
              className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
            >
              {saveMutation.isPending ? t('common.saving') : t('common.save')}
            </button>
          </div>
        )}
      </div>

      <div className="mb-8 grid grid-cols-[repeat(auto-fit,minmax(300px,1fr))] gap-6">
        {metrics.map((metric) => {
          const percentage = metric.limit > 0 ? (metric.used / metric.limit) * 100 : 0
          const isWarning = percentage >= 80
          const isCritical = percentage >= 95

          return (
            <div key={metric.label} className="rounded-lg border border-border bg-card p-6">
              <div className="mb-4 flex items-center justify-between">
                <h3 className="m-0 text-base font-semibold text-foreground">{metric.label}</h3>
                {isWarning && (
                  <span
                    className={cn(
                      'rounded-full px-2 py-0.5 text-xs font-medium',
                      isCritical
                        ? 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300'
                        : 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300'
                    )}
                  >
                    {isCritical ? t('governorLimits.critical') : t('governorLimits.warning')}
                  </span>
                )}
              </div>
              <div className="flex flex-col gap-2">
                <div className="flex items-baseline gap-1">
                  <span className="text-2xl font-bold text-foreground">
                    {metric.used.toLocaleString()}
                  </span>
                  <span className="text-muted-foreground">/</span>
                  <span className="text-muted-foreground">{metric.limit.toLocaleString()}</span>
                </div>
                <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
                  <div
                    className={cn(
                      'h-full rounded-full transition-[width] duration-300',
                      isCritical ? 'bg-red-500' : isWarning ? 'bg-amber-500' : 'bg-emerald-500'
                    )}
                    style={{ width: `${Math.min(percentage, 100)}%` }}
                  />
                </div>
                <div className="text-right text-sm text-muted-foreground">
                  {percentage.toFixed(1)}%
                </div>
              </div>
            </div>
          )
        })}
      </div>

      <div className="mt-8">
        <h2 className="mb-4 text-lg font-semibold text-foreground">
          {t('governorLimits.allLimits')}
        </h2>
        <div className="overflow-x-auto rounded-lg border border-border bg-card">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="bg-muted">
                <th className="border-b border-border p-3 text-left font-semibold text-muted-foreground">
                  {t('governorLimits.limitName')}
                </th>
                <th className="border-b border-border p-3 text-left font-semibold text-muted-foreground">
                  {t('governorLimits.limitValue')}
                </th>
              </tr>
            </thead>
            <tbody>
              {limitRows.map((row, index) => (
                <tr key={row.key}>
                  <td
                    className={cn('p-3', index < limitRows.length - 1 && 'border-b border-border')}
                  >
                    {t(row.labelKey)}
                  </td>
                  <td
                    className={cn('p-3', index < limitRows.length - 1 && 'border-b border-border')}
                  >
                    {isEditing && editLimits ? (
                      <input
                        type="number"
                        min="0"
                        value={editLimits[row.key]}
                        onChange={(e) => updateField(row.key, e.target.value)}
                        className="w-32 rounded-md border border-border bg-background px-2 py-1 text-sm text-foreground"
                      />
                    ) : (
                      <>
                        {status.limits[row.key].toLocaleString()}
                        {row.suffix ?? ''}
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

export default GovernorLimitsPage
