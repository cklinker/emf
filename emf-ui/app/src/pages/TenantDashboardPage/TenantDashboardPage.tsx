/**
 * TenantDashboardPage Component
 *
 * Per-tenant usage and health metrics visible to tenant admins.
 * Shows API calls, storage, user counts, and collection counts against limits.
 *
 * Requirements:
 * - A14: Tenant Dashboard UI
 */

import React, { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { LoadingSpinner, ErrorMessage } from '../../components'
import styles from './TenantDashboardPage.module.css'

/**
 * Governor limits for the current tenant
 */
interface GovernorLimits {
  apiCallsPerDay: number
  storageGb: number
  maxUsers: number
  maxCollections: number
  maxFieldsPerCollection: number
  maxWorkflows: number
  maxReports: number
}

/**
 * Usage statistics for the current tenant
 */
interface TenantUsage {
  apiCallsToday: number
  storageUsedGb: number
  activeUsers: number
  collectionsCount: number
}

/**
 * Dashboard data from the tenant-scoped API
 */
interface TenantDashboardData {
  limits: GovernorLimits
  usage: TenantUsage
}

/**
 * Props for TenantDashboardPage component
 */
export interface TenantDashboardPageProps {
  testId?: string
}

/**
 * UsageCard Component — displays a single metric against its limit.
 */
interface UsageCardProps {
  title: string
  current: number
  limit: number
  unit?: string
  testId?: string
}

function UsageCard({
  title,
  current,
  limit,
  unit = '',
  testId,
}: UsageCardProps): React.ReactElement {
  const percentage = limit > 0 ? Math.min((current / limit) * 100, 100) : 0

  const barClass = useMemo(() => {
    if (percentage >= 90) return styles.barCritical
    if (percentage >= 75) return styles.barWarning
    return styles.barNormal
  }, [percentage])

  return (
    <div className={styles.usageCard} data-testid={testId} role="article" aria-label={title}>
      <h3 className={styles.usageCardTitle}>{title}</h3>
      <div className={styles.usageValues}>
        <span className={styles.usageCurrent}>
          {current.toLocaleString()}
          {unit}
        </span>
        <span className={styles.usageLimit}>
          / {limit.toLocaleString()}
          {unit}
        </span>
      </div>
      <div className={styles.usageBarContainer} aria-hidden="true">
        <div className={`${styles.usageBar} ${barClass}`} style={{ width: `${percentage}%` }} />
      </div>
      <span className={styles.usagePercentage}>{percentage.toFixed(1)}% used</span>
    </div>
  )
}

/**
 * TenantDashboardPage Component
 *
 * Displays per-tenant usage metrics against governor limits.
 */
export function TenantDashboardPage({
  testId = 'tenant-dashboard-page',
}: TenantDashboardPageProps): React.ReactElement {
  const { t } = useI18n()
  const { apiClient } = useApi()

  const {
    data: dashboardData,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['tenant-dashboard'],
    queryFn: () => apiClient.get<TenantDashboardData>('/control/_admin/dashboard'),
    refetchInterval: 60000, // Refresh every minute
  })

  // Default values when data hasn't loaded yet
  const limits = dashboardData?.limits ?? {
    apiCallsPerDay: 100000,
    storageGb: 10,
    maxUsers: 100,
    maxCollections: 200,
    maxFieldsPerCollection: 500,
    maxWorkflows: 50,
    maxReports: 200,
  }

  const usage = dashboardData?.usage ?? {
    apiCallsToday: 0,
    storageUsedGb: 0,
    activeUsers: 0,
    collectionsCount: 0,
  }

  if (isLoading) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label={t('common.loading')} />
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className={styles.container} data-testid={testId}>
        <ErrorMessage
          error={error instanceof Error ? error : new Error(t('errors.generic'))}
          onRetry={() => refetch()}
        />
      </div>
    )
  }

  return (
    <div className={styles.container} data-testid={testId}>
      <header className={styles.header}>
        <h1 className={styles.title}>Tenant Dashboard</h1>
      </header>

      <section className={styles.section} aria-labelledby="usage-heading">
        <h2 id="usage-heading" className={styles.sectionTitle}>
          Usage & Limits
        </h2>
        <div className={styles.usageGrid} data-testid="usage-cards">
          <UsageCard
            title="API Calls Today"
            current={usage.apiCallsToday}
            limit={limits.apiCallsPerDay}
            testId="usage-api-calls"
          />
          <UsageCard
            title="Storage"
            current={usage.storageUsedGb}
            limit={limits.storageGb}
            unit=" GB"
            testId="usage-storage"
          />
          <UsageCard
            title="Active Users"
            current={usage.activeUsers}
            limit={limits.maxUsers}
            testId="usage-users"
          />
          <UsageCard
            title="Collections"
            current={usage.collectionsCount}
            limit={limits.maxCollections}
            testId="usage-collections"
          />
        </div>
      </section>

      <section className={styles.section} aria-labelledby="limits-heading">
        <h2 id="limits-heading" className={styles.sectionTitle}>
          Governor Limits
        </h2>
        <div className={styles.limitsTable}>
          <table className={styles.table} role="grid" aria-label="Governor Limits">
            <thead>
              <tr>
                <th>Limit</th>
                <th>Value</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>API Calls / Day</td>
                <td>{limits.apiCallsPerDay.toLocaleString()}</td>
              </tr>
              <tr>
                <td>Storage</td>
                <td>{limits.storageGb} GB</td>
              </tr>
              <tr>
                <td>Max Users</td>
                <td>{limits.maxUsers.toLocaleString()}</td>
              </tr>
              <tr>
                <td>Max Collections</td>
                <td>{limits.maxCollections.toLocaleString()}</td>
              </tr>
              <tr>
                <td>Max Fields / Collection</td>
                <td>{limits.maxFieldsPerCollection.toLocaleString()}</td>
              </tr>
              <tr>
                <td>Max Workflows</td>
                <td>{limits.maxWorkflows.toLocaleString()}</td>
              </tr>
              <tr>
                <td>Max Reports</td>
                <td>{limits.maxReports.toLocaleString()}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}

export default TenantDashboardPage
