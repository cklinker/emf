/**
 * LoadingSpinner Component
 *
 * Displays an animated loading spinner with optional label text.
 * Supports multiple size variants and accessibility features.
 *
 * Requirements:
 * - 18.4: Display loading indicators during async operations
 *
 * Features:
 * - Animated spinner (Tailwind animate-spin)
 * - Size variants: small (16px), medium (24px), large (48px)
 * - Optional visible label text
 * - Accessible with role="status" and aria-live="polite"
 * - Screen reader text for loading state
 * - Reduced motion support (motion-reduce:animate-none)
 */

import React from 'react'
import { cn } from '@/lib/utils'

/**
 * Size variants for the spinner
 */
export type SpinnerSize = 'small' | 'medium' | 'large'

/**
 * Props for the LoadingSpinner component
 */
export interface LoadingSpinnerProps {
  /** Size of the spinner: 'small' (16px), 'medium' (24px), or 'large' (48px) */
  size?: SpinnerSize
  /** Optional visible label text displayed below the spinner */
  label?: string
  /** Optional custom class name for additional styling */
  className?: string
  /** Optional test ID for testing purposes */
  'data-testid'?: string
}

/**
 * Default loading text for screen readers when no label is provided
 */
const DEFAULT_SR_TEXT = 'Loading...'

/**
 * Tailwind classes for each spinner size
 */
const sizeClasses: Record<SpinnerSize, string> = {
  small: 'w-4 h-4',
  medium: 'w-6 h-6',
  large: 'w-12 h-12',
}

/**
 * LoadingSpinner Component
 *
 * Displays an animated loading indicator with accessibility support.
 * The spinner uses Tailwind's animate-spin that respects reduced motion preferences.
 *
 * @example
 * ```tsx
 * // Basic usage
 * <LoadingSpinner />
 *
 * // With size variant
 * <LoadingSpinner size="large" />
 *
 * // With visible label
 * <LoadingSpinner size="medium" label="Loading data..." />
 * ```
 */
export function LoadingSpinner({
  size = 'medium',
  label,
  className,
  'data-testid': testId = 'loading-spinner',
}: LoadingSpinnerProps): React.ReactElement {
  return (
    <div
      className={cn('inline-flex flex-col items-center justify-center gap-2', className)}
      role="status"
      aria-live="polite"
      aria-busy="true"
      data-testid={testId}
    >
      {/* Spinner element */}
      <div
        className={cn('flex shrink-0 items-center justify-center', sizeClasses[size])}
        aria-hidden="true"
        data-testid={`${testId}-icon`}
        data-size={size}
      >
        <svg
          className="h-full w-full animate-spin motion-reduce:animate-none"
          viewBox="0 0 24 24"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
        >
          {/* Background circle (track) */}
          <circle className="fill-none stroke-muted" cx="12" cy="12" r="10" strokeWidth="3" />
          {/* Animated arc */}
          <circle
            className="fill-none stroke-primary"
            cx="12"
            cy="12"
            r="10"
            strokeWidth="3"
            strokeLinecap="round"
            strokeDasharray="45 200"
            strokeDashoffset="0"
          />
        </svg>
      </div>

      {/* Visible label (if provided) */}
      {label && (
        <span
          className="text-center text-sm leading-relaxed text-muted-foreground"
          data-testid={`${testId}-label`}
        >
          {label}
        </span>
      )}

      {/* Screen reader text (always present for accessibility) */}
      <span className="sr-only" data-testid={`${testId}-sr-text`}>
        {label || DEFAULT_SR_TEXT}
      </span>
    </div>
  )
}

// Export default for convenience
export default LoadingSpinner
