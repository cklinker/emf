/**
 * ProtectedRoute Component
 *
 * A route guard component that checks authentication and authorization
 * before rendering protected routes.
 *
 * Requirements:
 * - 2.1: Redirect unauthenticated users to OIDC provider login page
 * - 2.2: Display provider selection page for multiple providers
 */

import React from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { LoadingSpinner } from '../LoadingSpinner'

/**
 * Props for the ProtectedRoute component
 */
export interface ProtectedRouteProps {
  /** Child components to render when authenticated and authorized */
  children: React.ReactNode
  /** Optional policies required to access this route */
  requiredPolicies?: string[]
  /** Optional roles required to access this route */
  requiredRoles?: string[]
  /** Custom redirect path for unauthenticated users (defaults to /login) */
  loginPath?: string
  /** Custom redirect path for unauthorized users (defaults to /unauthorized) */
  unauthorizedPath?: string
  /** Custom loading component */
  loadingComponent?: React.ReactNode
  /** Callback when authorization check fails */
  onUnauthorized?: () => void
}

/**
 * Check if user has any of the required roles
 */
// eslint-disable-next-line react-refresh/only-export-components
export function hasRequiredRoles(
  userRoles: string[] | undefined,
  requiredRoles: string[]
): boolean {
  if (!requiredRoles || requiredRoles.length === 0) {
    return true
  }
  if (!userRoles || userRoles.length === 0) {
    return false
  }
  return requiredRoles.some((role) => userRoles.includes(role))
}

/**
 * Check if user has any of the required policies
 * Note: In a real implementation, this would check against the user's
 * permissions/policies from the auth context or a separate authorization service
 */
// eslint-disable-next-line react-refresh/only-export-components
export function hasRequiredPolicies(
  userPolicies: string[] | undefined,
  requiredPolicies: string[]
): boolean {
  if (!requiredPolicies || requiredPolicies.length === 0) {
    return true
  }
  if (!userPolicies || userPolicies.length === 0) {
    return false
  }
  return requiredPolicies.some((policy) => userPolicies.includes(policy))
}

/**
 * ProtectedRoute Component
 *
 * Wraps routes that require authentication and optionally authorization.
 * Shows loading state while checking auth, redirects to login if not authenticated,
 * and redirects to unauthorized page if user lacks required permissions.
 *
 * @example
 * ```tsx
 * // Basic protected route
 * <ProtectedRoute>
 *   <DashboardPage />
 * </ProtectedRoute>
 *
 * // Protected route with role requirements
 * <ProtectedRoute requiredRoles={['admin']}>
 *   <AdminPage />
 * </ProtectedRoute>
 *
 * // Protected route with policy requirements
 * <ProtectedRoute requiredPolicies={['collections:read']}>
 *   <CollectionsPage />
 * </ProtectedRoute>
 * ```
 */
export function ProtectedRoute({
  children,
  requiredPolicies = [],
  requiredRoles = [],
  loginPath = '/login',
  unauthorizedPath = '/unauthorized',
  loadingComponent,
  onUnauthorized,
}: ProtectedRouteProps): React.ReactElement {
  const { user, isAuthenticated, isLoading } = useAuth()
  const location = useLocation()

  // Show loading state while checking authentication
  if (isLoading) {
    return (
      <div
        role="status"
        aria-live="polite"
        aria-label="Checking authentication"
        data-testid="protected-route-loading"
        style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          minHeight: '200px',
        }}
      >
        {loadingComponent || <LoadingSpinner size="large" label="Checking authentication..." />}
      </div>
    )
  }

  // Redirect to login if not authenticated
  // Requirement 2.1: Redirect unauthenticated users to OIDC provider login page
  if (!isAuthenticated) {
    // Store the current location to redirect back after login
    return (
      <Navigate
        to={loginPath}
        state={{ from: location }}
        replace
        data-testid="protected-route-redirect-login"
      />
    )
  }

  // Check role-based authorization
  const hasRoles = hasRequiredRoles(user?.roles, requiredRoles)

  // Check policy-based authorization
  // Note: User policies would typically come from claims or a separate authorization check
  const userPolicies = (user?.claims?.policies as string[]) || []
  const hasPolicies = hasRequiredPolicies(userPolicies, requiredPolicies)

  // Redirect to unauthorized page if user lacks required permissions
  if (!hasRoles || !hasPolicies) {
    if (onUnauthorized) {
      onUnauthorized()
    }
    return (
      <Navigate
        to={unauthorizedPath}
        state={{
          from: location,
          requiredRoles: requiredRoles.length > 0 ? requiredRoles : undefined,
          requiredPolicies: requiredPolicies.length > 0 ? requiredPolicies : undefined,
        }}
        replace
        data-testid="protected-route-redirect-unauthorized"
      />
    )
  }

  // User is authenticated and authorized, render children
  return <>{children}</>
}

export default ProtectedRoute
