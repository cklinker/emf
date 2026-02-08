/**
 * Authorization Types
 *
 * Types related to roles, policies, and access control.
 */

/**
 * Role definition
 */
export interface Role {
  id: string
  name: string
  description?: string
  createdAt: string
}

/**
 * Policy definition
 */
export interface Policy {
  id: string
  name: string
  description?: string
  expression?: string
  rules?: string
  createdAt: string
}
