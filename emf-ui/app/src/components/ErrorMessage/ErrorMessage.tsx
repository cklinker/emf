/**
 * ErrorMessage Component
 *
 * Displays error messages with optional retry functionality.
 * Supports different error types (network, validation, generic) and variants.
 *
 * Requirements:
 * - 18.1: Display appropriate error messages when API requests fail
 * - 18.5: Offer retry option when network errors occur
 *
 * Features:
 * - Display error message from Error object or string
 * - Optional retry button for recoverable errors
 * - Error icon with type-specific styling
 * - Accessible with role="alert" and aria-live="assertive"
 * - Support for different error types (network, validation, generic)
 * - Compact and inline variants
 * - Reduced motion support
 */

import React, { useMemo } from 'react'
import { useI18n } from '../../context/I18nContext'
import styles from './ErrorMessage.module.css'

/**
 * Error type variants
 */
export type ErrorType = 'network' | 'validation' | 'generic' | 'notFound' | 'forbidden' | 'server'

/**
 * Display variant for the error message
 */
export type ErrorVariant = 'default' | 'compact' | 'inline'

/**
 * Props for the ErrorMessage component
 */
export interface ErrorMessageProps {
  /** Error to display - can be an Error object or a string message */
  error: Error | string
  /** Optional callback when retry button is clicked */
  onRetry?: () => void
  /** Type of error (affects styling and default message) */
  type?: ErrorType
  /** Display variant */
  variant?: ErrorVariant
  /** Optional custom class name for additional styling */
  className?: string
  /** Optional test ID for testing purposes */
  'data-testid'?: string
  /** Optional custom title for the error */
  title?: string
  /** Whether to show the error icon */
  showIcon?: boolean
}

/**
 * Get the error message from an Error object or string
 */
function getErrorMessage(error: Error | string): string {
  if (typeof error === 'string') {
    return error
  }
  return error.message || 'An unknown error occurred'
}

/**
 * Detect error type from error object
 */
function detectErrorType(error: Error | string): ErrorType {
  const message = getErrorMessage(error).toLowerCase()

  // Check for network-related errors
  if (
    message.includes('network') ||
    message.includes('connection') ||
    message.includes('timeout') ||
    message.includes('fetch') ||
    message.includes('offline')
  ) {
    return 'network'
  }

  // Check for validation errors
  if (
    message.includes('validation') ||
    message.includes('invalid') ||
    message.includes('required')
  ) {
    return 'validation'
  }

  // Check for not found errors
  if (message.includes('not found') || message.includes('404')) {
    return 'notFound'
  }

  // Check for forbidden/unauthorized errors
  if (
    message.includes('forbidden') ||
    message.includes('unauthorized') ||
    message.includes('permission') ||
    message.includes('403') ||
    message.includes('401')
  ) {
    return 'forbidden'
  }

  // Check for server errors
  if (message.includes('server') || message.includes('500')) {
    return 'server'
  }

  return 'generic'
}

/**
 * Get the appropriate icon for an error type
 */
function getErrorIcon(type: ErrorType): string {
  switch (type) {
    case 'network':
      return '⚡' // Lightning bolt for network issues
    case 'validation':
      return '⚠' // Warning for validation
    case 'notFound':
      return '🔍' // Magnifying glass for not found
    case 'forbidden':
      return '🔒' // Lock for forbidden
    case 'server':
      return '🖥' // Computer for server errors
    case 'generic':
    default:
      return '✕' // X for generic errors
  }
}

/**
 * ErrorMessage Component
 *
 * Displays an error message with optional retry functionality.
 * Automatically detects error type if not specified.
 *
 * @example
 * ```tsx
 * // Basic usage with string
 * <ErrorMessage error="Something went wrong" />
 *
 * // With Error object
 * <ErrorMessage error={new Error("Network error")} />
 *
 * // With retry button
 * <ErrorMessage
 *   error="Failed to load data"
 *   onRetry={() => refetch()}
 * />
 *
 * // Compact variant
 * <ErrorMessage
 *   error="Invalid input"
 *   variant="compact"
 *   type="validation"
 * />
 * ```
 */
export function ErrorMessage({
  error,
  onRetry,
  type,
  variant = 'default',
  className,
  'data-testid': testId = 'error-message',
  title,
  showIcon = true,
}: ErrorMessageProps): React.ReactElement {
  const { t } = useI18n()

  // Get the error message
  const message = useMemo(() => getErrorMessage(error), [error])

  // Detect or use provided error type
  const errorType = useMemo(() => type || detectErrorType(error), [type, error])

  // Get the icon for this error type
  const icon = useMemo(() => getErrorIcon(errorType), [errorType])

  // Get the default title based on error type
  const displayTitle = useMemo(() => {
    if (title) return title

    switch (errorType) {
      case 'network':
        return t('errors.network')
      case 'validation':
        return t('errors.validation')
      case 'notFound':
        return t('errors.notFound')
      case 'forbidden':
        return t('errors.forbidden')
      case 'server':
        return t('errors.serverError')
      case 'generic':
      default:
        return t('common.error')
    }
  }, [title, errorType, t])

  // Determine if retry should be shown (typically for network/server errors)
  const showRetry = useMemo(() => {
    if (!onRetry) return false
    // Show retry for network and server errors by default
    return errorType === 'network' || errorType === 'server' || errorType === 'generic'
  }, [onRetry, errorType])

  // Combine class names
  const containerClasses = [styles.container, styles[variant], styles[errorType], className]
    .filter(Boolean)
    .join(' ')

  return (
    <div
      className={containerClasses}
      role="alert"
      aria-live="assertive"
      aria-atomic="true"
      data-testid={testId}
      data-error-type={errorType}
    >
      {/* Error icon */}
      {showIcon && (
        <span className={styles.icon} aria-hidden="true" data-testid={`${testId}-icon`}>
          {icon}
        </span>
      )}

      {/* Error content */}
      <div className={styles.content}>
        {/* Title (only shown in default variant) */}
        {variant === 'default' && (
          <h3 className={styles.title} data-testid={`${testId}-title`}>
            {displayTitle}
          </h3>
        )}

        {/* Error message */}
        <p className={styles.message} data-testid={`${testId}-message`}>
          {message}
        </p>
      </div>

      {/* Retry button */}
      {showRetry && onRetry && (
        <button
          type="button"
          className={styles.retryButton}
          onClick={onRetry}
          aria-label={t('common.retry')}
          data-testid={`${testId}-retry`}
        >
          <span className={styles.retryIcon} aria-hidden="true">
            ↻
          </span>
          <span className={styles.retryText}>{t('common.retry')}</span>
        </button>
      )}
    </div>
  )
}

// Export default for convenience
export default ErrorMessage
