/**
 * DashboardPage Component
 *
 * Displays system health status, metrics, and recent errors for the EMF platform.
 * Provides an overview of the platform's operational status.
 *
 * Requirements:
 * - 13.1: Dashboard displays system health status
 * - 13.2: Dashboard displays health status for control plane, database, Kafka, and Redis
 * - 13.3: Dashboard displays key API metrics including request rate, error rate, and latency
 * - 13.4: Dashboard displays recent errors and warnings from the system logs
 * - 13.5: Dashboard allows configuring time range for metrics display
 * - 13.6: Dashboard auto-refreshes at configurable interval
 * - 13.7: Dashboard displays health alerts when services are unhealthy
 */

import React, { useMemo, useState, useCallback } from 'react'
import { useQuery } from '@tanstack/react-query'
import { AlertTriangle } from 'lucide-react'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { LoadingSpinner, ErrorMessage } from '../../components'
import styles from './DashboardPage.module.css'

/**
 * Health status for a service
 */
export interface HealthStatus {
  service: string
  status: 'healthy' | 'unhealthy' | 'unknown'
  details?: string
  lastChecked: string
}

/**
 * Metric data point for time-series data
 */
export interface MetricDataPoint {
  timestamp: string
  value: number
}

/**
 * System metrics data
 */
export interface SystemMetrics {
  requestRate: MetricDataPoint[]
  errorRate: MetricDataPoint[]
  latencyP50: MetricDataPoint[]
  latencyP99: MetricDataPoint[]
}

/**
 * Recent error entry
 */
export interface RecentError {
  id: string
  timestamp: string
  level: 'error' | 'warning'
  message: string
  source: string
  traceId?: string
}

/**
 * Dashboard data from API
 */
export interface DashboardData {
  health: HealthStatus[]
  metrics: SystemMetrics
  recentErrors: RecentError[]
}

/**
 * Time range options for metrics display
 */
export type TimeRangeValue = '5m' | '15m' | '1h' | '6h' | '24h'

/**
 * Auto-refresh interval options
 */
export type RefreshIntervalValue = '10s' | '30s' | '1m' | '5m' | 'off'

/**
 * Time range option configuration
 */
export interface TimeRangeOption {
  value: TimeRangeValue
  label: string
  minutes: number
}

/**
 * Refresh interval option configuration
 */
export interface RefreshIntervalOption {
  value: RefreshIntervalValue
  label: string
  milliseconds: number | null
}

/**
 * Props for DashboardPage component
 */
export interface DashboardPageProps {
  /** Optional test ID for testing */
  testId?: string
}

/**
 * Time range options for metrics display
 */
const TIME_RANGE_OPTIONS: TimeRangeOption[] = [
  { value: '5m', label: '5 minutes', minutes: 5 },
  { value: '15m', label: '15 minutes', minutes: 15 },
  { value: '1h', label: '1 hour', minutes: 60 },
  { value: '6h', label: '6 hours', minutes: 360 },
  { value: '24h', label: '24 hours', minutes: 1440 },
]

/**
 * Auto-refresh interval options
 */
const REFRESH_INTERVAL_OPTIONS: RefreshIntervalOption[] = [
  { value: '10s', label: '10 seconds', milliseconds: 10000 },
  { value: '30s', label: '30 seconds', milliseconds: 30000 },
  { value: '1m', label: '1 minute', milliseconds: 60000 },
  { value: '5m', label: '5 minutes', milliseconds: 300000 },
  { value: 'off', label: 'Off', milliseconds: null },
]

/**
 * HealthCard Component
 * Displays health status for a single service
 */
interface HealthCardProps {
  service: string
  status: 'healthy' | 'unhealthy' | 'unknown'
  details?: string
  lastChecked: string
  testId?: string
}

function HealthCard({
  service,
  status,
  details,
  lastChecked,
  testId,
}: HealthCardProps): React.ReactElement {
  const { t, formatDate } = useI18n()

  const statusLabel = useMemo(() => {
    switch (status) {
      case 'healthy':
        return t('dashboard.healthy')
      case 'unhealthy':
        return t('dashboard.unhealthy')
      default:
        return t('dashboard.unknown')
    }
  }, [status, t])

  const statusClassName = useMemo(() => {
    switch (status) {
      case 'healthy':
        return styles.statusHealthy
      case 'unhealthy':
        return styles.statusUnhealthy
      default:
        return styles.statusUnknown
    }
  }, [status])

  return (
    <div
      className={`${styles.healthCard} ${statusClassName}`}
      data-testid={testId}
      role="article"
      aria-label={`${service} health status: ${statusLabel}`}
    >
      <div className={styles.healthCardHeader}>
        <h3 className={styles.healthCardTitle}>{service}</h3>
        <span
          className={`${styles.statusBadge} ${statusClassName}`}
          aria-label={`Status: ${statusLabel}`}
        >
          {statusLabel}
        </span>
      </div>
      {details && <p className={styles.healthCardDetails}>{details}</p>}
      <p className={styles.healthCardTimestamp}>
        {formatDate(new Date(lastChecked), {
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
        })}
      </p>
    </div>
  )
}

/**
 * MetricsCard Component
 * Displays a single metric with its current value and trend
 */
interface MetricsCardProps {
  title: string
  data: MetricDataPoint[]
  unit?: string
  testId?: string
}

function MetricsCard({ title, data, unit = '', testId }: MetricsCardProps): React.ReactElement {
  const { formatNumber } = useI18n()

  // Calculate current value (latest data point)
  const currentValue = useMemo(() => {
    if (data.length === 0) return 0
    return data[data.length - 1].value
  }, [data])

  // Calculate average value
  const averageValue = useMemo(() => {
    if (data.length === 0) return 0
    const sum = data.reduce((acc, point) => acc + point.value, 0)
    return sum / data.length
  }, [data])

  // Calculate trend (comparing last value to average)
  const trend = useMemo(() => {
    if (data.length < 2) return 'stable'
    const diff = currentValue - averageValue
    const threshold = averageValue * 0.1 // 10% threshold
    if (diff > threshold) return 'up'
    if (diff < -threshold) return 'down'
    return 'stable'
  }, [currentValue, averageValue, data.length])

  const trendIcon = useMemo(() => {
    switch (trend) {
      case 'up':
        return '↑'
      case 'down':
        return '↓'
      default:
        return '→'
    }
  }, [trend])

  // Simple sparkline visualization using CSS
  const sparklineData = useMemo(() => {
    if (data.length === 0) return []
    const maxValue = Math.max(...data.map((d) => d.value))
    const minValue = Math.min(...data.map((d) => d.value))
    const range = maxValue - minValue || 1
    return data.slice(-10).map((d) => ({
      height: ((d.value - minValue) / range) * 100,
      value: d.value,
    }))
  }, [data])

  return (
    <div
      className={styles.metricsCard}
      data-testid={testId}
      role="article"
      aria-label={`${title}: ${formatNumber(currentValue)}${unit}`}
    >
      <h3 className={styles.metricsCardTitle}>{title}</h3>
      <div className={styles.metricsCardValue}>
        <span className={styles.metricsValue}>
          {formatNumber(currentValue, { maximumFractionDigits: 2 })}
          {unit}
        </span>
        <span
          className={`${styles.metricsTrend} ${styles[`trend${trend.charAt(0).toUpperCase() + trend.slice(1)}`]}`}
          aria-label={`Trend: ${trend}`}
        >
          {trendIcon}
        </span>
      </div>
      {sparklineData.length > 0 && (
        <div className={styles.sparkline} aria-hidden="true">
          {sparklineData.map((point, index) => (
            <div
              key={index}
              className={styles.sparklineBar}
              style={{ height: `${Math.max(point.height, 5)}%` }}
              title={`${formatNumber(point.value, { maximumFractionDigits: 2 })}${unit}`}
            />
          ))}
        </div>
      )}
      <p className={styles.metricsCardAverage}>
        Avg: {formatNumber(averageValue, { maximumFractionDigits: 2 })}
        {unit}
      </p>
    </div>
  )
}

/**
 * ErrorsList Component
 * Displays recent errors and warnings
 */
interface ErrorsListProps {
  errors: RecentError[]
  testId?: string
}

function ErrorsList({ errors, testId }: ErrorsListProps): React.ReactElement {
  const { t, formatDate } = useI18n()

  if (errors.length === 0) {
    return (
      <div className={styles.emptyErrors} data-testid={testId}>
        <p>{t('common.noResults')}</p>
      </div>
    )
  }

  return (
    <ul className={styles.errorsList} data-testid={testId} aria-label={t('dashboard.recentErrors')}>
      {errors.map((error) => (
        <li
          key={error.id}
          className={`${styles.errorItem} ${error.level === 'error' ? styles.errorLevelError : styles.errorLevelWarning}`}
          data-testid={`error-item-${error.id}`}
        >
          <div className={styles.errorHeader}>
            <span
              className={`${styles.errorLevel} ${error.level === 'error' ? styles.levelError : styles.levelWarning}`}
              aria-label={`Level: ${error.level}`}
            >
              {error.level.toUpperCase()}
            </span>
            <span className={styles.errorTimestamp}>
              {formatDate(new Date(error.timestamp), {
                month: 'short',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
              })}
            </span>
          </div>
          <p className={styles.errorMessage}>{error.message}</p>
          <div className={styles.errorMeta}>
            <span className={styles.errorSource}>{error.source}</span>
            {error.traceId && (
              <span className={styles.errorTraceId} title="Trace ID">
                {error.traceId.substring(0, 8)}...
              </span>
            )}
          </div>
        </li>
      ))}
    </ul>
  )
}

/**
 * TimeRangeSelector Component
 * Allows selecting the time range for metrics display
 */
interface TimeRangeSelectorProps {
  value: TimeRangeValue
  onChange: (value: TimeRangeValue) => void
  testId?: string
}

function TimeRangeSelector({
  value,
  onChange,
  testId,
}: TimeRangeSelectorProps): React.ReactElement {
  const { t } = useI18n()

  const handleChange = useCallback(
    (event: React.ChangeEvent<HTMLSelectElement>) => {
      onChange(event.target.value as TimeRangeValue)
    },
    [onChange]
  )

  return (
    <div className={styles.controlGroup} data-testid={testId}>
      <label htmlFor="time-range-select" className={styles.controlLabel}>
        {t('dashboard.timeRange')}
      </label>
      <select
        id="time-range-select"
        className={styles.controlSelect}
        value={value}
        onChange={handleChange}
        aria-label={t('dashboard.timeRange')}
      >
        {TIME_RANGE_OPTIONS.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </div>
  )
}

/**
 * RefreshIntervalSelector Component
 * Allows selecting the auto-refresh interval
 */
interface RefreshIntervalSelectorProps {
  value: RefreshIntervalValue
  onChange: (value: RefreshIntervalValue) => void
  testId?: string
}

function RefreshIntervalSelector({
  value,
  onChange,
  testId,
}: RefreshIntervalSelectorProps): React.ReactElement {
  const { t } = useI18n()

  const handleChange = useCallback(
    (event: React.ChangeEvent<HTMLSelectElement>) => {
      onChange(event.target.value as RefreshIntervalValue)
    },
    [onChange]
  )

  return (
    <div className={styles.controlGroup} data-testid={testId}>
      <label htmlFor="refresh-interval-select" className={styles.controlLabel}>
        {t('dashboard.autoRefresh')}
      </label>
      <select
        id="refresh-interval-select"
        className={styles.controlSelect}
        value={value}
        onChange={handleChange}
        aria-label={t('dashboard.autoRefresh')}
      >
        {REFRESH_INTERVAL_OPTIONS.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </div>
  )
}

/**
 * HealthAlerts Component
 * Displays alerts when services are unhealthy
 */
interface HealthAlertsProps {
  healthStatuses: HealthStatus[]
  testId?: string
}

function HealthAlerts({ healthStatuses, testId }: HealthAlertsProps): React.ReactElement | null {
  const { t } = useI18n()

  // Filter for unhealthy services
  const unhealthyServices = useMemo(() => {
    return healthStatuses.filter((health) => health.status === 'unhealthy')
  }, [healthStatuses])

  // Don't render if no unhealthy services
  if (unhealthyServices.length === 0) {
    return null
  }

  return (
    <div className={styles.healthAlerts} data-testid={testId} role="alert" aria-live="polite">
      <div className={styles.healthAlertsHeader}>
        <span className={styles.alertIcon} aria-hidden="true">
          <AlertTriangle size={18} />
        </span>
        <h3 className={styles.healthAlertsTitle}>{t('dashboard.healthAlerts')}</h3>
      </div>
      {/* eslint-disable-next-line jsx-a11y/no-redundant-roles */}
      <ul className={styles.alertsList} role="list">
        {unhealthyServices.map((health) => (
          <li
            key={health.service}
            className={styles.alertItem}
            data-testid={`health-alert-${health.service.toLowerCase().replace(/\s+/g, '-')}`}
          >
            <span className={styles.alertServiceName}>{health.service}</span>
            {health.details && <span className={styles.alertDetails}>{health.details}</span>}
          </li>
        ))}
      </ul>
    </div>
  )
}

/**
 * DashboardPage Component
 *
 * Main dashboard page for monitoring EMF platform health and metrics.
 * Displays health status cards, metrics charts, and recent errors.
 */
export function DashboardPage({
  testId = 'dashboard-page',
}: DashboardPageProps): React.ReactElement {
  const { t } = useI18n()
  const { apiClient } = useApi()

  // State for time range and refresh interval
  const [timeRange, setTimeRange] = useState<TimeRangeValue>('15m')
  const [refreshInterval, setRefreshInterval] = useState<RefreshIntervalValue>('30s')

  // Calculate refresh interval in milliseconds
  const refreshIntervalMs = useMemo(() => {
    const option = REFRESH_INTERVAL_OPTIONS.find((opt) => opt.value === refreshInterval)
    return option?.milliseconds ?? false
  }, [refreshInterval])

  // Fetch dashboard data with time range parameter
  const {
    data: dashboardData,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['dashboard', timeRange],
    queryFn: () => apiClient.get<DashboardData>(`/control/dashboard?timeRange=${timeRange}`),
    refetchInterval: refreshIntervalMs,
  })

  // Handle time range change
  const handleTimeRangeChange = useCallback((value: TimeRangeValue) => {
    setTimeRange(value)
  }, [])

  // Handle refresh interval change
  const handleRefreshIntervalChange = useCallback((value: RefreshIntervalValue) => {
    setRefreshInterval(value)
  }, [])

  // Default health statuses for the four main services
  const healthStatuses = useMemo(() => {
    if (!dashboardData?.health) {
      return [
        {
          service: 'Control Plane',
          status: 'unknown' as const,
          lastChecked: new Date().toISOString(),
        },
        { service: 'Database', status: 'unknown' as const, lastChecked: new Date().toISOString() },
        { service: 'Kafka', status: 'unknown' as const, lastChecked: new Date().toISOString() },
        { service: 'Redis', status: 'unknown' as const, lastChecked: new Date().toISOString() },
      ]
    }
    return dashboardData.health
  }, [dashboardData])

  // Default metrics
  const metrics = useMemo(() => {
    if (!dashboardData?.metrics) {
      return {
        requestRate: [],
        errorRate: [],
        latencyP50: [],
        latencyP99: [],
      }
    }
    return dashboardData.metrics
  }, [dashboardData])

  // Recent errors
  const recentErrors = useMemo(() => {
    return dashboardData?.recentErrors || []
  }, [dashboardData])

  // Render loading state
  if (isLoading) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingContainer}>
          <LoadingSpinner size="large" label={t('common.loading')} />
        </div>
      </div>
    )
  }

  // Render error state
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
      {/* Page Header */}
      <header className={styles.header}>
        <h1 className={styles.title}>{t('dashboard.title')}</h1>
        <div className={styles.controls} data-testid="dashboard-controls">
          <TimeRangeSelector
            value={timeRange}
            onChange={handleTimeRangeChange}
            testId="time-range-selector"
          />
          <RefreshIntervalSelector
            value={refreshInterval}
            onChange={handleRefreshIntervalChange}
            testId="refresh-interval-selector"
          />
        </div>
      </header>

      {/* Health Alerts Section */}
      <HealthAlerts healthStatuses={healthStatuses} testId="health-alerts" />

      {/* System Health Section */}
      <section className={styles.section} aria-labelledby="health-heading">
        <h2 id="health-heading" className={styles.sectionTitle}>
          {t('dashboard.systemHealth')}
        </h2>
        <div className={styles.healthGrid} data-testid="health-cards">
          {healthStatuses.map((health, index) => (
            <HealthCard
              key={health.service}
              service={health.service}
              status={health.status}
              details={health.details}
              lastChecked={health.lastChecked}
              testId={`health-card-${index}`}
            />
          ))}
        </div>
      </section>

      {/* Metrics Section */}
      <section className={styles.section} aria-labelledby="metrics-heading">
        <h2 id="metrics-heading" className={styles.sectionTitle}>
          {t('dashboard.metrics')}
        </h2>
        <div className={styles.metricsGrid} data-testid="metrics-cards">
          <MetricsCard
            title={t('dashboard.requestRate')}
            data={metrics.requestRate}
            unit="/s"
            testId="metrics-request-rate"
          />
          <MetricsCard
            title={t('dashboard.errorRate')}
            data={metrics.errorRate}
            unit="%"
            testId="metrics-error-rate"
          />
          <MetricsCard
            title={`${t('dashboard.latency')} (P50)`}
            data={metrics.latencyP50}
            unit="ms"
            testId="metrics-latency-p50"
          />
          <MetricsCard
            title={`${t('dashboard.latency')} (P99)`}
            data={metrics.latencyP99}
            unit="ms"
            testId="metrics-latency-p99"
          />
        </div>
      </section>

      {/* Recent Errors Section */}
      <section className={styles.section} aria-labelledby="errors-heading">
        <h2 id="errors-heading" className={styles.sectionTitle}>
          {t('dashboard.recentErrors')}
        </h2>
        <ErrorsList errors={recentErrors} testId="errors-list" />
      </section>
    </div>
  )
}

export default DashboardPage
