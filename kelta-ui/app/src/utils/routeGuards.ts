/**
 * Route Guard Utilities
 *
 * Utility functions for checking authentication and authorization
 * in route guards.
 *
 * Requirements:
 * - 2.1: Redirect unauthenticated users to OIDC provider login page
 * - 2.2: Display provider selection page for multiple providers
 */

import type { User } from '../types/auth'
import type { PageConfig } from '../types/config'

/**
 * Authorization check result
 */
export interface AuthorizationResult {
  /** Whether the user is authorized */
  authorized: boolean
  /** Reason for denial if not authorized */
  reason?: 'unauthenticated' | 'missing_role' | 'missing_policy'
  /** Missing roles if authorization failed due to roles */
  missingRoles?: string[]
  /** Missing policies if authorization failed due to policies */
  missingPolicies?: string[]
}

/**
 * Check if a user is authorized to access a page based on its policies
 *
 * @param user - The current user (null if not authenticated)
 * @param pageConfig - The page configuration with policies
 * @returns Authorization result
 */
export function checkPageAuthorization(
  user: User | null,
  pageConfig: PageConfig
): AuthorizationResult {
  // Check authentication first
  if (!user) {
    return {
      authorized: false,
      reason: 'unauthenticated',
    }
  }

  // If no policies are defined, allow access
  if (!pageConfig.policies || pageConfig.policies.length === 0) {
    return { authorized: true }
  }

  // Get user's policies from claims
  const userPolicies = (user.claims?.policies as string[]) || []

  // Check if user has any of the required policies
  const hasPolicy = pageConfig.policies.some((policy) => userPolicies.includes(policy))

  if (!hasPolicy) {
    return {
      authorized: false,
      reason: 'missing_policy',
      missingPolicies: pageConfig.policies.filter((policy) => !userPolicies.includes(policy)),
    }
  }

  return { authorized: true }
}

/**
 * Check if a user has specific roles
 *
 * @param user - The current user
 * @param requiredRoles - Array of required roles (user needs at least one)
 * @returns Authorization result
 */
export function checkRoleAuthorization(
  user: User | null,
  requiredRoles: string[]
): AuthorizationResult {
  // Check authentication first
  if (!user) {
    return {
      authorized: false,
      reason: 'unauthenticated',
    }
  }

  // If no roles are required, allow access
  if (!requiredRoles || requiredRoles.length === 0) {
    return { authorized: true }
  }

  // Get user's roles
  const userRoles = user.roles || []

  // Check if user has any of the required roles
  const hasRole = requiredRoles.some((role) => userRoles.includes(role))

  if (!hasRole) {
    return {
      authorized: false,
      reason: 'missing_role',
      missingRoles: requiredRoles.filter((role) => !userRoles.includes(role)),
    }
  }

  return { authorized: true }
}

/**
 * Check if a user has specific policies
 *
 * @param user - The current user
 * @param requiredPolicies - Array of required policies (user needs at least one)
 * @returns Authorization result
 */
export function checkPolicyAuthorization(
  user: User | null,
  requiredPolicies: string[]
): AuthorizationResult {
  // Check authentication first
  if (!user) {
    return {
      authorized: false,
      reason: 'unauthenticated',
    }
  }

  // If no policies are required, allow access
  if (!requiredPolicies || requiredPolicies.length === 0) {
    return { authorized: true }
  }

  // Get user's policies from claims
  const userPolicies = (user.claims?.policies as string[]) || []

  // Check if user has any of the required policies
  const hasPolicy = requiredPolicies.some((policy) => userPolicies.includes(policy))

  if (!hasPolicy) {
    return {
      authorized: false,
      reason: 'missing_policy',
      missingPolicies: requiredPolicies.filter((policy) => !userPolicies.includes(policy)),
    }
  }

  return { authorized: true }
}

/**
 * Combined authorization check for both roles and policies
 *
 * @param user - The current user
 * @param requiredRoles - Array of required roles (user needs at least one)
 * @param requiredPolicies - Array of required policies (user needs at least one)
 * @returns Authorization result
 */
export function checkAuthorization(
  user: User | null,
  requiredRoles: string[] = [],
  requiredPolicies: string[] = []
): AuthorizationResult {
  // Check authentication first
  if (!user) {
    return {
      authorized: false,
      reason: 'unauthenticated',
    }
  }

  // Check roles
  const roleResult = checkRoleAuthorization(user, requiredRoles)
  if (!roleResult.authorized && roleResult.reason === 'missing_role') {
    return roleResult
  }

  // Check policies
  const policyResult = checkPolicyAuthorization(user, requiredPolicies)
  if (!policyResult.authorized && policyResult.reason === 'missing_policy') {
    return policyResult
  }

  return { authorized: true }
}

/**
 * Get the redirect path for an unauthorized user
 *
 * @param result - The authorization result
 * @param loginPath - Path to redirect unauthenticated users
 * @param unauthorizedPath - Path to redirect unauthorized users
 * @returns The redirect path
 */
export function getRedirectPath(
  result: AuthorizationResult,
  loginPath: string = '/login',
  unauthorizedPath: string = '/unauthorized'
): string {
  if (result.authorized) {
    return ''
  }

  if (result.reason === 'unauthenticated') {
    return loginPath
  }

  return unauthorizedPath
}

/**
 * Filter pages based on user authorization
 *
 * @param pages - Array of page configurations
 * @param user - The current user
 * @returns Array of pages the user is authorized to access
 */
export function filterAuthorizedPages(pages: PageConfig[], user: User | null): PageConfig[] {
  if (!user) {
    return []
  }

  return pages.filter((page) => {
    const result = checkPageAuthorization(user, page)
    return result.authorized
  })
}

/**
 * Check if a user can access a specific route path
 *
 * @param path - The route path to check
 * @param pages - Array of page configurations
 * @param user - The current user
 * @returns Authorization result
 */
export function canAccessRoute(
  path: string,
  pages: PageConfig[],
  user: User | null
): AuthorizationResult {
  // Find the page config for this path
  const pageConfig = pages.find((page) => page.path === path)

  // If no page config found, assume public access
  if (!pageConfig) {
    return { authorized: true }
  }

  return checkPageAuthorization(user, pageConfig)
}
