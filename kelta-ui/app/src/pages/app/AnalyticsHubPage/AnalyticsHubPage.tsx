import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { BarChart3, FileText, Play } from 'lucide-react'
import { Button } from '../../../components/ui/button'
import { useApi } from '../../../context/ApiContext'
import { useI18n } from '../../../context/I18nContext'

interface HubDashboard {
  id: string
  name: string
  description: string | null
}

interface HubReport {
  id: string
  name: string
  description: string | null
  reportType: string | null
}

/** End-user analytics hub: the dashboards + reports the caller can run. */
export function AnalyticsHubPage() {
  const { t } = useI18n()
  const { apiClient } = useApi()
  const { tenantSlug } = useParams<{ tenantSlug: string }>()

  const { data: dashboards, isLoading: dashboardsLoading } = useQuery({
    queryKey: ['analytics-hub', 'dashboards'],
    staleTime: 5 * 60 * 1000,
    queryFn: () =>
      apiClient.getList<HubDashboard>('/api/dashboards?sort=name&page[size]=100').catch(() => []),
  })
  const { data: reports, isLoading: reportsLoading } = useQuery({
    queryKey: ['analytics-hub', 'reports'],
    staleTime: 5 * 60 * 1000,
    queryFn: () =>
      apiClient.getList<HubReport>('/api/reports?sort=name&page[size]=100').catch(() => []),
  })

  const isLoading = dashboardsLoading || reportsLoading
  const isEmpty = !isLoading && (dashboards?.length ?? 0) === 0 && (reports?.length ?? 0) === 0

  return (
    <main role="main" className="mx-auto w-full max-w-[1180px] px-4 py-6">
      <h1 className="mb-4 text-[26px] font-bold tracking-[-0.01em]">
        {t('analytics.title', 'Analytics')}
      </h1>

      {isLoading ? (
        <div className="h-48 animate-pulse rounded-[10px] bg-muted/40" data-testid="hub-loading" />
      ) : isEmpty ? (
        <div
          className="rounded-[10px] border border-border bg-card p-8 text-center text-sm text-muted-foreground"
          data-testid="hub-empty"
        >
          {t('analytics.hubEmpty', 'No dashboards or reports yet.')}
        </div>
      ) : (
        <>
          {(dashboards?.length ?? 0) > 0 && (
            <section className="mb-6">
              <h2 className="mb-2 text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
                {t('analytics.dashboardsSection', 'Dashboards')}
              </h2>
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {dashboards!.map((d) => (
                  <Link
                    key={d.id}
                    to={`/${tenantSlug}/app/dashboards/${d.id}`}
                    className="rounded-[10px] border border-border bg-card p-4 hover:bg-primary/10"
                    data-testid="hub-dashboard-card"
                  >
                    <div className="flex items-center gap-2">
                      <BarChart3 className="h-4 w-4 text-muted-foreground" aria-hidden />
                      <span className="font-medium truncate">{d.name}</span>
                    </div>
                    {d.description && (
                      <p className="mt-1 line-clamp-2 text-sm text-muted-foreground">
                        {d.description}
                      </p>
                    )}
                  </Link>
                ))}
              </div>
            </section>
          )}

          {(reports?.length ?? 0) > 0 && (
            <section>
              <h2 className="mb-2 text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
                {t('analytics.reportsSection', 'Reports')}
              </h2>
              <div className="rounded-[10px] border border-border bg-card overflow-hidden">
                {reports!.map((r) => (
                  <div
                    key={r.id}
                    className="flex items-center justify-between gap-3 border-b border-border px-4 py-3 last:border-b-0"
                    data-testid="hub-report-row"
                  >
                    <div className="flex min-w-0 items-center gap-2">
                      <FileText className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
                      <span className="truncate font-medium">{r.name}</span>
                      {r.reportType && (
                        <span className="shrink-0 text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
                          {r.reportType}
                        </span>
                      )}
                    </div>
                    <Button asChild size="sm" variant="outline">
                      <Link to={`/${tenantSlug}/app/reports/${r.id}`} data-testid="hub-report-run">
                        <Play className="mr-1.5 h-3.5 w-3.5" aria-hidden />
                        {t('analytics.run', 'Run')}
                      </Link>
                    </Button>
                  </div>
                ))}
              </div>
            </section>
          )}
        </>
      )}
    </main>
  )
}
