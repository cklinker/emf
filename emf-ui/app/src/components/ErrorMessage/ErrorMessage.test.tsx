/**
 * ErrorMessage Component Tests
 *
 * Tests for the ErrorMessage component covering:
 * - Basic rendering with string and Error objects
 * - Error type detection and styling
 * - Retry button functionality
 * - Display variants (default, compact, inline)
 * - Accessibility features
 *
 * Requirements:
 * - 18.1: Display appropriate error messages when API requests fail
 * - 18.5: Offer retry option when network errors occur
 */

import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ErrorMessage } from './ErrorMessage'
import { I18nProvider } from '../../context/I18nContext'

/**
 * Helper to render ErrorMessage with required providers
 */
function renderErrorMessage(props: React.ComponentProps<typeof ErrorMessage>) {
  return render(
    <I18nProvider>
      <ErrorMessage {...props} />
    </I18nProvider>
  )
}

describe('ErrorMessage', () => {
  describe('Basic Rendering', () => {
    it('renders with a string error message', () => {
      renderErrorMessage({ error: 'Something went wrong' })

      expect(screen.getByTestId('error-message')).toBeInTheDocument()
      expect(screen.getByTestId('error-message-message')).toHaveTextContent('Something went wrong')
    })

    it('renders with an Error object', () => {
      const error = new Error('Test error message')
      renderErrorMessage({ error })

      expect(screen.getByTestId('error-message-message')).toHaveTextContent('Test error message')
    })

    it('renders with custom test ID', () => {
      renderErrorMessage({ error: 'Test', 'data-testid': 'custom-error' })

      expect(screen.getByTestId('custom-error')).toBeInTheDocument()
    })

    it('renders error icon by default', () => {
      renderErrorMessage({ error: 'Test error' })

      expect(screen.getByTestId('error-message-icon')).toBeInTheDocument()
    })

    it('hides error icon when showIcon is false', () => {
      renderErrorMessage({ error: 'Test error', showIcon: false })

      expect(screen.queryByTestId('error-message-icon')).not.toBeInTheDocument()
    })

    it('renders custom title when provided', () => {
      renderErrorMessage({ error: 'Test error', title: 'Custom Title' })

      expect(screen.getByTestId('error-message-title')).toHaveTextContent('Custom Title')
    })
  })

  describe('Error Type Detection', () => {
    it('detects network error type from message', () => {
      renderErrorMessage({ error: 'Network connection failed' })

      expect(screen.getByTestId('error-message')).toHaveAttribute('data-error-type', 'network')
    })

    it('detects validation error type from message', () => {
      renderErrorMessage({ error: 'Validation failed: invalid email' })

      expect(screen.getByTestId('error-message')).toHaveAttribute('data-error-type', 'validation')
    })

    it('detects notFound error type from message', () => {
      renderErrorMessage({ error: 'Resource not found' })

      expect(screen.getByTestId('error-message')).toHaveAttribute('data-error-type', 'notFound')
    })

    it('detects forbidden error type from message', () => {
      renderErrorMessage({ error: 'Permission denied' })

      expect(screen.getByTestId('error-message')).toHaveAttribute('data-error-type', 'forbidden')
    })

    it('detects server error type from message', () => {
      renderErrorMessage({ error: 'Internal server error' })

      expect(screen.getByTestId('error-message')).toHaveAttribute('data-error-type', 'server')
    })

    it('uses generic type for unrecognized errors', () => {
      renderErrorMessage({ error: 'Something unexpected happened' })

      expect(screen.getByTestId('error-message')).toHaveAttribute('data-error-type', 'generic')
    })

    it('uses explicitly provided error type', () => {
      renderErrorMessage({ error: 'Some error', type: 'network' })

      expect(screen.getByTestId('error-message')).toHaveAttribute('data-error-type', 'network')
    })
  })

  describe('Retry Button', () => {
    it('does not render retry button when onRetry is not provided', () => {
      renderErrorMessage({ error: 'Test error' })

      expect(screen.queryByTestId('error-message-retry')).not.toBeInTheDocument()
    })

    it('renders retry button when onRetry is provided for network errors', () => {
      const onRetry = vi.fn()
      renderErrorMessage({ error: 'Network error', onRetry })

      expect(screen.getByTestId('error-message-retry')).toBeInTheDocument()
    })

    it('renders retry button when onRetry is provided for server errors', () => {
      const onRetry = vi.fn()
      renderErrorMessage({ error: 'Server error', onRetry })

      expect(screen.getByTestId('error-message-retry')).toBeInTheDocument()
    })

    it('renders retry button when onRetry is provided for generic errors', () => {
      const onRetry = vi.fn()
      renderErrorMessage({ error: 'Something went wrong', onRetry })

      expect(screen.getByTestId('error-message-retry')).toBeInTheDocument()
    })

    it('does not render retry button for validation errors even with onRetry', () => {
      const onRetry = vi.fn()
      renderErrorMessage({ error: 'Validation failed', onRetry })

      expect(screen.queryByTestId('error-message-retry')).not.toBeInTheDocument()
    })

    it('calls onRetry when retry button is clicked', () => {
      const onRetry = vi.fn()
      renderErrorMessage({ error: 'Network error', onRetry })

      fireEvent.click(screen.getByTestId('error-message-retry'))

      expect(onRetry).toHaveBeenCalledTimes(1)
    })

    it('retry button has accessible label', () => {
      const onRetry = vi.fn()
      renderErrorMessage({ error: 'Network error', onRetry })

      const retryButton = screen.getByTestId('error-message-retry')
      expect(retryButton).toHaveAttribute('aria-label', 'Retry')
    })
  })

  describe('Display Variants', () => {
    it('renders default variant with title', () => {
      renderErrorMessage({ error: 'Test error', variant: 'default' })

      expect(screen.getByTestId('error-message-title')).toBeInTheDocument()
    })

    it('renders compact variant without title', () => {
      renderErrorMessage({ error: 'Test error', variant: 'compact' })

      expect(screen.queryByTestId('error-message-title')).not.toBeInTheDocument()
      expect(screen.getByTestId('error-message-message')).toBeInTheDocument()
    })

    it('renders inline variant without title', () => {
      renderErrorMessage({ error: 'Test error', variant: 'inline' })

      expect(screen.queryByTestId('error-message-title')).not.toBeInTheDocument()
      expect(screen.getByTestId('error-message-message')).toBeInTheDocument()
    })

    it('applies variant class to container', () => {
      const { rerender } = renderErrorMessage({ error: 'Test', variant: 'compact' })

      expect(screen.getByTestId('error-message').className).toContain('compact')

      rerender(
        <I18nProvider>
          <ErrorMessage error="Test" variant="inline" />
        </I18nProvider>
      )

      expect(screen.getByTestId('error-message').className).toContain('inline')
    })
  })

  describe('Accessibility', () => {
    it('has role="alert" for screen readers', () => {
      renderErrorMessage({ error: 'Test error' })

      expect(screen.getByTestId('error-message')).toHaveAttribute('role', 'alert')
    })

    it('has aria-live="assertive" for immediate announcement', () => {
      renderErrorMessage({ error: 'Test error' })

      expect(screen.getByTestId('error-message')).toHaveAttribute('aria-live', 'assertive')
    })

    it('has aria-atomic="true" for complete announcement', () => {
      renderErrorMessage({ error: 'Test error' })

      expect(screen.getByTestId('error-message')).toHaveAttribute('aria-atomic', 'true')
    })

    it('icon is hidden from screen readers', () => {
      renderErrorMessage({ error: 'Test error' })

      expect(screen.getByTestId('error-message-icon')).toHaveAttribute('aria-hidden', 'true')
    })
  })

  describe('Custom Styling', () => {
    it('applies custom className', () => {
      renderErrorMessage({ error: 'Test', className: 'custom-class' })

      expect(screen.getByTestId('error-message').className).toContain('custom-class')
    })

    it('applies error type class to container', () => {
      renderErrorMessage({ error: 'Test', type: 'network' })

      expect(screen.getByTestId('error-message').className).toContain('network')
    })
  })

  describe('Error Message Extraction', () => {
    it('extracts message from Error object', () => {
      const error = new Error('Detailed error message')
      renderErrorMessage({ error })

      expect(screen.getByTestId('error-message-message')).toHaveTextContent(
        'Detailed error message'
      )
    })

    it('handles Error object with empty message', () => {
      const error = new Error('')
      renderErrorMessage({ error })

      // Should show a fallback message
      expect(screen.getByTestId('error-message-message')).toBeInTheDocument()
    })

    it('uses string error directly', () => {
      renderErrorMessage({ error: 'Direct string error' })

      expect(screen.getByTestId('error-message-message')).toHaveTextContent('Direct string error')
    })
  })

  describe('Error Icons', () => {
    it('shows icon for network errors', () => {
      renderErrorMessage({ error: 'Network error' })

      const icon = screen.getByTestId('error-message-icon')
      expect(icon.querySelector('svg')).toBeInTheDocument()
    })

    it('shows icon for validation errors', () => {
      renderErrorMessage({ error: 'Validation error' })

      const icon = screen.getByTestId('error-message-icon')
      expect(icon.querySelector('svg')).toBeInTheDocument()
    })

    it('shows icon for not found errors', () => {
      renderErrorMessage({ error: 'Not found' })

      const icon = screen.getByTestId('error-message-icon')
      expect(icon.querySelector('svg')).toBeInTheDocument()
    })

    it('shows icon for forbidden errors', () => {
      renderErrorMessage({ error: 'Permission denied' })

      const icon = screen.getByTestId('error-message-icon')
      expect(icon.querySelector('svg')).toBeInTheDocument()
    })

    it('shows icon for server errors', () => {
      renderErrorMessage({ error: 'Server error' })

      const icon = screen.getByTestId('error-message-icon')
      expect(icon.querySelector('svg')).toBeInTheDocument()
    })

    it('shows icon for generic errors', () => {
      renderErrorMessage({ error: 'Unknown error', type: 'generic' })

      const icon = screen.getByTestId('error-message-icon')
      expect(icon.querySelector('svg')).toBeInTheDocument()
    })
  })
})
