/**
 * LiveRegion Component Tests
 *
 * Tests for the LiveRegion component, LiveRegionProvider, and useAnnounce hook.
 * Covers rendering, announcements, and accessibility features.
 *
 * Requirements tested:
 * - 14.3: Provide appropriate ARIA labels and roles for all components
 * - 14.5: Announce dynamic content changes to screen readers
 */

import React from 'react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import { LiveRegion, LiveRegionProvider, useAnnounce } from './LiveRegion'

// Test component that uses the useAnnounce hook
function TestComponent() {
  const { announce, clear } = useAnnounce()

  return (
    <div>
      <button data-testid="announce-polite" onClick={() => announce('Polite message', 'polite')}>
        Announce Polite
      </button>
      <button
        data-testid="announce-assertive"
        onClick={() => announce('Assertive message', 'assertive')}
      >
        Announce Assertive
      </button>
      <button data-testid="announce-default" onClick={() => announce('Default message')}>
        Announce Default
      </button>
      <button data-testid="clear" onClick={clear}>
        Clear
      </button>
    </div>
  )
}

describe('LiveRegion Component', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('Rendering', () => {
    it('should render with message', () => {
      render(<LiveRegion message="Test announcement" />)

      expect(screen.getByTestId('live-region')).toHaveTextContent('Test announcement')
    })

    it('should render empty when no message', () => {
      render(<LiveRegion message="" />)

      expect(screen.getByTestId('live-region')).toHaveTextContent('')
    })

    it('should update when message changes', () => {
      const { rerender } = render(<LiveRegion message="First message" />)

      expect(screen.getByTestId('live-region')).toHaveTextContent('First message')

      rerender(<LiveRegion message="Second message" />)

      expect(screen.getByTestId('live-region')).toHaveTextContent('Second message')
    })
  })

  describe('Accessibility', () => {
    it('should have role="status"', () => {
      render(<LiveRegion message="Test" />)

      expect(screen.getByTestId('live-region')).toHaveAttribute('role', 'status')
    })

    it('should have aria-live="polite" by default', () => {
      render(<LiveRegion message="Test" />)

      expect(screen.getByTestId('live-region')).toHaveAttribute('aria-live', 'polite')
    })

    it('should have aria-live="assertive" when specified', () => {
      render(<LiveRegion message="Test" politeness="assertive" />)

      expect(screen.getByTestId('live-region')).toHaveAttribute('aria-live', 'assertive')
    })

    it('should have aria-atomic="true"', () => {
      render(<LiveRegion message="Test" />)

      expect(screen.getByTestId('live-region')).toHaveAttribute('aria-atomic', 'true')
    })

    it('should be visually hidden', () => {
      render(<LiveRegion message="Test" />)

      const element = screen.getByTestId('live-region')
      const styles = element.style

      expect(styles.position).toBe('absolute')
      expect(styles.width).toBe('1px')
      expect(styles.height).toBe('1px')
      expect(styles.overflow).toBe('hidden')
    })
  })

  describe('Auto-clear', () => {
    it('should clear message after default delay', () => {
      render(<LiveRegion message="Test" clearAfterAnnounce={true} />)

      expect(screen.getByTestId('live-region')).toHaveTextContent('Test')

      act(() => {
        vi.advanceTimersByTime(1000)
      })

      expect(screen.getByTestId('live-region')).toHaveTextContent('')
    })

    it('should clear message after custom delay', () => {
      render(<LiveRegion message="Test" clearAfterAnnounce={true} clearDelay={2000} />)

      expect(screen.getByTestId('live-region')).toHaveTextContent('Test')

      act(() => {
        vi.advanceTimersByTime(1999)
      })

      expect(screen.getByTestId('live-region')).toHaveTextContent('Test')

      act(() => {
        vi.advanceTimersByTime(1)
      })

      expect(screen.getByTestId('live-region')).toHaveTextContent('')
    })

    it('should not clear message when clearAfterAnnounce is false', () => {
      render(<LiveRegion message="Test" clearAfterAnnounce={false} />)

      expect(screen.getByTestId('live-region')).toHaveTextContent('Test')

      act(() => {
        vi.advanceTimersByTime(5000)
      })

      expect(screen.getByTestId('live-region')).toHaveTextContent('Test')
    })
  })
})

describe('LiveRegionProvider', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('Rendering', () => {
    it('should render children', () => {
      render(
        <LiveRegionProvider>
          <div data-testid="child">Child content</div>
        </LiveRegionProvider>
      )

      expect(screen.getByTestId('child')).toBeInTheDocument()
    })

    it('should render both polite and assertive live regions', () => {
      render(
        <LiveRegionProvider>
          <div>Content</div>
        </LiveRegionProvider>
      )

      expect(screen.getByTestId('live-region-polite')).toBeInTheDocument()
      expect(screen.getByTestId('live-region-assertive')).toBeInTheDocument()
    })
  })

  describe('Accessibility', () => {
    it('should have correct ARIA attributes on polite region', () => {
      render(
        <LiveRegionProvider>
          <div>Content</div>
        </LiveRegionProvider>
      )

      const politeRegion = screen.getByTestId('live-region-polite')
      expect(politeRegion).toHaveAttribute('role', 'status')
      expect(politeRegion).toHaveAttribute('aria-live', 'polite')
      expect(politeRegion).toHaveAttribute('aria-atomic', 'true')
    })

    it('should have correct ARIA attributes on assertive region', () => {
      render(
        <LiveRegionProvider>
          <div>Content</div>
        </LiveRegionProvider>
      )

      const assertiveRegion = screen.getByTestId('live-region-assertive')
      expect(assertiveRegion).toHaveAttribute('role', 'alert')
      expect(assertiveRegion).toHaveAttribute('aria-live', 'assertive')
      expect(assertiveRegion).toHaveAttribute('aria-atomic', 'true')
    })
  })

  describe('announce', () => {
    it('should announce polite message', () => {
      render(
        <LiveRegionProvider>
          <TestComponent />
        </LiveRegionProvider>
      )

      act(() => {
        screen.getByTestId('announce-polite').click()
      })

      expect(screen.getByTestId('live-region-polite')).toHaveTextContent('Polite message')
      expect(screen.getByTestId('live-region-assertive')).toHaveTextContent('')
    })

    it('should announce assertive message', () => {
      render(
        <LiveRegionProvider>
          <TestComponent />
        </LiveRegionProvider>
      )

      act(() => {
        screen.getByTestId('announce-assertive').click()
      })

      expect(screen.getByTestId('live-region-assertive')).toHaveTextContent('Assertive message')
      expect(screen.getByTestId('live-region-polite')).toHaveTextContent('')
    })

    it('should use default politeness when not specified', () => {
      render(
        <LiveRegionProvider defaultPoliteness="polite">
          <TestComponent />
        </LiveRegionProvider>
      )

      act(() => {
        screen.getByTestId('announce-default').click()
      })

      expect(screen.getByTestId('live-region-polite')).toHaveTextContent('Default message')
    })

    it('should clear message after delay', () => {
      render(
        <LiveRegionProvider clearDelay={1000}>
          <TestComponent />
        </LiveRegionProvider>
      )

      act(() => {
        screen.getByTestId('announce-polite').click()
      })
      expect(screen.getByTestId('live-region-polite')).toHaveTextContent('Polite message')

      act(() => {
        vi.advanceTimersByTime(1000)
      })

      expect(screen.getByTestId('live-region-polite')).toHaveTextContent('')
    })
  })

  describe('clear', () => {
    it('should clear all messages', () => {
      render(
        <LiveRegionProvider>
          <TestComponent />
        </LiveRegionProvider>
      )

      act(() => {
        screen.getByTestId('announce-polite').click()
      })
      expect(screen.getByTestId('live-region-polite')).toHaveTextContent('Polite message')

      act(() => {
        screen.getByTestId('clear').click()
      })

      expect(screen.getByTestId('live-region-polite')).toHaveTextContent('')
      expect(screen.getByTestId('live-region-assertive')).toHaveTextContent('')
    })
  })
})

describe('useAnnounce', () => {
  it('should throw error when used outside LiveRegionProvider', () => {
    // Suppress console.error for this test
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

    function TestOutsideProvider() {
      useAnnounce()
      return null
    }

    expect(() => render(<TestOutsideProvider />)).toThrow(
      'useAnnounce must be used within a LiveRegionProvider'
    )

    consoleSpy.mockRestore()
  })

  it('should return context value when used inside LiveRegionProvider', () => {
    let contextValue: ReturnType<typeof useAnnounce> | undefined

    function TestInsideProvider() {
      contextValue = useAnnounce()
      return <div data-testid="has-context">{contextValue ? 'yes' : 'no'}</div>
    }

    render(
      <LiveRegionProvider>
        <TestInsideProvider />
      </LiveRegionProvider>
    )

    expect(screen.getByTestId('has-context')).toHaveTextContent('yes')
    expect(contextValue).toBeDefined()
    expect(typeof contextValue?.announce).toBe('function')
    expect(typeof contextValue?.clear).toBe('function')
  })
})
