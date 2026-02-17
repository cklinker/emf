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
import { Zap, AlertTriangle, Search, Lock, Monitor, X, RotateCw } from 'lucide-react'
import { useI18n } from '../../context/I18nContext'
import { cn } from '@/lib/utils'

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
 * Tailwind classes for each error type: container colors and icon background
 */
const ERROR_TYPE_CLASSES: Record<ErrorType, { container: string; iconBg: string }> = {
  network: {
    container:
      'bg-amber-50 border-amber-300 text-amber-900 dark:bg-amber-950 dark:border-amber-800 dark:text-amber-200',
    iconBg: 'bg-amber-400',
  },
  validation: {
    container:
      'bg-red-50 border-red-200 text-red-900 dark:bg-red-950 dark:border-red-800 dark:text-red-200',
    iconBg: 'bg-red-500',
  },
  notFound: {
    container:
      'bg-gray-100 border-gray-300 text-gray-700 dark:bg-gray-800 dark:border-gray-600 dark:text-gray-300',
    iconBg: 'bg-gray-500',
  },
  forbidden: {
    container:
      'bg-red-50 border-red-200 text-red-900 dark:bg-red-950 dark:border-red-800 dark:text-red-200',
    iconBg: 'bg-red-600',
  },
  server: {
    container:
      'bg-red-50 border-red-200 text-red-900 dark:bg-red-950 dark:border-red-800 dark:text-red-200',
    iconBg: 'bg-red-700',
  },
  generic: {
    container:
      'bg-red-50 border-red-200 text-red-900 dark:bg-red-950 dark:border-red-800 dark:text-red-200',
    iconBg: 'bg-red-500',
  },
}

/**
 * Tailwind classes for each display variant
 */
const VARIANT_CLASSES: Record<ErrorVariant, string> = {
  default: 'flex items-start gap-3 p-4 rounded-lg border animate-in fade-in',
  compact: 'flex items-center gap-2 p-2.5 rounded-lg border',
  inline: 'inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded bg-transparent border-0',
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
function getErrorIcon(type: ErrorType): React.ReactNode {
  switch (type) {
    case 'network':
      return <Zap size={20} />
    case 'validation':
      return <AlertTriangle size={20} />
    case 'notFound':
      return <Search size={20} />
    case 'forbidden':
      return <Lock size={20} />
    case 'server':
      return <Monitor size={20} />
    case 'generic':
    default:
      return <X size={20} />
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

  const typeClasses = ERROR_TYPE_CLASSES[errorType]

  return (
    <div
      className={cn(VARIANT_CLASSES[variant], typeClasses.container, className)}
      role="alert"
      aria-live="assertive"
      aria-atomic="true"
      data-testid={testId}
      data-error-type={errorType}
      data-variant={variant}
    >
      {/* Error icon */}
      {showIcon && (
        <span
          className={cn(
            'flex items-center justify-center rounded-full text-white shrink-0',
            variant === 'inline'
              ? 'w-4 h-4 bg-transparent text-current'
              : variant === 'compact'
                ? 'w-5 h-5'
                : 'w-8 h-8',
            variant !== 'inline' && typeClasses.iconBg
          )}
          aria-hidden="true"
          data-testid={`${testId}-icon`}
        >
          {icon}
        </span>
      )}

      {/* Error content */}
      <div
        className={cn(
          'flex flex-1 min-w-0',
          variant === 'default' ? 'flex-col' : 'flex-row items-center gap-2'
        )}
      >
        {/* Title (only shown in default variant) */}
        {variant === 'default' && (
          <h3
            className="m-0 text-[15px] font-semibold leading-snug"
            data-testid={`${testId}-title`}
          >
            {displayTitle}
          </h3>
        )}

        {/* Error message */}
        <p
          className={cn(
            'm-0 leading-relaxed opacity-90 break-words',
            variant === 'inline' ? 'text-[13px]' : 'text-sm'
          )}
          data-testid={`${testId}-message`}
        >
          {message}
        </p>
      </div>

      {/* Retry button */}
      {showRetry && onRetry && (
        <button
          type="button"
          className={cn(
            'inline-flex items-center gap-1.5 border border-current rounded-md',
            'bg-transparent text-inherit text-sm font-medium cursor-pointer',
            'shrink-0 self-start transition-colors',
            'hover:bg-black/5 dark:hover:bg-white/10',
            'focus-visible:outline-2 focus-visible:outline-current focus-visible:outline-offset-2',
            variant === 'inline' ? 'px-2 py-1 text-xs' : 'px-3.5 py-2'
          )}
          onClick={onRetry}
          aria-label={t('common.retry')}
          data-testid={`${testId}-retry`}
        >
          <span className="inline-flex leading-none" aria-hidden="true">
            <RotateCw size={14} />
          </span>
          <span className="leading-none">{t('common.retry')}</span>
        </button>
      )}
    </div>
  )
}

// Export default for convenience
export default ErrorMessage
