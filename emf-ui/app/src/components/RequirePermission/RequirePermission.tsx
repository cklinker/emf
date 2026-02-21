import React from 'react'
import { useSystemPermissions } from '../../hooks/useSystemPermissions'

interface RequirePermissionProps {
  permission: string
  children: React.ReactNode
  fallback?: React.ReactNode
}

/**
 * Component that conditionally renders children based on system permission.
 * Shows a loading spinner while permissions are being fetched.
 * Shows the fallback (or a default "Unauthorized" message) if permission is denied.
 */
export function RequirePermission({
  permission,
  children,
  fallback,
}: RequirePermissionProps): React.ReactElement | null {
  const { hasPermission, isLoading } = useSystemPermissions()

  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-8">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600" />
      </div>
    )
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
