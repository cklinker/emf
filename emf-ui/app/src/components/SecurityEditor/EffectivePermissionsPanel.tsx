/**
 * EffectivePermissionsPanel Component
 *
 * Shows resolved/effective permissions for a user by fetching from
 * the /control/my-permissions/effective endpoint.
 *
 * Features:
 * - Section 1: System permissions with green check / red X indicators
 * - Section 2: Object permissions summary table
 * - Expandable/collapsible sections
 * - Loading and error states
 */

import React, { useState, useCallback } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Check, X, ChevronDown, ChevronRight, Shield, Database, AlertCircle } from 'lucide-react'
import { cn } from '@/lib/utils'
import { useApi } from '../../context/ApiContext'
import { Button } from '@/components/ui/button'

/** Effective system permission from the API */
interface EffectiveSystemPermission {
  name: string
  granted: boolean
}

/** Effective object permission from the API */
interface EffectiveObjectPermission {
  collectionId: string
  collectionName: string
  canCreate: boolean
  canRead: boolean
  canEdit: boolean
  canDelete: boolean
  canViewAll: boolean
  canModifyAll: boolean
}

/** API response shape */
interface EffectivePermissionsResponse {
  userId: string
  systemPermissions: EffectiveSystemPermission[]
  objectPermissions: EffectiveObjectPermission[]
}

export interface EffectivePermissionsPanelProps {
  /** The user ID to fetch effective permissions for */
  userId: string
  /** Test ID for the component */
  testId?: string
}

export function EffectivePermissionsPanel({
  userId,
  testId = 'effective-permissions-panel',
}: EffectivePermissionsPanelProps): React.ReactElement {
  const { apiClient } = useApi()

  const [systemExpanded, setSystemExpanded] = useState(true)
  const [objectExpanded, setObjectExpanded] = useState(true)

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['effective-permissions', userId],
    queryFn: () =>
      apiClient.get<EffectivePermissionsResponse>(
        `/control/my-permissions/effective?userId=${encodeURIComponent(userId)}`
      ),
    enabled: !!userId,
  })

  const toggleSystem = useCallback(() => setSystemExpanded((prev) => !prev), [])
  const toggleObject = useCallback(() => setObjectExpanded((prev) => !prev), [])

  if (isLoading) {
    return (
      <div className="rounded-lg border border-border bg-card p-6" data-testid={testId}>
        <div className="flex items-center justify-center py-8 text-muted-foreground">
          <div className="flex items-center gap-2">
            <div className="h-4 w-4 animate-spin rounded-full border-2 border-muted-foreground border-t-transparent" />
            <span className="text-sm">Loading effective permissions...</span>
          </div>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="rounded-lg border border-border bg-card p-6" data-testid={testId}>
        <div className="flex flex-col items-center justify-center gap-3 py-8 text-muted-foreground">
          <AlertCircle size={24} className="text-destructive" />
          <p className="text-sm">Failed to load effective permissions.</p>
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            Retry
          </Button>
        </div>
      </div>
    )
  }

  const systemPermissions = data?.systemPermissions ?? []
  const objectPermissions = data?.objectPermissions ?? []
  const grantedCount = systemPermissions.filter((p) => p.granted).length

  return (
    <div className="space-y-4" data-testid={testId}>
      {/* Section 1: System Permissions */}
      <div className="rounded-lg border border-border bg-card">
        <button
          type="button"
          onClick={toggleSystem}
          className="flex w-full items-center gap-2 px-4 py-3 text-left hover:bg-muted/50 transition-colors"
          aria-expanded={systemExpanded}
          aria-controls={`${testId}-system-content`}
          data-testid={`${testId}-system-toggle`}
        >
          {systemExpanded ? (
            <ChevronDown size={16} className="text-muted-foreground" />
          ) : (
            <ChevronRight size={16} className="text-muted-foreground" />
          )}
          <Shield size={16} className="text-muted-foreground" />
          <span className="text-sm font-semibold text-foreground">System Permissions</span>
          <span className="ml-auto text-xs text-muted-foreground">
            {grantedCount} of {systemPermissions.length} granted
          </span>
        </button>

        {systemExpanded && (
          <div
            id={`${testId}-system-content`}
            className="border-t border-border"
            data-testid={`${testId}-system-content`}
          >
            {systemPermissions.length === 0 ? (
              <div className="px-4 py-6 text-center text-sm text-muted-foreground">
                No system permissions data available.
              </div>
            ) : (
              <div className="divide-y divide-border">
                {systemPermissions.map((perm) => (
                  <div
                    key={perm.name}
                    className="flex items-center gap-3 px-4 py-2.5"
                    data-testid={`${testId}-system-perm-${perm.name}`}
                  >
                    {perm.granted ? (
                      <div className="flex h-5 w-5 items-center justify-center rounded-full bg-emerald-100 dark:bg-emerald-950">
                        <Check size={12} className="text-emerald-600 dark:text-emerald-400" />
                      </div>
                    ) : (
                      <div className="flex h-5 w-5 items-center justify-center rounded-full bg-red-100 dark:bg-red-950">
                        <X size={12} className="text-red-600 dark:text-red-400" />
                      </div>
                    )}
                    <span
                      className={cn(
                        'text-sm',
                        perm.granted ? 'text-foreground' : 'text-muted-foreground'
                      )}
                    >
                      {formatPermissionName(perm.name)}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Section 2: Object Permissions */}
      <div className="rounded-lg border border-border bg-card">
        <button
          type="button"
          onClick={toggleObject}
          className="flex w-full items-center gap-2 px-4 py-3 text-left hover:bg-muted/50 transition-colors"
          aria-expanded={objectExpanded}
          aria-controls={`${testId}-object-content`}
          data-testid={`${testId}-object-toggle`}
        >
          {objectExpanded ? (
            <ChevronDown size={16} className="text-muted-foreground" />
          ) : (
            <ChevronRight size={16} className="text-muted-foreground" />
          )}
          <Database size={16} className="text-muted-foreground" />
          <span className="text-sm font-semibold text-foreground">Object Permissions</span>
          <span className="ml-auto text-xs text-muted-foreground">
            {objectPermissions.length} collection{objectPermissions.length !== 1 ? 's' : ''}
          </span>
        </button>

        {objectExpanded && (
          <div
            id={`${testId}-object-content`}
            className="border-t border-border"
            data-testid={`${testId}-object-content`}
          >
            {objectPermissions.length === 0 ? (
              <div className="px-4 py-6 text-center text-sm text-muted-foreground">
                No object permissions data available.
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table
                  className="w-full border-collapse text-sm"
                  role="grid"
                  aria-label="Effective object permissions"
                  data-testid={`${testId}-object-table`}
                >
                  <thead>
                    <tr role="row" className="bg-muted/50">
                      <th
                        role="columnheader"
                        scope="col"
                        className="px-4 py-2 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                      >
                        Collection
                      </th>
                      {['Create', 'Read', 'Edit', 'Delete', 'View All', 'Modify All'].map(
                        (header) => (
                          <th
                            key={header}
                            role="columnheader"
                            scope="col"
                            className="px-3 py-2 text-center text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                          >
                            {header}
                          </th>
                        )
                      )}
                    </tr>
                  </thead>
                  <tbody>
                    {objectPermissions.map((perm) => (
                      <tr
                        key={perm.collectionId}
                        role="row"
                        className="border-t border-border"
                        data-testid={`${testId}-object-row-${perm.collectionId}`}
                      >
                        <td role="gridcell" className="px-4 py-2 font-medium text-foreground">
                          {perm.collectionName}
                        </td>
                        {(
                          [
                            perm.canCreate,
                            perm.canRead,
                            perm.canEdit,
                            perm.canDelete,
                            perm.canViewAll,
                            perm.canModifyAll,
                          ] as boolean[]
                        ).map((granted, colIndex) => (
                          <td key={colIndex} role="gridcell" className="px-3 py-2 text-center">
                            {granted ? (
                              <Check
                                size={14}
                                className="mx-auto text-emerald-600 dark:text-emerald-400"
                              />
                            ) : (
                              <X size={14} className="mx-auto text-red-400 dark:text-red-600" />
                            )}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

/** Format a SCREAMING_SNAKE_CASE permission name into Title Case */
function formatPermissionName(name: string): string {
  return name
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ')
}

export default EffectivePermissionsPanel
