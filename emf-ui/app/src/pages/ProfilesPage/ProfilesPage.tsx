import React, { useState, useCallback } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useApi } from '../../context/ApiContext'
import { useI18n } from '../../context/I18nContext'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Lock, ChevronDown, ChevronRight, Plus, Shield } from 'lucide-react'

interface ObjectPermission {
  objectName: string
  canRead: boolean
  canCreate: boolean
  canEdit: boolean
  canDelete: boolean
}

interface SecurityProfile {
  id: string
  name: string
  description: string | null
  system: boolean
  systemPermissions: string[]
  objectPermissions: ObjectPermission[]
  createdAt: string
  updatedAt: string
}

export interface ProfilesPageProps {
  testId?: string
}

function SystemBadge() {
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800 dark:bg-amber-950 dark:text-amber-300">
      <Lock size={12} />
      System
    </span>
  )
}

function ProfileDetail({ profile }: { profile: SecurityProfile }) {
  return (
    <div className="border-t border-border bg-muted/30 px-6 py-4">
      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        {/* System Permissions */}
        <div>
          <h4 className="mb-2 text-sm font-semibold text-foreground">System Permissions</h4>
          {profile.systemPermissions.length === 0 ? (
            <p className="text-sm text-muted-foreground">No system permissions assigned</p>
          ) : (
            <ul className="space-y-1">
              {profile.systemPermissions.map((perm) => (
                <li key={perm} className="flex items-center gap-2 text-sm text-foreground">
                  <Shield size={14} className="text-primary" />
                  {perm}
                </li>
              ))}
            </ul>
          )}
        </div>

        {/* Object Permissions */}
        <div>
          <h4 className="mb-2 text-sm font-semibold text-foreground">Object Permissions</h4>
          {profile.objectPermissions.length === 0 ? (
            <p className="text-sm text-muted-foreground">No object permissions assigned</p>
          ) : (
            <div className="overflow-x-auto rounded border border-border">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-muted">
                    <th className="px-3 py-1.5 text-left text-xs font-medium text-muted-foreground">
                      Object
                    </th>
                    <th className="px-3 py-1.5 text-center text-xs font-medium text-muted-foreground">
                      Read
                    </th>
                    <th className="px-3 py-1.5 text-center text-xs font-medium text-muted-foreground">
                      Create
                    </th>
                    <th className="px-3 py-1.5 text-center text-xs font-medium text-muted-foreground">
                      Edit
                    </th>
                    <th className="px-3 py-1.5 text-center text-xs font-medium text-muted-foreground">
                      Delete
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {profile.objectPermissions.map((op) => (
                    <tr key={op.objectName} className="border-t border-border">
                      <td className="px-3 py-1.5 font-medium text-foreground">{op.objectName}</td>
                      <td className="px-3 py-1.5 text-center">
                        {op.canRead ? (
                          <span className="text-emerald-600">Yes</span>
                        ) : (
                          <span className="text-muted-foreground">No</span>
                        )}
                      </td>
                      <td className="px-3 py-1.5 text-center">
                        {op.canCreate ? (
                          <span className="text-emerald-600">Yes</span>
                        ) : (
                          <span className="text-muted-foreground">No</span>
                        )}
                      </td>
                      <td className="px-3 py-1.5 text-center">
                        {op.canEdit ? (
                          <span className="text-emerald-600">Yes</span>
                        ) : (
                          <span className="text-muted-foreground">No</span>
                        )}
                      </td>
                      <td className="px-3 py-1.5 text-center">
                        {op.canDelete ? (
                          <span className="text-emerald-600">Yes</span>
                        ) : (
                          <span className="text-muted-foreground">No</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export function ProfilesPage({ testId = 'profiles-page' }: ProfilesPageProps): React.ReactElement {
  const { apiClient } = useApi()
  const { formatDate } = useI18n()

  const [expandedId, setExpandedId] = useState<string | null>(null)

  const {
    data: profiles,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['profiles'],
    queryFn: () => apiClient.get<SecurityProfile[]>('/control/profiles'),
  })

  const profileList = profiles ?? []

  const handleToggleExpand = useCallback((id: string) => {
    setExpandedId((prev) => (prev === id ? null : id))
  }, [])

  if (isLoading) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <div className="flex min-h-[400px] items-center justify-center text-muted-foreground">
          Loading...
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <div className="flex flex-col items-center justify-center gap-4 p-12 text-muted-foreground">
          <p>Failed to load profiles.</p>
          <Button variant="outline" onClick={() => refetch()}>
            Retry
          </Button>
        </div>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-foreground">Profiles</h1>
        <Button data-testid="new-profile-button">
          <Plus size={16} />
          New Profile
        </Button>
      </header>

      {profileList.length === 0 ? (
        <div
          className="rounded-lg border border-border bg-card py-16 text-center text-muted-foreground"
          data-testid="empty-state"
        >
          <p>No profiles found. Create your first profile to get started.</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border bg-card">
          <table
            className="w-full border-collapse"
            role="grid"
            aria-label="Profiles"
            data-testid="profiles-table"
          >
            <thead>
              <tr role="row" className="bg-muted">
                <th
                  role="columnheader"
                  scope="col"
                  className="w-8 border-b border-border px-4 py-3"
                >
                  <span className="sr-only">Expand</span>
                </th>
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
              {profileList.map((profile, index) => {
                const isExpanded = expandedId === profile.id
                return (
                  <React.Fragment key={profile.id}>
                    <tr
                      role="row"
                      className={cn(
                        'cursor-pointer border-b border-border transition-colors hover:bg-muted/50',
                        isExpanded && 'bg-muted/30'
                      )}
                      onClick={() => handleToggleExpand(profile.id)}
                      data-testid={`profile-row-${index}`}
                    >
                      <td role="gridcell" className="px-4 py-3 text-muted-foreground">
                        {isExpanded ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                      </td>
                      <td role="gridcell" className="px-4 py-3 text-sm font-medium text-foreground">
                        {profile.name}
                      </td>
                      <td
                        role="gridcell"
                        className="max-w-[300px] truncate px-4 py-3 text-sm text-muted-foreground"
                      >
                        {profile.description || '\u2014'}
                      </td>
                      <td role="gridcell" className="px-4 py-3 text-sm">
                        {profile.system && <SystemBadge />}
                      </td>
                      <td role="gridcell" className="px-4 py-3 text-sm text-muted-foreground">
                        {formatDate(new Date(profile.createdAt), {
                          year: 'numeric',
                          month: 'short',
                          day: 'numeric',
                        })}
                      </td>
                    </tr>
                    {isExpanded && (
                      <tr>
                        <td colSpan={5} className="p-0">
                          <ProfileDetail profile={profile} />
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export default ProfilesPage
