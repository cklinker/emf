import React from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { Shield, Plus, Lock } from 'lucide-react'
import { useApi } from '../../context/ApiContext'
import { useTenant } from '../../context/TenantContext'
import { useI18n } from '../../context/I18nContext'
import { LoadingSpinner, ErrorMessage } from '../../components'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'

interface PermissionSet {
  id: string
  name: string
  description: string | null
  system: boolean
  createdAt: string
  updatedAt: string
}

export interface PermissionSetsPageProps {
  testId?: string
}

export function PermissionSetsPage({
  testId = 'permission-sets-page',
}: PermissionSetsPageProps): React.ReactElement {
  const { formatDate } = useI18n()
  const { apiClient } = useApi()
  const { tenantSlug } = useTenant()
  const navigate = useNavigate()

  const {
    data: permissionSets,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['permission-sets'],
    queryFn: () => apiClient.get<PermissionSet[]>('/control/permission-sets'),
  })

  const permissionSetList = permissionSets ?? []

  if (isLoading) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <div className="flex min-h-[400px] items-center justify-center">
          <LoadingSpinner size="large" label="Loading..." />
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <ErrorMessage
          error={error instanceof Error ? error : new Error('Failed to load permission sets')}
          onRetry={() => refetch()}
        />
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
      <header className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Shield className="h-6 w-6 text-muted-foreground" />
          <h1 className="text-2xl font-semibold text-foreground">Permission Sets</h1>
        </div>
        <Button
          type="button"
          onClick={() => navigate(`/${tenantSlug}/setup/permission-sets/new`)}
          data-testid="new-permission-set-button"
        >
          <Plus className="mr-2 h-4 w-4" />
          New Permission Set
        </Button>
      </header>

      {permissionSetList.length === 0 ? (
        <div
          className="rounded-lg border border-border bg-card py-16 text-center text-muted-foreground"
          data-testid="empty-state"
        >
          <Shield className="mx-auto mb-4 h-12 w-12 text-muted-foreground/50" />
          <p className="text-lg font-medium">No permission sets found</p>
          <p className="mt-1 text-sm">Create a permission set to manage access controls.</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border bg-card">
          <table
            className="w-full border-collapse text-sm"
            role="grid"
            aria-label="Permission Sets"
            data-testid="permission-sets-table"
          >
            <thead>
              <tr role="row" className="bg-muted">
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Name
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Description
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  System
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Created
                </th>
              </tr>
            </thead>
            <tbody>
              {permissionSetList.map((ps, index) => (
                <tr
                  key={ps.id}
                  role="row"
                  className="cursor-pointer border-b border-border transition-colors last:border-b-0 hover:bg-muted/50"
                  onClick={() => navigate(`/${tenantSlug}/setup/permission-sets/${ps.id}`)}
                  data-testid={`permission-set-row-${index}`}
                >
                  <td role="gridcell" className="px-4 py-3 font-medium text-foreground">
                    <div className="flex items-center gap-2">
                      {ps.system ? (
                        <Lock className="h-4 w-4 text-muted-foreground" />
                      ) : (
                        <Shield className="h-4 w-4 text-muted-foreground" />
                      )}
                      {ps.name}
                    </div>
                  </td>
                  <td
                    role="gridcell"
                    className="max-w-[400px] truncate px-4 py-3 text-muted-foreground"
                  >
                    {ps.description || '\u2014'}
                  </td>
                  <td role="gridcell" className="px-4 py-3">
                    {ps.system ? (
                      <span
                        className={cn(
                          'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium',
                          'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300'
                        )}
                      >
                        System
                      </span>
                    ) : (
                      <span className="text-sm text-muted-foreground">\u2014</span>
                    )}
                  </td>
                  <td role="gridcell" className="whitespace-nowrap px-4 py-3 text-muted-foreground">
                    {formatDate(new Date(ps.createdAt), {
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric',
                    })}
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

export default PermissionSetsPage
