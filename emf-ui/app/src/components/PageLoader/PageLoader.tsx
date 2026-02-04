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

import React from 'react';
import { LoadingSpinner } from '../LoadingSpinner';
import styles from './PageLoader.module.css';

/**
 * Skeleton variant types
 */
export type SkeletonVariant = 'text' | 'card' | 'table' | 'form' | 'header' | 'list';

/**
 * Props for the PageLoader component
 */
export interface PageLoaderProps {
  /** Loading message to display */
  message?: string;
  /** Size of the spinner */
  size?: 'small' | 'medium' | 'large';
  /** Whether to show as full page overlay */
  fullPage?: boolean;
  /** Optional custom class name */
  className?: string;
  /** Optional test ID for testing purposes */
  'data-testid'?: string;
}

/**
 * Props for the Skeleton component
 */
export interface SkeletonProps {
  /** Type of skeleton to display */
  variant?: SkeletonVariant;
  /** Number of skeleton items to display (for list/table variants) */
  count?: number;
  /** Width of the skeleton (CSS value) */
  width?: string;
  /** Height of the skeleton (CSS value) */
  height?: string;
  /** Optional custom class name */
  className?: string;
  /** Optional test ID for testing purposes */
  'data-testid'?: string;
}

/**
 * Props for the ContentLoader component
 */
export interface ContentLoaderProps {
  /** Whether content is loading */
  isLoading: boolean;
  /** Content to display when not loading */
  children: React.ReactNode;
  /** Skeleton variant to show while loading */
  skeleton?: SkeletonVariant;
  /** Number of skeleton items */
  skeletonCount?: number;
  /** Loading message for screen readers */
  loadingMessage?: string;
  /** Optional custom class name */
  className?: string;
  /** Optional test ID for testing purposes */
  'data-testid'?: string;
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
  const containerClasses = [
    styles.pageLoader,
    fullPage ? styles.fullPage : styles.inline,
    className,
  ].filter(Boolean).join(' ');

  return (
    <div
      className={containerClasses}
      role="status"
      aria-live="polite"
      aria-busy="true"
      data-testid={testId}
    >
      <div className={styles.loaderContent}>
        <LoadingSpinner
          size={size}
          label={message}
          data-testid={`${testId}-spinner`}
        />
      </div>
    </div>
  );
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
  };

  const renderSkeletonItem = (index: number) => {
    const itemTestId = count > 1 ? `${testId}-item-${index}` : testId;

    switch (variant) {
      case 'text':
        return (
          <div
            key={index}
            className={`${styles.skeleton} ${styles.skeletonText}`}
            style={style}
            data-testid={itemTestId}
            aria-hidden="true"
          />
        );

      case 'card':
        return (
          <div
            key={index}
            className={`${styles.skeleton} ${styles.skeletonCard}`}
            style={style}
            data-testid={itemTestId}
            aria-hidden="true"
          >
            <div className={styles.skeletonCardHeader} />
            <div className={styles.skeletonCardBody}>
              <div className={styles.skeletonLine} style={{ width: '80%' }} />
              <div className={styles.skeletonLine} style={{ width: '60%' }} />
              <div className={styles.skeletonLine} style={{ width: '70%' }} />
            </div>
          </div>
        );

      case 'table':
        return (
          <div
            key={index}
            className={`${styles.skeleton} ${styles.skeletonTableRow}`}
            style={style}
            data-testid={itemTestId}
            aria-hidden="true"
          >
            <div className={styles.skeletonCell} style={{ width: '20%' }} />
            <div className={styles.skeletonCell} style={{ width: '30%' }} />
            <div className={styles.skeletonCell} style={{ width: '25%' }} />
            <div className={styles.skeletonCell} style={{ width: '15%' }} />
          </div>
        );

      case 'form':
        return (
          <div
            key={index}
            className={`${styles.skeleton} ${styles.skeletonForm}`}
            style={style}
            data-testid={itemTestId}
            aria-hidden="true"
          >
            <div className={styles.skeletonFormField}>
              <div className={styles.skeletonLabel} />
              <div className={styles.skeletonInput} />
            </div>
          </div>
        );

      case 'header':
        return (
          <div
            key={index}
            className={`${styles.skeleton} ${styles.skeletonHeader}`}
            style={style}
            data-testid={itemTestId}
            aria-hidden="true"
          >
            <div className={styles.skeletonTitle} />
            <div className={styles.skeletonSubtitle} />
          </div>
        );

      case 'list':
        return (
          <div
            key={index}
            className={`${styles.skeleton} ${styles.skeletonListItem}`}
            style={style}
            data-testid={itemTestId}
            aria-hidden="true"
          >
            <div className={styles.skeletonAvatar} />
            <div className={styles.skeletonListContent}>
              <div className={styles.skeletonLine} style={{ width: '70%' }} />
              <div className={styles.skeletonLine} style={{ width: '50%' }} />
            </div>
          </div>
        );

      default:
        return (
          <div
            key={index}
            className={`${styles.skeleton} ${styles.skeletonText}`}
            style={style}
            data-testid={itemTestId}
            aria-hidden="true"
          />
        );
    }
  };

  const containerClasses = [
    styles.skeletonContainer,
    className,
  ].filter(Boolean).join(' ');

  return (
    <div
      className={containerClasses}
      role="status"
      aria-label="Loading content"
      data-testid={`${testId}-container`}
    >
      <span className={styles.srOnly}>Loading...</span>
      {Array.from({ length: count }, (_, index) => renderSkeletonItem(index))}
    </div>
  );
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
      <div
        className={className}
        data-testid={testId}
        aria-busy="true"
      >
        <span className={styles.srOnly}>{loadingMessage}</span>
        <Skeleton
          variant={skeleton}
          count={skeletonCount}
          data-testid={`${testId}-skeleton`}
        />
      </div>
    );
  }

  return (
    <div
      className={className}
      data-testid={testId}
      aria-busy="false"
    >
      {children}
    </div>
  );
}

// Export default for convenience
export default PageLoader;
