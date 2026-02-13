/**
 * HomePage Component
 *
 * User-centric landing page showing recent records, pending approvals,
 * favorites, and quick-create buttons. Replaces the system health dashboard
 * as the default route.
 */

import { useMemo } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { getTenantSlug } from '../../context/TenantContext'
import { useApi } from '../../context/ApiContext'
import { useAuth } from '../../context/AuthContext'
import { useRecentRecords } from '../../hooks/useRecentRecords'
import { useFavorites } from '../../hooks/useFavorites'
import { formatRelativeTime } from '../../utils/formatRelativeTime'
import styles from './HomePage.module.css'

export interface HomePageProps {
  testId?: string
}

interface ApprovalInstance {
  id: string
  approvalProcessName: string
  collectionId: string
  recordId: string
  submittedBy: string
  status: string
  submittedAt: string
}

interface CollectionSummary {
  name: string
  displayName: string
  recordCount: number
}

export function HomePage({ testId = 'home-page' }: HomePageProps): JSX.Element {
  const { t } = useI18n()
  const { apiClient } = useApi()
  const { user } = useAuth()
  const navigate = useNavigate()
  const userId = user?.id ?? 'anonymous'

  const { recentRecords } = useRecentRecords(userId)
  const { favorites } = useFavorites(userId)

  // Fetch pending approvals
  const { data: pendingApprovals } = useQuery({
    queryKey: ['pending-approvals'],
    queryFn: () =>
      apiClient.get<ApprovalInstance[]>('/control/approvals/instances/pending?userId=system'),
    staleTime: 60000,
  })

  const approvalsList: ApprovalInstance[] = useMemo(
    () => (Array.isArray(pendingApprovals) ? pendingApprovals : []),
    [pendingApprovals]
  )

  // Fetch collections for quick-create
  const { data: collections } = useQuery({
    queryKey: ['collections-summary'],
    queryFn: () => apiClient.get<CollectionSummary[]>('/control/collections'),
    staleTime: 300000,
  })

  const collectionsList: CollectionSummary[] = useMemo(
    () => (Array.isArray(collections) ? collections : []),
    [collections]
  )

  const topCollections = useMemo(() => collectionsList.slice(0, 5), [collectionsList])

  const recentDisplay = useMemo(() => recentRecords.slice(0, 15), [recentRecords])

  const favoriteRecords = useMemo(
    () => favorites.filter((f) => f.type === 'record').slice(0, 10),
    [favorites]
  )

  return (
    <div className={styles.container} data-testid={testId}>
      <div className={styles.header}>
        <h1 className={styles.title}>{t('home.welcome')}</h1>
        <p className={styles.subtitle}>{t('home.subtitle')}</p>
      </div>

      {/* Quick Create Buttons */}
      {topCollections.length > 0 && (
        <section className={styles.section} data-testid="quick-create-section">
          <h2 className={styles.sectionTitle}>{t('home.quickCreate')}</h2>
          <div className={styles.quickCreateGrid}>
            {topCollections.map((col) => (
              <button
                key={col.name}
                className={styles.quickCreateButton}
                onClick={() => navigate(`/${getTenantSlug()}/resources/${col.name}/new`)}
              >
                <span className={styles.quickCreateIcon} aria-hidden="true">
                  +
                </span>
                <span className={styles.quickCreateLabel}>{col.displayName || col.name}</span>
              </button>
            ))}
          </div>
        </section>
      )}

      <div className={styles.grid}>
        {/* Recent Records */}
        <section className={styles.section} data-testid="recent-records-section">
          <div className={styles.sectionHeader}>
            <h2 className={styles.sectionTitle}>{t('home.recentRecords')}</h2>
          </div>
          {recentDisplay.length === 0 ? (
            <div className={styles.emptyState}>
              <p>{t('home.noRecentRecords')}</p>
            </div>
          ) : (
            <ul className={styles.recordList}>
              {recentDisplay.map((record, idx) => (
                <li key={`${record.collectionName}-${record.id}-${idx}`}>
                  <Link
                    to={`/${getTenantSlug()}/resources/${record.collectionName}/${record.id}`}
                    className={styles.recordItem}
                  >
                    <div className={styles.recordInfo}>
                      <span className={styles.recordName}>{record.displayValue}</span>
                      <span className={styles.recordCollection}>
                        {record.collectionDisplayName || record.collectionName}
                      </span>
                    </div>
                    <span className={styles.recordTime}>{formatRelativeTime(record.viewedAt)}</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* Pending Approvals */}
        <section className={styles.section} data-testid="pending-approvals-section">
          <div className={styles.sectionHeader}>
            <h2 className={styles.sectionTitle}>{t('home.pendingApprovals')}</h2>
            {approvalsList.length > 0 && (
              <span className={styles.badge}>{approvalsList.length}</span>
            )}
          </div>
          {approvalsList.length === 0 ? (
            <div className={styles.emptyState}>
              <p>{t('home.noApprovals')}</p>
            </div>
          ) : (
            <ul className={styles.recordList}>
              {approvalsList.slice(0, 5).map((approval) => (
                <li key={approval.id}>
                  <Link
                    to={`/${getTenantSlug()}/resources/${approval.collectionId}/${approval.recordId}`}
                    className={styles.recordItem}
                  >
                    <div className={styles.recordInfo}>
                      <span className={styles.recordName}>{approval.approvalProcessName}</span>
                      <span className={styles.recordCollection}>
                        {t('home.submittedBy')}: {approval.submittedBy}
                      </span>
                    </div>
                    <span className={styles.approvalBadge}>{t('home.pending')}</span>
                  </Link>
                </li>
              ))}
              {approvalsList.length > 5 && (
                <li>
                  <Link to={`/${getTenantSlug()}/approvals`} className={styles.viewAll}>
                    {t('home.viewAll')} ({approvalsList.length})
                  </Link>
                </li>
              )}
            </ul>
          )}
        </section>

        {/* Favorites */}
        <section className={styles.section} data-testid="favorites-section">
          <div className={styles.sectionHeader}>
            <h2 className={styles.sectionTitle}>{t('home.favorites')}</h2>
          </div>
          {favoriteRecords.length === 0 ? (
            <div className={styles.emptyState}>
              <p>{t('home.noFavorites')}</p>
            </div>
          ) : (
            <ul className={styles.recordList}>
              {favoriteRecords.map((fav) => (
                <li key={`${fav.type}-${fav.id}`}>
                  <Link
                    to={`/${getTenantSlug()}/resources/${fav.collectionName}/${fav.id}`}
                    className={styles.recordItem}
                  >
                    <div className={styles.recordInfo}>
                      <span className={styles.recordName}>{fav.displayValue}</span>
                      <span className={styles.recordCollection}>
                        {fav.collectionDisplayName || fav.collectionName}
                      </span>
                    </div>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  )
}

export default HomePage
