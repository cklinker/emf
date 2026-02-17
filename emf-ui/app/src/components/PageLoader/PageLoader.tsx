/**
 * PageLoader Component
 *
 * Provides consistent full-page loading states and skeleton loading states
 * for content areas across the application.
 *
 * Requirements:
 * - 18.4: Display loading indicators during async operations
 * - 14.7: Support reduced motion preferences from the operating system
 *
 * Features:
 * - Full-page loading state with centered spinner
 * - Skeleton loading states for content areas
 * - Multiple skeleton variants (text, card, table, form)
 * - Respects prefers-reduced-motion preference
 * - Accessible with proper ARIA attributes
 */

import React from 'react'
import { LoadingSpinner } from '../LoadingSpinner'
import { cn } from '@/lib/utils'

/**
 * Skeleton variant types
 */
export type SkeletonVariant = 'text' | 'card' | 'table' | 'form' | 'header' | 'list'

/**
 * Props for the PageLoader component
 */
export interface PageLoaderProps {
  /** Loading message to display */
  message?: string
  /** Size of the spinner */
  size?: 'small' | 'medium' | 'large'
  /** Whether to show as full page overlay */
  fullPage?: boolean
  /** Optional custom class name */
  className?: string
  /** Optional test ID for testing purposes */
  'data-testid'?: string
}

/**
 * Props for the Skeleton component
 */
export interface SkeletonProps {
  /** Type of skeleton to display */
  variant?: SkeletonVariant
  /** Number of skeleton items to display (for list/table variants) */
  count?: number
  /** Width of the skeleton (CSS value) */
  width?: string
  /** Height of the skeleton (CSS value) */
  height?: string
  /** Optional custom class name */
  className?: string
  /** Optional test ID for testing purposes */
  'data-testid'?: string
}

/**
 * Props for the ContentLoader component
 */
export interface ContentLoaderProps {
  /** Whether content is loading */
  isLoading: boolean
  /** Content to display when not loading */
  children: React.ReactNode
  /** Skeleton variant to show while loading */
  skeleton?: SkeletonVariant
  /** Number of skeleton items */
  skeletonCount?: number
  /** Loading message for screen readers */
  loadingMessage?: string
  /** Optional custom class name */
  className?: string
  /** Optional test ID for testing purposes */
  'data-testid'?: string
}

/**
 * PageLoader Component
 *
 * Displays a full-page or inline loading state with a spinner.
 *
 * @example
 * ```tsx
 * // Full page loader
 * <PageLoader fullPage message="Loading dashboard..." />
 *
 * // Inline loader
 * <PageLoader message="Loading data..." />
 * ```
 */
export function PageLoader({
  message = 'Loading...',
  size = 'large',
  fullPage = false,
  className,
  'data-testid': testId = 'page-loader',
}: PageLoaderProps): React.ReactElement {
  return (
    <div
      className={cn(
        'flex items-center justify-center',
        fullPage
          ? 'fullPage fixed inset-0 z-[1000] bg-background/95 dark:bg-background/95'
          : 'inline min-h-[200px] w-full',
        className
      )}
      role="status"
      aria-live="polite"
      aria-busy="true"
      data-testid={testId}
    >
      <div className="flex flex-col items-center justify-center gap-4">
        <LoadingSpinner size={size} label={message} data-testid={`${testId}-spinner`} />
      </div>
    </div>
  )
}

/**
 * Skeleton Component
 *
 * Displays a skeleton placeholder for content that is loading.
 *
 * @example
 * ```tsx
 * // Text skeleton
 * <Skeleton variant="text" />
 *
 * // Card skeleton
 * <Skeleton variant="card" />
 *
 * // Table skeleton with multiple rows
 * <Skeleton variant="table" count={5} />
 * ```
 */
export function Skeleton({
  variant = 'text',
  count = 1,
  width,
  height,
  className,
  'data-testid': testId = 'skeleton',
}: SkeletonProps): React.ReactElement {
  const style: React.CSSProperties = {
    ...(width && { width }),
    ...(height && { height }),
  }

  const renderSkeletonItem = (index: number) => {
    const itemTestId = count > 1 ? `${testId}-item-${index}` : testId

    switch (variant) {
      case 'text':
        return (
          <div
            key={index}
            className="skeletonText h-4 w-full mb-2 rounded bg-muted animate-pulse last:w-[70%]"
            style={style}
            data-testid={itemTestId}
            aria-hidden="true"
          />
        )

      case 'card':
        return (
          <div
            key={index}
            className="skeletonCard p-4 rounded-lg mb-4 min-h-[150px] bg-muted animate-pulse"
            style={style}
            data-testid={itemTestId}
            aria-hidden="true"
          >
            <div className="h-5 w-[60%] mb-4 rounded bg-muted animate-pulse" />
            <div className="flex flex-col gap-2">
              <div
                className="h-3.5 mb-2 last:mb-0 rounded bg-muted animate-pulse"
                style={{ width: '80%' }}
              />
              <div
                className="h-3.5 mb-2 last:mb-0 rounded bg-muted animate-pulse"
                style={{ width: '60%' }}
              />
              <div
                className="h-3.5 mb-2 last:mb-0 rounded bg-muted animate-pulse"
                style={{ width: '70%' }}
              />
            </div>
          </div>
        )

      case 'table':
        return (
          <div
            key={index}
            className="skeletonTableRow flex gap-4 py-3 border-b border-border last:border-b-0 bg-muted animate-pulse"
            style={style}
            data-testid={itemTestId}
            aria-hidden="true"
          >
            <div className="h-4 rounded bg-muted animate-pulse" style={{ width: '20%' }} />
            <div className="h-4 rounded bg-muted animate-pulse" style={{ width: '30%' }} />
            <div className="h-4 rounded bg-muted animate-pulse" style={{ width: '25%' }} />
            <div className="h-4 rounded bg-muted animate-pulse" style={{ width: '15%' }} />
          </div>
        )

      case 'form':
        return (
          <div
            key={index}
            className="skeletonForm"
            style={style}
            data-testid={itemTestId}
            aria-hidden="true"
          >
            <div className="mb-6">
              <div className="h-3.5 w-[30%] mb-2 rounded bg-muted animate-pulse" />
              <div className="h-10 w-full rounded bg-muted animate-pulse" />
            </div>
          </div>
        )

      case 'header':
        return (
          <div
            key={index}
            className="skeletonHeader mb-6"
            style={style}
            data-testid={itemTestId}
            aria-hidden="true"
          >
            <div className="h-8 w-1/2 mb-3 rounded bg-muted animate-pulse" />
            <div className="h-4 w-[35%] rounded bg-muted animate-pulse" />
          </div>
        )

      case 'list':
        return (
          <div
            key={index}
            className="skeletonListItem flex items-center gap-4 py-3 border-b border-border last:border-b-0"
            style={style}
            data-testid={itemTestId}
            aria-hidden="true"
          >
            <div className="w-10 h-10 rounded-full shrink-0 bg-muted animate-pulse" />
            <div className="flex-1 flex flex-col gap-2">
              <div
                className="h-3.5 mb-2 last:mb-0 rounded bg-muted animate-pulse"
                style={{ width: '70%' }}
              />
              <div
                className="h-3.5 mb-2 last:mb-0 rounded bg-muted animate-pulse"
                style={{ width: '50%' }}
              />
            </div>
          </div>
        )

      default:
        return (
          <div
            key={index}
            className="skeletonText h-4 w-full mb-2 rounded bg-muted animate-pulse last:w-[70%]"
            style={style}
            data-testid={itemTestId}
            aria-hidden="true"
          />
        )
    }
  }

  return (
    <div
      className={cn('w-full', className)}
      role="status"
      aria-label="Loading content"
      data-testid={`${testId}-container`}
    >
      <span className="sr-only">Loading...</span>
      {Array.from({ length: count }, (_, index) => renderSkeletonItem(index))}
    </div>
  )
}

/**
 * ContentLoader Component
 *
 * Wrapper component that shows skeleton loading state while content is loading,
 * then displays the actual content when ready.
 *
 * @example
 * ```tsx
 * <ContentLoader isLoading={isLoading} skeleton="card" skeletonCount={3}>
 *   <CardList items={items} />
 * </ContentLoader>
 * ```
 */
export function ContentLoader({
  isLoading,
  children,
  skeleton = 'text',
  skeletonCount = 3,
  loadingMessage = 'Loading content...',
  className,
  'data-testid': testId = 'content-loader',
}: ContentLoaderProps): React.ReactElement {
  if (isLoading) {
    return (
      <div className={className} data-testid={testId} aria-busy="true">
        <span className="sr-only">{loadingMessage}</span>
        <Skeleton variant={skeleton} count={skeletonCount} data-testid={`${testId}-skeleton`} />
      </div>
    )
  }

  return (
    <div className={className} data-testid={testId} aria-busy="false">
      {children}
    </div>
  )
}

// Export default for convenience
export default PageLoader
