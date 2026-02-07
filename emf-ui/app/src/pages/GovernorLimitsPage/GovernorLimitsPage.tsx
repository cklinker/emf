import React from 'react'
import { useQuery } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { LoadingSpinner, ErrorMessage } from '../../components'
import styles from './GovernorLimitsPage.module.css'

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

  if (!status) return <div className={styles.empty}>{t('governorLimits.noData')}</div>

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
    <div className={`${styles.container} ${className || ''}`}>
      <div className={styles.header}>
        <h1 className={styles.title}>{t('governorLimits.title')}</h1>
      </div>

      <div className={styles.grid}>
        {metrics.map((metric) => {
          const percentage = metric.limit > 0 ? (metric.used / metric.limit) * 100 : 0
          const isWarning = percentage >= 80
          const isCritical = percentage >= 95

          return (
            <div key={metric.label} className={styles.card}>
              <div className={styles.cardHeader}>
                <h3 className={styles.cardTitle}>{metric.label}</h3>
                {isWarning && (
                  <span
                    className={`${styles.alert} ${isCritical ? styles.alertCritical : styles.alertWarning}`}
                  >
                    {isCritical ? t('governorLimits.critical') : t('governorLimits.warning')}
                  </span>
                )}
              </div>
              <div className={styles.cardBody}>
                <div className={styles.usage}>
                  <span className={styles.usageValue}>{metric.used.toLocaleString()}</span>
                  <span className={styles.usageSeparator}>/</span>
                  <span className={styles.usageLimit}>{metric.limit.toLocaleString()}</span>
                </div>
                <div className={styles.progressBar}>
                  <div
                    className={`${styles.progressFill} ${isCritical ? styles.progressCritical : isWarning ? styles.progressWarning : styles.progressNormal}`}
                    style={{ width: `${Math.min(percentage, 100)}%` }}
                  />
                </div>
                <div className={styles.percentage}>{percentage.toFixed(1)}%</div>
              </div>
            </div>
          )
        })}
      </div>

      <div className={styles.limitsDetail}>
        <h2 className={styles.sectionTitle}>{t('governorLimits.allLimits')}</h2>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>{t('governorLimits.limitName')}</th>
              <th>{t('governorLimits.limitValue')}</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>{t('governorLimits.apiCallsPerDay')}</td>
              <td>{status.limits.apiCallsPerDay.toLocaleString()}</td>
            </tr>
            <tr>
              <td>{t('governorLimits.storageGb')}</td>
              <td>{status.limits.storageGb} GB</td>
            </tr>
            <tr>
              <td>{t('governorLimits.maxUsers')}</td>
              <td>{status.limits.maxUsers.toLocaleString()}</td>
            </tr>
            <tr>
              <td>{t('governorLimits.maxCollections')}</td>
              <td>{status.limits.maxCollections.toLocaleString()}</td>
            </tr>
            <tr>
              <td>{t('governorLimits.maxFieldsPerCollection')}</td>
              <td>{status.limits.maxFieldsPerCollection.toLocaleString()}</td>
            </tr>
            <tr>
              <td>{t('governorLimits.maxWorkflows')}</td>
              <td>{status.limits.maxWorkflows.toLocaleString()}</td>
            </tr>
            <tr>
              <td>{t('governorLimits.maxReports')}</td>
              <td>{status.limits.maxReports.toLocaleString()}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  )
}

export default GovernorLimitsPage
