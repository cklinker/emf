/**
 * AppShell Constants
 *
 * Shared constants for the AppShell component.
 */

/**
 * Breakpoint definitions for responsive layout
 * - Desktop: 1024px and above
 * - Tablet: 768px to 1023px
 * - Mobile: below 768px
 */
export const BREAKPOINTS = {
  mobile: 768,
  tablet: 1024,
} as const

/**
 * Screen size categories
 */
export type ScreenSize = 'mobile' | 'tablet' | 'desktop'
