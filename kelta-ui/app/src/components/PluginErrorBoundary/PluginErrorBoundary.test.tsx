/**
 * PluginErrorBoundary Tests
 *
 * Tests for the error boundary that wraps plugin-rendered components.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { PluginErrorBoundary } from './PluginErrorBoundary'

// Suppress console.error for expected errors in tests
beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

function ThrowingComponent({ message }: { message?: string }): never {
  throw new Error(message || 'Test plugin error')
}

function SafeComponent() {
  return <div data-testid="safe-content">Hello from plugin</div>
}

describe('PluginErrorBoundary', () => {
  it('renders children when no error occurs', () => {
    render(
      <PluginErrorBoundary>
        <SafeComponent />
      </PluginErrorBoundary>
    )
    expect(screen.getByTestId('safe-content')).toBeInTheDocument()
  })

  it('renders error fallback when child throws', () => {
    render(
      <PluginErrorBoundary>
        <ThrowingComponent />
      </PluginErrorBoundary>
    )
    expect(screen.getByTestId('plugin-error-boundary')).toBeInTheDocument()
    expect(screen.getByText('Test plugin error')).toBeInTheDocument()
  })

  it('shows plugin name and component type in error message', () => {
    render(
      <PluginErrorBoundary pluginName="my-plugin" componentType="field renderer">
        <ThrowingComponent />
      </PluginErrorBoundary>
    )
    expect(screen.getByText(/field renderer failed to render/)).toBeInTheDocument()
    expect(screen.getByText(/\(my-plugin\)/)).toBeInTheDocument()
  })

  it('renders compact error for inline use', () => {
    render(
      <PluginErrorBoundary compact>
        <ThrowingComponent />
      </PluginErrorBoundary>
    )
    expect(screen.getByTestId('plugin-error-compact')).toBeInTheDocument()
    expect(screen.getByText('Plugin error')).toBeInTheDocument()
  })

  it('resets error state when "Try again" is clicked', () => {
    let shouldThrow = true
    function ConditionalThrower() {
      if (shouldThrow) throw new Error('conditional error')
      return <div data-testid="recovered">Recovered!</div>
    }

    render(
      <PluginErrorBoundary>
        <ConditionalThrower />
      </PluginErrorBoundary>
    )

    expect(screen.getByTestId('plugin-error-boundary')).toBeInTheDocument()

    // Fix the component before retrying
    shouldThrow = false
    fireEvent.click(screen.getByText('Try again'))

    expect(screen.getByTestId('recovered')).toBeInTheDocument()
  })

  it('logs error with plugin context', () => {
    render(
      <PluginErrorBoundary pluginName="test-plugin" componentType="page component">
        <ThrowingComponent message="render failed" />
      </PluginErrorBoundary>
    )

    expect(console.error).toHaveBeenCalledWith(
      expect.stringContaining('page component'),
      expect.any(Error),
      expect.any(String)
    )
  })
})
