import React, { useState, useCallback } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { LoadingSpinner, ErrorMessage } from '../../components'
import styles from './SetupAuditTrailPage.module.css'

export interface SetupAuditTrailPageProps {
  className?: string
}

interface AuditEntry {
  id: string
  userId: string
  action: string
  section: string
  entityType: string
  entityId?: string
  entityName?: string
  oldValue?: string
  newValue?: string
  timestamp: string
}

export function SetupAuditTrailPage({ className }: SetupAuditTrailPageProps): React.ReactElement {
  const { t } = useI18n()
  const { adminClient } = useApi()

  const [section, setSection] = useState<string>('')
  const [entityType, setEntityType] = useState<string>('')
  const [page, setPage] = useState(0)
  const [expandedRows, setExpandedRows] = useState<Set<string>>(new Set())

  const {
    data: auditData,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['audit-trail', section, entityType, page],
    queryFn: () =>
      adminClient.audit.list({
        section: section || undefined,
        entityType: entityType || undefined,
        page,
        size: 50,
      }),
  })

  const toggleRow = useCallback((id: string) => {
    setExpandedRows((prev) => {
      const next = new Set(prev)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      return next
    })
  }, [])

  if (isLoading) return <LoadingSpinner />
  if (error) return <ErrorMessage message={t('audit.loadError')} />

  const entries: AuditEntry[] = auditData?.content || []
  const totalPages = auditData?.totalPages || 0

  return (
    <div className={`${styles.container} ${className || ''}`}>
      <div className={styles.header}>
        <h1 className={styles.title}>{t('audit.title')}</h1>
      </div>

      <div className={styles.filters}>
        <div className={styles.filterGroup}>
          <label htmlFor="section-filter">{t('audit.section')}</label>
          <select
            id="section-filter"
            value={section}
            onChange={(e) => {
              setSection(e.target.value)
              setPage(0)
            }}
          >
            <option value="">{t('audit.allSections')}</option>
            <option value="Collections">{t('audit.sectionCollections')}</option>
            <option value="Fields">{t('audit.sectionFields')}</option>
            <option value="Profiles">{t('audit.sectionProfiles')}</option>
            <option value="PermissionSets">{t('audit.sectionPermissionSets')}</option>
            <option value="OIDC">{t('audit.sectionOIDC')}</option>
            <option value="Tenants">{t('audit.sectionTenants')}</option>
            <option value="Sharing">{t('audit.sectionSharing')}</option>
          </select>
        </div>
        <div className={styles.filterGroup}>
          <label htmlFor="entity-filter">{t('audit.entityType')}</label>
          <input
            id="entity-filter"
            type="text"
            value={entityType}
            onChange={(e) => {
              setEntityType(e.target.value)
              setPage(0)
            }}
            placeholder={t('audit.entityTypePlaceholder')}
          />
        </div>
      </div>

      {entries.length === 0 ? (
        <div className={styles.empty}>
          <p>{t('audit.noEntries')}</p>
        </div>
      ) : (
        <>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>{t('audit.timestamp')}</th>
                <th>{t('audit.action')}</th>
                <th>{t('audit.section')}</th>
                <th>{t('audit.entityType')}</th>
                <th>{t('audit.entityName')}</th>
                <th>{t('audit.user')}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {entries.map((entry) => (
                <React.Fragment key={entry.id}>
                  <tr
                    className={expandedRows.has(entry.id) ? styles.rowExpanded : ''}
                    onClick={() => toggleRow(entry.id)}
                  >
                    <td>{new Date(entry.timestamp).toLocaleString()}</td>
                    <td>
                      <span className={`${styles.badge} ${styles[`badge${entry.action}`]}`}>
                        {entry.action}
                      </span>
                    </td>
                    <td>{entry.section}</td>
                    <td>{entry.entityType}</td>
                    <td>{entry.entityName || entry.entityId || '-'}</td>
                    <td>{entry.userId}</td>
                    <td>
                      <span className={styles.expandIcon}>
                        {expandedRows.has(entry.id) ? '\u25BC' : '\u25B6'}
                      </span>
                    </td>
                  </tr>
                  {expandedRows.has(entry.id) && (
                    <tr className={styles.detailRow}>
                      <td colSpan={7}>
                        <div className={styles.diffView}>
                          <div className={styles.diffColumn}>
                            <h4>{t('audit.oldValue')}</h4>
                            <pre>{formatJson(entry.oldValue)}</pre>
                          </div>
                          <div className={styles.diffColumn}>
                            <h4>{t('audit.newValue')}</h4>
                            <pre>{formatJson(entry.newValue)}</pre>
                          </div>
                        </div>
                      </td>
                    </tr>
                  )}
                </React.Fragment>
              ))}
            </tbody>
          </table>

          {totalPages > 1 && (
            <div className={styles.pagination}>
              <button
                className={styles.btn}
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
              >
                {t('common.previous')}
              </button>
              <span>
                {t('audit.pageInfo', { current: String(page + 1), total: String(totalPages) })}
              </span>
              <button
                className={styles.btn}
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
              >
                {t('common.next')}
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}

function formatJson(value?: string): string {
  if (!value) return '-'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

export default SetupAuditTrailPage
