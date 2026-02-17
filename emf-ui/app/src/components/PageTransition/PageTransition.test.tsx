/**
 * PageTransition Component Tests
 *
 * Tests for the PageTransition component covering:
 * - Rendering with default props
 * - Transition type variants (fade, fade-slide, none)
 * - Reduced motion preference handling
 * - Custom duration support
 * - Accessibility attributes
 * - Animation state transitions
 */

import React from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { PageTransition, usePrefersReducedMotion } from './PageTransition'
import type { TransitionType } from './PageTransition'

// Mock matchMedia
const createMatchMedia = (matches: boolean) => {
  return (query: string) => ({
    matches,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })
}

describe('PageTransition', () => {
  beforeEach(() => {
    // Default to no reduced motion preference
    window.matchMedia = createMatchMedia(false) as typeof window.matchMedia
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('Rendering', () => {
    it('renders children correctly', () => {
      render(
        <PageTransition>
          <div data-testid="child-content">Test Content</div>
        </PageTransition>
      )

      expect(screen.getByTestId('child-content')).toBeInTheDocument()
      expect(screen.getByText('Test Content')).toBeInTheDocument()
    })

    it('renders with default test ID', () => {
      render(
        <PageTransition>
          <div>Content</div>
        </PageTransition>
      )

      expect(screen.getByTestId('page-transition')).toBeInTheDocument()
    })

    it('supports custom test ID', () => {
      render(
        <PageTransition data-testid="custom-transition">
          <div>Content</div>
        </PageTransition>
      )

      expect(screen.getByTestId('custom-transition')).toBeInTheDocument()
    })

    it('applies custom className', () => {
      render(
        <PageTransition className="custom-class">
          <div>Content</div>
        </PageTransition>
      )

      const container = screen.getByTestId('page-transition')
      expect(container).toHaveClass('custom-class')
    })
  })

  describe('Transition Types', () => {
    const transitionTypes: TransitionType[] = ['fade', 'fade-slide', 'none']

    transitionTypes.forEach((type) => {
      it(`renders with type="${type}"`, () => {
        render(
          <PageTransition type={type}>
            <div>Content</div>
          </PageTransition>
        )

        const container = screen.getByTestId('page-transition')
        expect(container).toHaveAttribute('data-transition-type', type)
      })
    })

    it('defaults to fade transition type', () => {
      render(
        <PageTransition>
          <div>Content</div>
        </PageTransition>
      )

      const container = screen.getByTestId('page-transition')
      expect(container).toHaveAttribute('data-transition-type', 'fade')
    })
  })

  describe('Animation States', () => {
    it('starts in hidden state and transitions to visible', async () => {
      render(
        <PageTransition>
          <div>Content</div>
        </PageTransition>
      )

      const container = screen.getByTestId('page-transition')

      // Wait for the animation frame to trigger visibility
      await waitFor(() => {
        expect(container).toHaveAttribute('data-visible', 'true')
      })
    })

    it('applies custom duration via inline transitionDuration style', () => {
      render(
        <PageTransition duration={500}>
          <div>Content</div>
        </PageTransition>
      )

      const container = screen.getByTestId('page-transition')
      expect(container).toHaveStyle({ transitionDuration: '500ms' })
    })

    it('uses default duration of 200ms', () => {
      render(
        <PageTransition>
          <div>Content</div>
        </PageTransition>
      )

      const container = screen.getByTestId('page-transition')
      expect(container).toHaveStyle({ transitionDuration: '200ms' })
    })
  })

  describe('Reduced Motion Preference', () => {
    it('respects prefers-reduced-motion: reduce', () => {
      window.matchMedia = createMatchMedia(true) as typeof window.matchMedia

      render(
        <PageTransition type="fade-slide">
          <div>Content</div>
        </PageTransition>
      )

      const container = screen.getByTestId('page-transition')
      expect(container).toHaveAttribute('data-reduced-motion', 'true')
      expect(container).toHaveAttribute('data-transition-type', 'none')
    })

    it('uses requested transition type when reduced motion is not preferred', () => {
      window.matchMedia = createMatchMedia(false) as typeof window.matchMedia

      render(
        <PageTransition type="fade-slide">
          <div>Content</div>
        </PageTransition>
      )

      const container = screen.getByTestId('page-transition')
      expect(container).toHaveAttribute('data-reduced-motion', 'false')
      expect(container).toHaveAttribute('data-transition-type', 'fade-slide')
    })

    it('sets data-reduced-motion attribute correctly', () => {
      window.matchMedia = createMatchMedia(true) as typeof window.matchMedia

      render(
        <PageTransition>
          <div>Content</div>
        </PageTransition>
      )

      const container = screen.getByTestId('page-transition')
      expect(container).toHaveAttribute('data-reduced-motion', 'true')
    })
  })

  describe('usePrefersReducedMotion Hook', () => {
    it('returns false when reduced motion is not preferred', () => {
      window.matchMedia = createMatchMedia(false) as typeof window.matchMedia

      function TestComponent() {
        const result = usePrefersReducedMotion()
        return <div data-testid="result">{String(result)}</div>
      }

      render(<TestComponent />)
      expect(screen.getByTestId('result')).toHaveTextContent('false')
    })

    it('returns true when reduced motion is preferred', () => {
      window.matchMedia = createMatchMedia(true) as typeof window.matchMedia

      function TestComponent() {
        const result = usePrefersReducedMotion()
        return <div data-testid="result">{String(result)}</div>
      }

      render(<TestComponent />)
      expect(screen.getByTestId('result')).toHaveTextContent('true')
    })
  })

  describe('Combined Props', () => {
    it('renders correctly with all props', async () => {
      render(
        <PageTransition
          type="fade-slide"
          duration={300}
          className="custom-class"
          data-testid="full-transition"
        >
          <div data-testid="child">Test Content</div>
        </PageTransition>
      )

      const container = screen.getByTestId('full-transition')
      expect(container).toBeInTheDocument()
      expect(container).toHaveClass('custom-class')
      expect(container).toHaveStyle({ transitionDuration: '300ms' })
      expect(container).toHaveAttribute('data-transition-type', 'fade-slide')

      const child = screen.getByTestId('child')
      expect(child).toBeInTheDocument()
      expect(child).toHaveTextContent('Test Content')

      // Wait for visible state
      await waitFor(() => {
        expect(container).toHaveAttribute('data-visible', 'true')
      })
    })
  })
})
