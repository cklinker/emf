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
import styles from './ErrorBoundary.module.css'

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
      className={styles.errorContainer}
      role="alert"
      aria-live="assertive"
      aria-atomic="true"
      data-testid="error-boundary-fallback"
    >
      <div className={styles.errorContent}>
        {/* Error Icon */}
        <div className={styles.errorIcon} aria-hidden="true">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            className={styles.errorIconSvg}
          >
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
        </div>

        {/* Error Title */}
        <h1 className={styles.errorTitle} data-testid="error-title">
          Something went wrong
        </h1>

        {/* Error Message */}
        <p className={styles.errorMessage} data-testid="error-message">
          {error?.message || 'An unexpected error occurred. Please try again.'}
        </p>

        {/* Stack Trace (Development Only) */}
        {showStackTrace && (
          <details className={styles.stackTraceContainer} data-testid="error-stack-trace">
            <summary className={styles.stackTraceSummary}>View technical details</summary>
            <pre className={styles.stackTrace}>{error.stack}</pre>
          </details>
        )}

        {/* Recovery Actions */}
        <div className={styles.errorActions} data-testid="error-actions">
          {onReset && (
            <button
              type="button"
              className={styles.primaryButton}
              onClick={handleTryAgain}
              data-testid="try-again-button"
            >
              Try Again
            </button>
          )}
          <button
            type="button"
            className={styles.primaryButton}
            onClick={handleReload}
            data-testid="reload-button"
          >
            Reload Page
          </button>
          <button
            type="button"
            className={styles.secondaryButton}
            onClick={handleGoHome}
            data-testid="go-home-button"
          >
            Go Home
          </button>
        </div>

        {/* Help Text */}
        <p className={styles.helpText}>If this problem persists, please contact support.</p>
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
