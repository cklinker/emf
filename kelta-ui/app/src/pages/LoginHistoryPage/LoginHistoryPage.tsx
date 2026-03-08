import React, { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useApi } from '../../context/ApiContext'
import { useTenant } from '../../context/TenantContext'
import { cn } from '@/lib/utils'
import { Clock } from 'lucide-react'

interface LoginHistoryEntry {
  id: string
  userId: string
  loginTime: string
  sourceIp: string
  loginType: 'UI' | 'API' | 'OAUTH' | 'SERVICE_ACCOUNT'
  status: 'SUCCESS' | 'FAILED' | 'LOCKED_OUT'
  userAgent: string
}

export interface LoginHistoryPageProps {
  testId?: string
}

const STATUS_STYLES: Record<string, string> = {
  SUCCESS: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300',
  FAILED: 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300',
  LOCKED_OUT: 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
}

function StatusBadge({ status }: { status: string }) {
  return (
    <span
      className={cn(
        'inline-block rounded-full px-2 py-0.5 text-xs font-medium',
        STATUS_STYLES[status] || 'bg-muted text-foreground'
      )}
    >
      {status}
    </span>
  )
}

export function LoginHistoryPage({
  testId = 'login-history-page',
}: LoginHistoryPageProps): React.ReactElement {
  const { apiClient } = useApi()
  const { tenantSlug } = useTenant()

  const [page, setPage] = useState(0)

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['login-history', tenantSlug, page],
    queryFn: async () => {
      return apiClient.getPage<LoginHistoryEntry>(
        `/api/login-history?page[number]=${page}&page[size]=50&sort=-loginTime`
      )
    },
  })

  const entries = data?.content ?? []
  const totalPages = data?.totalPages ?? 0

  if (error) {
    return (
      <div className="mx-auto max-w-[1200px] p-6" data-testid={testId}>
        <div className="flex flex-col items-center justify-center gap-4 p-12 text-muted-foreground">
          <p>Failed to load login history.</p>
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
    <div className="mx-auto max-w-[1200px] p-6" data-testid={testId}>
      <header className="mb-6 flex items-center gap-3">
        <Clock className="h-6 w-6 text-muted-foreground" />
        <h1 className="m-0 text-2xl font-semibold">Login History</h1>
      </header>

      {isLoading ? (
        <div className="flex flex-col items-center justify-center p-12 text-muted-foreground">
          Loading login history...
        </div>
      ) : entries.length === 0 ? (
        <div className="flex flex-col items-center justify-center p-12 text-muted-foreground">
          <p>No login events found.</p>
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
                    Source IP
                  </th>
                  <th className="border-b-2 border-border p-3 text-left font-semibold text-foreground">
                    Login Type
                  </th>
                  <th className="border-b-2 border-border p-3 text-left font-semibold text-foreground">
                    Status
                  </th>
                  <th className="border-b-2 border-border p-3 text-left font-semibold text-foreground">
                    User Agent
                  </th>
                </tr>
              </thead>
              <tbody>
                {entries.map((entry) => (
                  <tr key={entry.id} className="hover:bg-muted">
                    <td className="border-b border-border p-3 whitespace-nowrap">
                      {new Date(entry.loginTime).toLocaleString()}
                    </td>
                    <td className="border-b border-border p-3 font-mono text-xs">
                      {entry.sourceIp || '-'}
                    </td>
                    <td className="border-b border-border p-3">{entry.loginType}</td>
                    <td className="border-b border-border p-3">
                      <StatusBadge status={entry.status} />
                    </td>
                    <td className="max-w-[300px] truncate border-b border-border p-3 text-xs text-muted-foreground">
                      {entry.userAgent || '-'}
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
