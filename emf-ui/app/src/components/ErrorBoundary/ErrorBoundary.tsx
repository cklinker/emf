/**
 * ErrorBoundary Component
 *
 * A React error boundary that catches JavaScript errors in child components,
 * logs them, and displays a fallback UI with recovery options.
 *
 * Requirements:
 * - 18.7: Provide a global error boundary to catch and display unexpected errors
 * - 18.8: Display a user-friendly error page with recovery options
 * - 18.6: Log errors to the console with sufficient detail for debugging
 */

import { Component, type ErrorInfo, type ReactNode, type ReactElement } from 'react'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'

/**
 * Props for the ErrorBoundary component
 */
export interface ErrorBoundaryProps {
  /** Child components to render */
  children: ReactNode
  /** Optional custom fallback UI to display when an error occurs */
  fallback?: ReactNode
}

/**
 * State for the ErrorBoundary component
 */
export interface ErrorBoundaryState {
  /** Whether an error has been caught */
  hasError: boolean
  /** The error that was caught, if any */
  error: Error | null
}

/**
 * Props for the ErrorFallback component
 */
export interface ErrorFallbackProps {
  /** The error that was caught */
  error: Error | null
  /** Callback to reset the error state */
  onReset?: () => void
}

/**
 * Check if we're in development mode
 */
const isDevelopment = (): boolean => {
  return process.env.NODE_ENV === 'development' || import.meta.env?.DEV === true
}

/**
 * ErrorFallback Component
 *
 * Default fallback UI displayed when an error is caught.
 * Provides error information and recovery options.
 */
export function ErrorFallback({ error, onReset }: ErrorFallbackProps): ReactElement {
  /**
   * Handle reload page action
   */
  const handleReload = (): void => {
    window.location.reload()
  }

  /**
   * Handle navigate home action
   */
  const handleGoHome = (): void => {
    const slug = window.location.pathname.split('/')[1] || ''
    window.location.href = slug ? `/${slug}` : '/'
  }

  /**
   * Handle try again action (reset error boundary)
   */
  const handleTryAgain = (): void => {
    if (onReset) {
      onReset()
    }
  }

  const showStackTrace = isDevelopment() && error?.stack

  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center min-h-screen p-4 bg-background text-foreground',
        'md:p-8'
      )}
      role="alert"
      aria-live="assertive"
      aria-atomic="true"
      data-testid="error-boundary-fallback"
    >
      <div
        className={cn(
          'flex flex-col items-center max-w-[600px] w-full p-6 px-4 bg-card rounded-lg shadow-lg text-center',
          'md:p-8'
        )}
      >
        {/* Error Icon */}
        <div
          className={cn(
            'flex items-center justify-center w-16 h-16 mb-4 rounded-full bg-red-50 text-red-600 dark:bg-red-950 dark:text-red-400',
            'md:w-20 md:h-20 md:mb-6'
          )}
          aria-hidden="true"
        >
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            className={cn('w-9 h-9', 'md:w-12 md:h-12')}
          >
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
        </div>

        {/* Error Title */}
        <h1
          className={cn(
            'm-0 mb-4 text-xl font-semibold text-foreground leading-tight',
            'md:text-2xl'
          )}
          data-testid="error-title"
        >
          Something went wrong
        </h1>

        {/* Error Message */}
        <p
          className={cn('m-0 mb-6 text-sm text-muted-foreground leading-relaxed', 'md:text-base')}
          data-testid="error-message"
        >
          {error?.message || 'An unexpected error occurred. Please try again.'}
        </p>

        {/* Stack Trace (Development Only) */}
        {showStackTrace && (
          <details className="w-full mb-6 text-left" data-testid="error-stack-trace">
            <summary className="p-2 px-4 text-sm font-medium text-muted-foreground bg-muted rounded cursor-pointer hover:bg-muted/80">
              View technical details
            </summary>
            <pre className="mt-2 p-4 font-mono text-xs leading-relaxed text-foreground bg-muted border border-border rounded overflow-x-auto whitespace-pre-wrap break-words max-h-[300px] overflow-y-auto">
              {error.stack}
            </pre>
          </details>
        )}

        {/* Recovery Actions */}
        <div
          className={cn(
            'flex flex-col gap-2 justify-center mb-6 w-full',
            'md:flex-row md:flex-wrap md:w-auto'
          )}
          data-testid="error-actions"
        >
          {onReset && (
            <Button
              type="button"
              variant="default"
              onClick={handleTryAgain}
              className={cn('w-full', 'md:w-auto')}
              data-testid="try-again-button"
            >
              Try Again
            </Button>
          )}
          <Button
            type="button"
            variant="default"
            onClick={handleReload}
            className={cn('w-full', 'md:w-auto')}
            data-testid="reload-button"
          >
            Reload Page
          </Button>
          <Button
            type="button"
            variant="outline"
            onClick={handleGoHome}
            className={cn('w-full', 'md:w-auto')}
            data-testid="go-home-button"
          >
            Go Home
          </Button>
        </div>

        {/* Help Text */}
        <p className="m-0 text-sm text-muted-foreground">
          If this problem persists, please contact support.
        </p>
      </div>
    </div>
  )
}

/**
 * ErrorBoundary Component
 *
 * A React error boundary that catches JavaScript errors anywhere in the child
 * component tree, logs those errors, and displays a fallback UI.
 *
 * Features:
 * - Catches errors in child components during rendering, lifecycle methods, and constructors
 * - Logs errors to console with stack trace for debugging
 * - Displays customizable fallback UI with recovery options
 * - Provides "Reload Page" and "Go Home" recovery actions
 * - Accessible with proper ARIA attributes for screen readers
 *
 * @example
 * ```tsx
 * // Basic usage
 * <ErrorBoundary>
 *   <App />
 * </ErrorBoundary>
 *
 * // With custom fallback
 * <ErrorBoundary fallback={<CustomErrorPage />}>
 *   <App />
 * </ErrorBoundary>
 * ```
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props)
    this.state = {
      hasError: false,
      error: null,
    }
  }

  /**
   * Update state when an error is caught
   * This lifecycle method is called during the "render" phase
   *
   * @param error - The error that was thrown
   * @returns Updated state with error information
   */
  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return {
      hasError: true,
      error,
    }
  }

  /**
   * Log error information for debugging
   * This lifecycle method is called during the "commit" phase
   *
   * Requirement 18.6: Log errors to the console with sufficient detail for debugging
   *
   * @param error - The error that was thrown
   * @param errorInfo - Additional information about the error including component stack
   */
  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    // Log error with full details for debugging
    console.error('[ErrorBoundary] An error was caught:', {
      error: {
        name: error.name,
        message: error.message,
        stack: error.stack,
      },
      componentStack: errorInfo.componentStack,
      timestamp: new Date().toISOString(),
    })

    // Also log the raw error for easier debugging in console
    console.error('[ErrorBoundary] Error:', error)
    console.error('[ErrorBoundary] Component Stack:', errorInfo.componentStack)
  }

  /**
   * Reset the error state to allow recovery
   */
  resetError = (): void => {
    this.setState({
      hasError: false,
      error: null,
    })
  }

  render(): ReactNode {
    const { hasError, error } = this.state
    const { children, fallback } = this.props

    if (hasError) {
      // If a custom fallback is provided, use it
      if (fallback !== undefined) {
        return fallback
      }

      // Otherwise, use the default ErrorFallback component
      return <ErrorFallback error={error} onReset={this.resetError} />
    }

    return children
  }
}

export default ErrorBoundary
