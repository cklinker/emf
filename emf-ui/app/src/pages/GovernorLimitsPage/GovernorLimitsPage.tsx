import React from 'react'
import { useQuery } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { LoadingSpinner, ErrorMessage } from '../../components'
import { cn } from '@/lib/utils'

export interface GovernorLimitsPageProps {
  className?: string
}

export function GovernorLimitsPage({ className }: GovernorLimitsPageProps): React.ReactElement {
  const { t } = useI18n()
  const { adminClient } = useApi()

  const {
    data: status,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['governor-limits'],
    queryFn: () => adminClient.governorLimits.getStatus(),
    refetchInterval: 60000,
  })

  if (isLoading) return <LoadingSpinner />
  if (error) return <ErrorMessage message={t('governorLimits.loadError')} />

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

  return (
    <div className={cn('mx-auto max-w-[1200px] p-6', className)}>
      <div className="mb-6">
        <h1 className="m-0 text-2xl font-semibold text-foreground">{t('governorLimits.title')}</h1>
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
              <tr>
                <td className="border-b border-border p-3">{t('governorLimits.apiCallsPerDay')}</td>
                <td className="border-b border-border p-3">
                  {status.limits.apiCallsPerDay.toLocaleString()}
                </td>
              </tr>
              <tr>
                <td className="border-b border-border p-3">{t('governorLimits.storageGb')}</td>
                <td className="border-b border-border p-3">{status.limits.storageGb} GB</td>
              </tr>
              <tr>
                <td className="border-b border-border p-3">{t('governorLimits.maxUsers')}</td>
                <td className="border-b border-border p-3">
                  {status.limits.maxUsers.toLocaleString()}
                </td>
              </tr>
              <tr>
                <td className="border-b border-border p-3">{t('governorLimits.maxCollections')}</td>
                <td className="border-b border-border p-3">
                  {status.limits.maxCollections.toLocaleString()}
                </td>
              </tr>
              <tr>
                <td className="border-b border-border p-3">
                  {t('governorLimits.maxFieldsPerCollection')}
                </td>
                <td className="border-b border-border p-3">
                  {status.limits.maxFieldsPerCollection.toLocaleString()}
                </td>
              </tr>
              <tr>
                <td className="border-b border-border p-3">{t('governorLimits.maxWorkflows')}</td>
                <td className="border-b border-border p-3">
                  {status.limits.maxWorkflows.toLocaleString()}
                </td>
              </tr>
              <tr>
                <td className="p-3">{t('governorLimits.maxReports')}</td>
                <td className="p-3">{status.limits.maxReports.toLocaleString()}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

export default GovernorLimitsPage
