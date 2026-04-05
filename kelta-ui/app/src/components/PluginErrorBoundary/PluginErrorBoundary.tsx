/**
 * PluginErrorBoundary Component
 *
 * A lightweight error boundary for wrapping plugin-rendered components.
 * Catches errors from plugin field renderers and page components without
 * crashing the host application.
 *
 * Requirements:
 * - 12.9: Error boundaries around plugin components to prevent host crashes
 */

import { Component, type ErrorInfo, type ReactNode } from 'react'
import { AlertTriangle } from 'lucide-react'
import { cn } from '@/lib/utils'

export interface PluginErrorBoundaryProps {
  /** Child components to render */
  children: ReactNode
  /** Plugin name for error reporting */
  pluginName?: string
  /** Component type for error reporting (e.g., "field renderer", "page component") */
  componentType?: string
  /** Optional compact mode for inline use (e.g., inside form fields) */
  compact?: boolean
}

interface PluginErrorBoundaryState {
  hasError: boolean
  error: Error | null
}

export class PluginErrorBoundary extends Component<
  PluginErrorBoundaryProps,
  PluginErrorBoundaryState
> {
  constructor(props: PluginErrorBoundaryProps) {
    super(props)
    this.state = { hasError: false, error: null }
  }

  static getDerivedStateFromError(error: Error): PluginErrorBoundaryState {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    const { pluginName, componentType } = this.props
    console.error(
      `[Plugin] Error in ${componentType || 'component'}${pluginName ? ` from plugin "${pluginName}"` : ''}:`,
      error,
      errorInfo.componentStack
    )
  }

  resetError = (): void => {
    this.setState({ hasError: false, error: null })
  }

  render(): ReactNode {
    if (this.state.hasError) {
      const { compact, pluginName, componentType } = this.props

      if (compact) {
        return (
          <span
            className={cn(
              'inline-flex items-center gap-1 text-xs text-destructive',
              'rounded bg-destructive/10 px-2 py-1'
            )}
            role="alert"
            data-testid="plugin-error-compact"
          >
            <AlertTriangle className="h-3 w-3" aria-hidden="true" />
            Plugin error
          </span>
        )
      }

      return (
        <div
          className={cn(
            'flex items-center gap-3 rounded-md border border-destructive/30',
            'bg-destructive/5 p-4 text-sm text-destructive'
          )}
          role="alert"
          data-testid="plugin-error-boundary"
        >
          <AlertTriangle className="h-5 w-5 shrink-0" aria-hidden="true" />
          <div className="min-w-0">
            <p className="font-medium">
              Plugin {componentType || 'component'} failed to render
              {pluginName && ` (${pluginName})`}
            </p>
            {this.state.error && (
              <p className="mt-1 text-xs text-destructive/70 truncate">
                {this.state.error.message}
              </p>
            )}
            <button
              type="button"
              className="mt-2 text-xs underline hover:no-underline"
              onClick={this.resetError}
            >
              Try again
            </button>
          </div>
        </div>
      )
    }

    return this.props.children
  }
}
