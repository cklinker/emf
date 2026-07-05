import React from 'react'
import { useSystemPermissions } from '../../hooks/useSystemPermissions'
import { useDelegatedAdmin } from '../../hooks/useDelegatedAdmin'

interface RequireUserManagementAccessProps {
  children: React.ReactNode
  fallback?: React.ReactNode
}

/**
 * Route gate for the Users page: passes for callers holding `MANAGE_USERS`
 * OR callers who are delegated admins (listed in an active delegated-admin
 * scope). Mirrors `RequirePermission` semantics — renders children while
 * loading or on error because the API is the real enforcement point.
 */
export function RequireUserManagementAccess({
  children,
  fallback,
}: RequireUserManagementAccessProps): React.ReactElement | null {
  const { hasPermission, isLoading: permissionsLoading, error } = useSystemPermissions()
  const { isDelegated, isLoading: delegatedLoading } = useDelegatedAdmin()

  if (permissionsLoading || delegatedLoading || error) {
    return <>{children}</>
  }

  if (!hasPermission('MANAGE_USERS') && !isDelegated) {
    if (fallback) return <>{fallback}</>
    return (
      <div className="flex flex-col items-center justify-center p-12 text-center">
        <h2 className="text-lg font-semibold text-foreground">Insufficient permissions</h2>
        <p className="mt-2 text-sm text-gray-500">
          You do not have the required permission to access this page.
        </p>
      </div>
    )
  }

  return <>{children}</>
}
