/**
 * SkipLinks Component Tests
 *
 * Tests for the skip navigation links component.
 * Validates requirement 14.2: All interactive elements are keyboard accessible
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SkipLinks, type SkipLinkTarget } from './SkipLinks'

describe('SkipLinks', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Rendering', () => {
    it('should render default skip links', () => {
      render(<SkipLinks />)

      expect(screen.getByText('Skip to main content')).toBeInTheDocument()
      expect(screen.getByText('Skip to navigation')).toBeInTheDocument()
    })

    it('should render custom skip link targets', () => {
      const customTargets: SkipLinkTarget[] = [
        { id: 'search', label: 'Skip to search' },
        { id: 'footer', label: 'Skip to footer' },
      ]

      render(<SkipLinks targets={customTargets} />)

      expect(screen.getByText('Skip to search')).toBeInTheDocument()
      expect(screen.getByText('Skip to footer')).toBeInTheDocument()
      expect(screen.queryByText('Skip to main content')).not.toBeInTheDocument()
    })

    it('should render with correct href attributes', () => {
      render(<SkipLinks />)

      const mainContentLink = screen.getByText('Skip to main content')
      const navigationLink = screen.getByText('Skip to navigation')

      expect(mainContentLink).toHaveAttribute('href', '#main-content')
      expect(navigationLink).toHaveAttribute('href', '#main-navigation')
    })

    it('should have navigation role with aria-label', () => {
      render(<SkipLinks />)

      const nav = screen.getByRole('navigation', { name: 'Skip links' })
      expect(nav).toBeInTheDocument()
    })

    it('should have correct test ids', () => {
      render(<SkipLinks />)

      expect(screen.getByTestId('skip-links')).toBeInTheDocument()
      expect(screen.getByTestId('skip-link-main-content')).toBeInTheDocument()
      expect(screen.getByTestId('skip-link-main-navigation')).toBeInTheDocument()
    })
  })

  describe('Accessibility', () => {
    it('should be focusable via keyboard', async () => {
      const user = userEvent.setup()
      render(<SkipLinks />)

      // Tab to first skip link
      await user.tab()

      const firstLink = screen.getByText('Skip to main content')
      expect(firstLink).toHaveFocus()
    })

    it('should allow tabbing through all skip links', async () => {
      const user = userEvent.setup()
      render(<SkipLinks />)

      // Tab to first skip link
      await user.tab()
      expect(screen.getByText('Skip to main content')).toHaveFocus()

      // Tab to second skip link
      await user.tab()
      expect(screen.getByText('Skip to navigation')).toHaveFocus()
    })

    it('should be links (anchor elements)', () => {
      render(<SkipLinks />)

      const links = screen.getAllByRole('link')
      expect(links).toHaveLength(2)
    })
  })

  describe('Click Behavior', () => {
    it('should focus target element when clicked', async () => {
      const user = userEvent.setup()

      // Create target element
      const targetElement = document.createElement('main')
      targetElement.id = 'main-content'
      document.body.appendChild(targetElement)

      render(<SkipLinks />)

      const skipLink = screen.getByText('Skip to main content')
      await user.click(skipLink)

      expect(targetElement).toHaveFocus()

      // Cleanup
      document.body.removeChild(targetElement)
    })

    it('should add tabindex to target element if not present', async () => {
      const user = userEvent.setup()

      const targetElement = document.createElement('main')
      targetElement.id = 'main-content'
      document.body.appendChild(targetElement)

      expect(targetElement.hasAttribute('tabindex')).toBe(false)

      render(<SkipLinks />)

      const skipLink = screen.getByText('Skip to main content')
      await user.click(skipLink)

      expect(targetElement.getAttribute('tabindex')).toBe('-1')

      document.body.removeChild(targetElement)
    })

    it('should not modify tabindex if already present', async () => {
      const user = userEvent.setup()

      const targetElement = document.createElement('main')
      targetElement.id = 'main-content'
      targetElement.setAttribute('tabindex', '0')
      document.body.appendChild(targetElement)

      render(<SkipLinks />)

      const skipLink = screen.getByText('Skip to main content')
      await user.click(skipLink)

      expect(targetElement.getAttribute('tabindex')).toBe('0')

      document.body.removeChild(targetElement)
    })

    it('should handle missing target element gracefully', async () => {
      const user = userEvent.setup()

      render(<SkipLinks />)

      const skipLink = screen.getByText('Skip to main content')

      // Should not throw when target doesn't exist
      await expect(user.click(skipLink)).resolves.not.toThrow()
    })
  })

  describe('Custom Targets', () => {
    it('should render single custom target', () => {
      const targets: SkipLinkTarget[] = [{ id: 'custom-section', label: 'Skip to custom section' }]

      render(<SkipLinks targets={targets} />)

      expect(screen.getByText('Skip to custom section')).toBeInTheDocument()
      expect(screen.getByTestId('skip-link-custom-section')).toBeInTheDocument()
    })

    it('should render multiple custom targets in order', () => {
      const targets: SkipLinkTarget[] = [
        { id: 'first', label: 'First' },
        { id: 'second', label: 'Second' },
        { id: 'third', label: 'Third' },
      ]

      render(<SkipLinks targets={targets} />)

      const links = screen.getAllByRole('link')
      expect(links).toHaveLength(3)
      expect(links[0]).toHaveTextContent('First')
      expect(links[1]).toHaveTextContent('Second')
      expect(links[2]).toHaveTextContent('Third')
    })

    it('should handle empty targets array', () => {
      render(<SkipLinks targets={[]} />)

      const links = screen.queryAllByRole('link')
      expect(links).toHaveLength(0)
    })
  })
})
