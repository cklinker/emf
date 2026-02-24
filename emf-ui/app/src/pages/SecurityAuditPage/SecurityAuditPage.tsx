import React, { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useApi } from '../../context/ApiContext'
import { useTenant } from '../../context/TenantContext'
import { cn } from '@/lib/utils'
import { Shield, AlertTriangle, Activity } from 'lucide-react'

interface SecurityAuditEntry {
  id: string
  tenantId: string
  eventType: string
  eventCategory: string
  actorEmail: string
  targetType: string
  targetId: string
  targetName: string
  details: string
  ipAddress: string
  createdAt: string
}

interface SecurityAuditSummary {
  totalEventsLast24h: number
  authEvents: number
  authzEvents: number
  configEvents: number
  dataEvents: number
  permissionDenials: number
}

export interface SecurityAuditPageProps {
  className?: string
}

const CATEGORY_STYLES: Record<string, string> = {
  AUTH: 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300',
  AUTHZ: 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300',
  CONFIG: 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
  DATA: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300',
}

function CategoryBadge({ category }: { category: string }) {
  return (
    <span
      className={cn(
        'inline-block rounded-full px-2 py-0.5 text-xs font-medium',
        CATEGORY_STYLES[category] || 'bg-muted text-foreground'
      )}
    >
      {category}
    </span>
  )
}

export function SecurityAuditPage({ className }: SecurityAuditPageProps): React.ReactElement {
  const { apiClient } = useApi()
  const { tenantSlug } = useTenant()

  const [page, setPage] = useState(0)

  const {
    data: auditData,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['security-audit', tenantSlug, page],
    queryFn: async () => {
      return apiClient.getPage<SecurityAuditEntry>(
        `/api/setup-audit-entries?page[number]=${page}&page[size]=50`
      )
    },
  })

  const { data: summary } = useQuery({
    queryKey: ['security-audit-summary', tenantSlug],
    queryFn: async () => {
      return apiClient.getOne<SecurityAuditSummary>('/api/setup-audit-entries/summary')
    },
  })

  const entries = auditData?.content ?? []
  const totalPages = auditData?.totalPages ?? 0

  if (error) {
    return (
      <div className={cn('mx-auto max-w-[1200px] p-6', className)}>
        <div className="flex flex-col items-center justify-center gap-4 p-12 text-muted-foreground">
          <p>Failed to load security audit log.</p>
          <button
            onClick={() => refetch()}
            className="cursor-pointer rounded-md border-none bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
          >
            Retry
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className={cn('mx-auto max-w-[1200px] p-6', className)}>
      <header className="mb-6 flex items-center gap-3">
        <Shield className="h-6 w-6 text-muted-foreground" />
        <h1 className="m-0 text-2xl font-semibold">Security Audit Log</h1>
      </header>

      <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div className="rounded-lg border border-border bg-card p-4">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Activity className="h-4 w-4" />
            <span>Events (24h)</span>
          </div>
          <p className="mt-1 text-2xl font-semibold">
            {isLoading ? '--' : (summary?.totalEventsLast24h ?? 0)}
          </p>
        </div>
        <div className="rounded-lg border border-border bg-card p-4">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Shield className="h-4 w-4" />
            <span>Auth Events</span>
          </div>
          <p className="mt-1 text-2xl font-semibold">
            {isLoading ? '--' : (summary?.authEvents ?? 0)}
          </p>
        </div>
        <div className="rounded-lg border border-border bg-card p-4">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <AlertTriangle className="h-4 w-4" />
            <span>Permission Denials</span>
          </div>
          <p className="mt-1 text-2xl font-semibold">
            {isLoading ? '--' : (summary?.permissionDenials ?? 0)}
          </p>
        </div>
      </div>

      {isLoading ? (
        <div className="flex flex-col items-center justify-center p-12 text-muted-foreground">
          Loading audit log...
        </div>
      ) : entries.length === 0 ? (
        <div className="flex flex-col items-center justify-center p-12 text-muted-foreground">
          <p>No audit entries found.</p>
        </div>
      ) : (
        <>
          <div className="overflow-x-auto rounded-lg border border-border bg-card">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr>
                  <th className="border-b-2 border-border p-3 text-left font-semibold text-foreground">
                    Time
                  </th>
                  <th className="border-b-2 border-border p-3 text-left font-semibold text-foreground">
                    Category
                  </th>
                  <th className="border-b-2 border-border p-3 text-left font-semibold text-foreground">
                    Event Type
                  </th>
                  <th className="border-b-2 border-border p-3 text-left font-semibold text-foreground">
                    Actor
                  </th>
                  <th className="border-b-2 border-border p-3 text-left font-semibold text-foreground">
                    Target
                  </th>
                  <th className="border-b-2 border-border p-3 text-left font-semibold text-foreground">
                    IP Address
                  </th>
                </tr>
              </thead>
              <tbody>
                {entries.map((entry) => (
                  <tr key={entry.id} className="hover:bg-muted">
                    <td className="border-b border-border p-3 whitespace-nowrap">
                      {new Date(entry.createdAt).toLocaleString()}
                    </td>
                    <td className="border-b border-border p-3">
                      <CategoryBadge category={entry.eventCategory} />
                    </td>
                    <td className="border-b border-border p-3">{entry.eventType}</td>
                    <td className="border-b border-border p-3">{entry.actorEmail}</td>
                    <td className="border-b border-border p-3">
                      <div className="flex flex-col">
                        <span className="text-foreground">
                          {entry.targetName || entry.targetId || '-'}
                        </span>
                        {entry.targetType && (
                          <span className="text-xs text-muted-foreground">{entry.targetType}</span>
                        )}
                      </div>
                    </td>
                    <td className="border-b border-border p-3 font-mono text-xs">
                      {entry.ipAddress || '-'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div className="mt-4 flex items-center justify-center gap-4 text-sm">
              <button
                className="cursor-pointer rounded-md border border-border bg-card px-4 py-2 text-sm disabled:cursor-not-allowed disabled:opacity-50"
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
              >
                Previous
              </button>
              <span>
                Page {page + 1} of {totalPages}
              </span>
              <button
                className="cursor-pointer rounded-md border border-border bg-card px-4 py-2 text-sm disabled:cursor-not-allowed disabled:opacity-50"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
