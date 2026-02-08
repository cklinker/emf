/**
 * Common Types
 *
 * Shared types used across multiple domains.
 */

/**
 * User information from authentication
 */
export interface User {
  id: string
  email: string
  name?: string
  roles: string[]
}

/**
 * Paginated response wrapper
 */
export interface PaginatedResponse<T> {
  data: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

/**
 * API error response
 */
export interface ApiError {
  code: string
  message: string
  details?: Record<string, unknown>
}

/**
 * Sort direction
 */
export type SortDirection = 'asc' | 'desc'

/**
 * Sort configuration
 */
export interface SortConfig {
  field: string
  direction: SortDirection
}

/**
 * Filter configuration
 */
export interface FilterConfig {
  field: string
  operator: 'eq' | 'ne' | 'gt' | 'gte' | 'lt' | 'lte' | 'contains' | 'in'
  value: unknown
}
