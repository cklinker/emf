import React, { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useApi } from '../../context/ApiContext'
import { LoadingSpinner, ErrorMessage } from '../../components'
import { SupersetEmbed } from '../../components/SupersetEmbed'
import { Button } from '@/components/ui/button'
import { ArrowLeft, BarChart3, ExternalLink, RefreshCw } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { SupersetDashboard } from '@kelta/sdk'

export interface AnalyticsPageProps {
  testId?: string
}

function getSupersetUrl(): string {
  return (
    ((window as Record<string, unknown>).__SUPERSET_URL__ as string | undefined) ||
    `${window.location.protocol}//superset.rzware.com`
  )
}

export function AnalyticsPage({
  testId = 'analytics-page',
}: AnalyticsPageProps): React.ReactElement {
  const { keltaClient } = useApi()
  const [selectedDashboard, setSelectedDashboard] = useState<SupersetDashboard | null>(null)
  const supersetUrl = getSupersetUrl()

  const {
    data: dashboards,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['superset', 'dashboards'],
    queryFn: () => keltaClient.admin.superset.listDashboards(),
  })

  // Embedded dashboard view
  if (selectedDashboard) {
    return (
      <div className="flex flex-col" style={{ height: 'calc(100vh - 160px)' }} data-testid={testId}>
        <div className="flex shrink-0 items-center gap-3 border-b px-6 py-3">
          <Button variant="ghost" size="sm" onClick={() => setSelectedDashboard(null)}>
            <ArrowLeft className="mr-1 h-4 w-4" />
            Back
          </Button>
          <h2 className="text-lg font-semibold">{selectedDashboard.dashboard_title}</h2>
        </div>
        <div className="min-h-0 flex-1 p-4">
          <SupersetEmbed
            dashboardId={selectedDashboard.embedded_id || String(selectedDashboard.id)}
            className="h-full w-full rounded-lg border"
          />
        </div>
      </div>
    )
  }

  // Dashboard list view
  return (
    <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Analytics</h1>
          <p className="text-muted-foreground mt-1 text-sm">
            View dashboards and reports powered by Superset
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            <RefreshCw className="mr-1 h-4 w-4" />
            Refresh
          </Button>
          <Button variant="outline" size="sm" asChild>
            <a href={supersetUrl} target="_blank" rel="noopener noreferrer">
              <ExternalLink className="mr-1 h-4 w-4" />
              Open in Superset
            </a>
          </Button>
        </div>
      </div>

      {/* Content */}
      {isLoading && (
        <div className="flex min-h-[400px] items-center justify-center">
          <LoadingSpinner size="large" label="Loading dashboards..." />
        </div>
      )}

      {error && (
        <ErrorMessage
          error="Failed to load dashboards. Superset may not be configured."
          onRetry={() => refetch()}
        />
      )}

      {!isLoading && !error && dashboards && dashboards.length === 0 && (
        <div className="flex min-h-[400px] flex-col items-center justify-center text-center">
          <BarChart3 className="text-muted-foreground mb-4 h-12 w-12" />
          <h3 className="text-lg font-medium">No dashboards available</h3>
          <p className="text-muted-foreground mt-1 max-w-md text-sm">
            Dashboards are created in Apache Superset. Once published, they will appear here for
            viewing.
          </p>
          <Button variant="outline" className="mt-4" asChild>
            <a href={supersetUrl} target="_blank" rel="noopener noreferrer">
              <ExternalLink className="mr-1 h-4 w-4" />
              Create in Superset
            </a>
          </Button>
        </div>
      )}

      {!isLoading && !error && dashboards && dashboards.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {dashboards.map((dashboard) => (
            <button
              key={dashboard.id}
              onClick={() => setSelectedDashboard(dashboard)}
              className={cn(
                'group relative flex flex-col rounded-lg border bg-card p-5 text-left',
                'transition-colors hover:border-primary/50 hover:bg-accent/50',
                'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring'
              )}
            >
              <div className="mb-3 flex items-center gap-2">
                <BarChart3 className="text-muted-foreground h-5 w-5" />
                <h3 className="font-medium leading-tight">{dashboard.dashboard_title}</h3>
              </div>
              {dashboard.status && (
                <span
                  className={cn(
                    'mt-auto self-start rounded-full px-2 py-0.5 text-xs font-medium',
                    dashboard.published
                      ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
                      : 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400'
                  )}
                >
                  {dashboard.published ? 'Published' : 'Draft'}
                </span>
              )}
              {dashboard.changed_on_utc && (
                <p className="text-muted-foreground mt-2 text-xs">
                  Updated {new Date(dashboard.changed_on_utc).toLocaleDateString()}
                </p>
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
