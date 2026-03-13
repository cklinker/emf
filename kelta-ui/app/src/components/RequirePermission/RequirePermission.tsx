import React from 'react'
import { useSystemPermissions } from '../../hooks/useSystemPermissions'

interface RequirePermissionProps {
  permission: string
  children: React.ReactNode
  fallback?: React.ReactNode
}

/**
 * Component that conditionally renders children based on system permission.
 * Renders children while permissions are loading or on error to avoid blocking
 * page access — the gateway enforces permissions at the API level.
 * Only hides content when permissions are loaded and the required permission
 * is explicitly not granted.
 */
export function RequirePermission({
  permission,
  children,
  fallback,
}: RequirePermissionProps): React.ReactElement | null {
  const { hasPermission, isLoading, error } = useSystemPermissions()

  // Render children while loading or on error — the gateway enforces
  // permissions at the API level, so blocking the UI is unnecessary.
  if (isLoading || error) {
    return <>{children}</>
  }

  if (!hasPermission(permission)) {
    if (fallback) return <>{fallback}</>
    return (
      <div className="flex flex-col items-center justify-center p-12 text-center">
        <h2 className="text-lg font-semibold text-gray-900">Insufficient Permissions</h2>
        <p className="mt-2 text-sm text-gray-500">
          You do not have the required permission to access this page.
        </p>
      </div>
    )
  }

  return <>{children}</>
}
