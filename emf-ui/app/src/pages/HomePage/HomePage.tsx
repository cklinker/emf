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
import { useCollectionSummaries } from '../../hooks/useCollectionSummaries'
import { formatRelativeTime } from '../../utils/formatRelativeTime'
import { cn } from '@/lib/utils'

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
  const { summaries: collectionsList } = useCollectionSummaries()

  const topCollections = useMemo(() => collectionsList.slice(0, 5), [collectionsList])

  const recentDisplay = useMemo(() => recentRecords.slice(0, 15), [recentRecords])

  const favoriteRecords = useMemo(
    () => favorites.filter((f) => f.type === 'record').slice(0, 10),
    [favorites]
  )

  return (
    <div className="mx-auto max-w-[1200px] p-6 max-[767px]:p-4" data-testid={testId}>
      <div className="mb-6">
        <h1 className="mb-1 text-[1.75rem] font-bold text-foreground max-[767px]:text-[1.375rem]">
          {t('home.welcome')}
        </h1>
        <p className="m-0 text-[0.9375rem] text-muted-foreground">{t('home.subtitle')}</p>
      </div>

      {/* Quick Create Buttons */}
      {topCollections.length > 0 && (
        <section className="mb-6" data-testid="quick-create-section">
          <h2 className="mb-4 text-base font-semibold text-foreground">{t('home.quickCreate')}</h2>
          <div className="flex flex-wrap gap-2 max-[767px]:flex-nowrap max-[767px]:overflow-x-auto max-[767px]:pb-1">
            {topCollections.map((col) => (
              <button
                key={col.name}
                className={cn(
                  'flex shrink-0 items-center gap-1 rounded-md border border-border bg-card px-4 py-2 text-sm font-medium text-foreground',
                  'cursor-pointer transition-all duration-150',
                  'hover:border-primary hover:text-primary hover:bg-primary/5',
                  'focus:outline-2 focus:outline-offset-2 focus:outline-ring',
                  'focus:not(:focus-visible):outline-none'
                )}
                onClick={() => navigate(`/${getTenantSlug()}/resources/${col.name}/new`)}
              >
                <span className="text-lg font-bold leading-none" aria-hidden="true">
                  +
                </span>
                <span className="whitespace-nowrap">{col.displayName || col.name}</span>
              </button>
            ))}
          </div>
        </section>
      )}

      <div className="grid grid-cols-2 gap-6 max-[767px]:grid-cols-1">
        {/* Recent Records */}
        <section className="mb-6" data-testid="recent-records-section">
          <div className="mb-4 flex items-center gap-2">
            <h2 className="m-0 text-base font-semibold text-foreground">
              {t('home.recentRecords')}
            </h2>
          </div>
          {recentDisplay.length === 0 ? (
            <div className="flex items-center justify-center rounded-md border border-dashed border-border bg-card p-6">
              <p className="m-0 text-sm text-muted-foreground">{t('home.noRecentRecords')}</p>
            </div>
          ) : (
            <ul className="m-0 list-none overflow-hidden rounded-md border border-border bg-card p-0">
              {recentDisplay.map((record, idx) => (
                <li key={`${record.collectionName}-${record.id}-${idx}`}>
                  <Link
                    to={`/${getTenantSlug()}/resources/${record.collectionName}/${record.id}`}
                    className={cn(
                      'flex items-center justify-between border-b border-border px-4 py-2 text-inherit no-underline',
                      'transition-colors duration-150',
                      'hover:bg-accent',
                      'focus:outline-2 focus:outline-offset-[-2px] focus:outline-ring',
                      'focus:not(:focus-visible):outline-none',
                      'last:border-b-0'
                    )}
                  >
                    <div className="flex min-w-0 flex-col gap-0.5">
                      <span className="truncate text-sm font-medium text-foreground">
                        {record.displayValue}
                      </span>
                      <span className="text-xs text-muted-foreground">
                        {record.collectionDisplayName || record.collectionName}
                      </span>
                    </div>
                    <span className="ml-4 shrink-0 whitespace-nowrap text-xs text-muted-foreground">
                      {formatRelativeTime(record.viewedAt)}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* Pending Approvals */}
        <section className="mb-6" data-testid="pending-approvals-section">
          <div className="mb-4 flex items-center gap-2">
            <h2 className="m-0 text-base font-semibold text-foreground">
              {t('home.pendingApprovals')}
            </h2>
            {approvalsList.length > 0 && (
              <span className="inline-flex h-6 min-w-[1.5rem] items-center justify-center rounded-full bg-primary px-1 text-xs font-semibold text-primary-foreground">
                {approvalsList.length}
              </span>
            )}
          </div>
          {approvalsList.length === 0 ? (
            <div className="flex items-center justify-center rounded-md border border-dashed border-border bg-card p-6">
              <p className="m-0 text-sm text-muted-foreground">{t('home.noApprovals')}</p>
            </div>
          ) : (
            <ul className="m-0 list-none overflow-hidden rounded-md border border-border bg-card p-0">
              {approvalsList.slice(0, 5).map((approval) => (
                <li key={approval.id}>
                  <Link
                    to={`/${getTenantSlug()}/resources/${approval.collectionId}/${approval.recordId}`}
                    className={cn(
                      'flex items-center justify-between border-b border-border px-4 py-2 text-inherit no-underline',
                      'transition-colors duration-150',
                      'hover:bg-accent',
                      'focus:outline-2 focus:outline-offset-[-2px] focus:outline-ring',
                      'focus:not(:focus-visible):outline-none',
                      'last:border-b-0'
                    )}
                  >
                    <div className="flex min-w-0 flex-col gap-0.5">
                      <span className="truncate text-sm font-medium text-foreground">
                        {approval.approvalProcessName}
                      </span>
                      <span className="text-xs text-muted-foreground">
                        {t('home.submittedBy')}: {approval.submittedBy}
                      </span>
                    </div>
                    <span className="ml-4 inline-flex shrink-0 items-center whitespace-nowrap rounded-full bg-amber-100 px-2 py-0.5 text-[0.6875rem] font-semibold text-amber-800 dark:bg-amber-950 dark:text-amber-300">
                      {t('home.pending')}
                    </span>
                  </Link>
                </li>
              ))}
              {approvalsList.length > 5 && (
                <li>
                  <Link
                    to={`/${getTenantSlug()}/approvals`}
                    className="block px-4 py-2 text-center text-[0.8125rem] font-medium text-primary no-underline hover:underline"
                  >
                    {t('home.viewAll')} ({approvalsList.length})
                  </Link>
                </li>
              )}
            </ul>
          )}
        </section>

        {/* Favorites */}
        <section className="mb-6" data-testid="favorites-section">
          <div className="mb-4 flex items-center gap-2">
            <h2 className="m-0 text-base font-semibold text-foreground">{t('home.favorites')}</h2>
          </div>
          {favoriteRecords.length === 0 ? (
            <div className="flex items-center justify-center rounded-md border border-dashed border-border bg-card p-6">
              <p className="m-0 text-sm text-muted-foreground">{t('home.noFavorites')}</p>
            </div>
          ) : (
            <ul className="m-0 list-none overflow-hidden rounded-md border border-border bg-card p-0">
              {favoriteRecords.map((fav) => (
                <li key={`${fav.type}-${fav.id}`}>
                  <Link
                    to={`/${getTenantSlug()}/resources/${fav.collectionName}/${fav.id}`}
                    className={cn(
                      'flex items-center justify-between border-b border-border px-4 py-2 text-inherit no-underline',
                      'transition-colors duration-150',
                      'hover:bg-accent',
                      'focus:outline-2 focus:outline-offset-[-2px] focus:outline-ring',
                      'focus:not(:focus-visible):outline-none',
                      'last:border-b-0'
                    )}
                  >
                    <div className="flex min-w-0 flex-col gap-0.5">
                      <span className="truncate text-sm font-medium text-foreground">
                        {fav.displayValue}
                      </span>
                      <span className="text-xs text-muted-foreground">
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
