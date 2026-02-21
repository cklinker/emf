import React, { useState, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useApi } from '../../context/ApiContext'
import { useTenant } from '../../context/TenantContext'
import { cn } from '@/lib/utils'
import { Clock, User, Shield, ArrowUpDown, ShieldCheck, ShieldOff } from 'lucide-react'

interface PlatformUser {
  id: string
  email: string
  firstName: string
  lastName: string
  username?: string
  status: 'ACTIVE' | 'INACTIVE' | 'LOCKED' | 'PENDING_ACTIVATION'
  lastLoginAt?: string
  loginCount: number
  mfaEnabled: boolean
  createdAt: string
  updatedAt: string
}

interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

type SortField = 'lastLoginAt' | 'name' | 'loginCount'
type SortDirection = 'asc' | 'desc'

export interface LoginHistoryPageProps {
  testId?: string
}

function StatusBadge({ status }: { status: string }) {
  return (
    <span
      className={cn(
        'inline-block rounded-full px-2 py-0.5 text-xs font-medium',
        status === 'ACTIVE' &&
          'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300',
        status === 'INACTIVE' && 'bg-muted text-foreground',
        status === 'LOCKED' && 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300',
        status === 'PENDING_ACTIVATION' &&
          'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300'
      )}
    >
      {status}
    </span>
  )
}

function formatRelativeTime(dateString: string): string {
  const date = new Date(dateString)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  if (diffMins < 1) return 'Just now'
  if (diffMins < 60) return `${diffMins}m ago`
  if (diffHours < 24) return `${diffHours}h ago`
  if (diffDays < 7) return `${diffDays}d ago`
  return date.toLocaleDateString()
}

export function LoginHistoryPage({
  testId = 'login-history-page',
}: LoginHistoryPageProps): React.ReactElement {
  const { apiClient } = useApi()
  const { tenantSlug } = useTenant()

  const [sortField, setSortField] = useState<SortField>('lastLoginAt')
  const [sortDirection, setSortDirection] = useState<SortDirection>('desc')

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['login-history', tenantSlug],
    queryFn: async () => {
      return apiClient.get<PageResponse<PlatformUser>>('/control/users?size=100')
    },
  })

  const users = data?.content ?? []

  const sortedUsers = useMemo(() => {
    const sorted = [...users].sort((a, b) => {
      switch (sortField) {
        case 'lastLoginAt': {
          const aTime = a.lastLoginAt ? new Date(a.lastLoginAt).getTime() : 0
          const bTime = b.lastLoginAt ? new Date(b.lastLoginAt).getTime() : 0
          return sortDirection === 'desc' ? bTime - aTime : aTime - bTime
        }
        case 'name': {
          const aName = `${a.firstName} ${a.lastName}`.toLowerCase()
          const bName = `${b.firstName} ${b.lastName}`.toLowerCase()
          return sortDirection === 'desc' ? bName.localeCompare(aName) : aName.localeCompare(bName)
        }
        case 'loginCount':
          return sortDirection === 'desc'
            ? b.loginCount - a.loginCount
            : a.loginCount - b.loginCount
        default:
          return 0
      }
    })
    return sorted
  }, [users, sortField, sortDirection])

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortDirection((d) => (d === 'asc' ? 'desc' : 'asc'))
    } else {
      setSortField(field)
      setSortDirection('desc')
    }
  }

  const totalLogins = users.reduce((sum, u) => sum + u.loginCount, 0)
  const activeUsers = users.filter((u) => u.status === 'ACTIVE').length
  const mfaEnabledCount = users.filter((u) => u.mfaEnabled).length

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

      <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div className="rounded-lg border border-border bg-card p-4">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <User className="h-4 w-4" />
            <span>Active Users</span>
          </div>
          <p className="mt-1 text-2xl font-semibold">{isLoading ? '--' : activeUsers}</p>
        </div>
        <div className="rounded-lg border border-border bg-card p-4">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Clock className="h-4 w-4" />
            <span>Total Logins</span>
          </div>
          <p className="mt-1 text-2xl font-semibold">{isLoading ? '--' : totalLogins}</p>
        </div>
        <div className="rounded-lg border border-border bg-card p-4">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Shield className="h-4 w-4" />
            <span>MFA Enabled</span>
          </div>
          <p className="mt-1 text-2xl font-semibold">{isLoading ? '--' : mfaEnabledCount}</p>
        </div>
      </div>

      {isLoading ? (
        <div className="flex flex-col items-center justify-center p-12 text-muted-foreground">
          Loading login history...
        </div>
      ) : sortedUsers.length === 0 ? (
        <div className="flex flex-col items-center justify-center p-12 text-muted-foreground">
          <p>No users found.</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border bg-card">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr>
                <th
                  className="cursor-pointer border-b-2 border-border p-3 text-left font-semibold text-foreground hover:bg-muted"
                  onClick={() => handleSort('name')}
                >
                  <div className="flex items-center gap-1">
                    User
                    <ArrowUpDown className="h-3 w-3 text-muted-foreground" />
                  </div>
                </th>
                <th
                  className="cursor-pointer border-b-2 border-border p-3 text-left font-semibold text-foreground hover:bg-muted"
                  onClick={() => handleSort('lastLoginAt')}
                >
                  <div className="flex items-center gap-1">
                    Last Login
                    <ArrowUpDown className="h-3 w-3 text-muted-foreground" />
                  </div>
                </th>
                <th
                  className="cursor-pointer border-b-2 border-border p-3 text-left font-semibold text-foreground hover:bg-muted"
                  onClick={() => handleSort('loginCount')}
                >
                  <div className="flex items-center gap-1">
                    Login Count
                    <ArrowUpDown className="h-3 w-3 text-muted-foreground" />
                  </div>
                </th>
                <th className="border-b-2 border-border p-3 text-left font-semibold text-foreground">
                  Status
                </th>
                <th className="border-b-2 border-border p-3 text-left font-semibold text-foreground">
                  MFA
                </th>
              </tr>
            </thead>
            <tbody>
              {sortedUsers.map((user) => (
                <tr key={user.id} className="hover:bg-muted">
                  <td className="border-b border-border p-3">
                    <div className="flex flex-col">
                      <span className="font-medium text-foreground">
                        {user.firstName} {user.lastName}
                      </span>
                      <span className="text-xs text-muted-foreground">{user.email}</span>
                    </div>
                  </td>
                  <td className="border-b border-border p-3">
                    {user.lastLoginAt ? (
                      <div className="flex flex-col">
                        <span className="text-foreground">
                          {formatRelativeTime(user.lastLoginAt)}
                        </span>
                        <span className="text-xs text-muted-foreground">
                          {new Date(user.lastLoginAt).toLocaleString()}
                        </span>
                      </div>
                    ) : (
                      <span className="text-muted-foreground">Never</span>
                    )}
                  </td>
                  <td className="border-b border-border p-3">
                    <span className="font-mono text-foreground">{user.loginCount}</span>
                  </td>
                  <td className="border-b border-border p-3">
                    <StatusBadge status={user.status} />
                  </td>
                  <td className="border-b border-border p-3">
                    {user.mfaEnabled ? (
                      <ShieldCheck className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
                    ) : (
                      <ShieldOff className="h-4 w-4 text-muted-foreground" />
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
