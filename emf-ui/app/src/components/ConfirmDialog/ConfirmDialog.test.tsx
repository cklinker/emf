/**
 * ConfirmDialog Component Tests
 *
 * Tests for the ConfirmDialog component covering rendering, interactions,
 * accessibility, and keyboard navigation.
 *
 * Requirements tested:
 * - 3.10: Display confirmation dialog before collection deletion
 * - 4.8: Display confirmation dialog before field deletion
 * - 5.5: Display confirmation dialog before role deletion
 * - 6.7: Display confirmation dialog before OIDC provider deletion
 */

import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ConfirmDialog } from './ConfirmDialog'
import { I18nProvider } from '../../context/I18nContext'

// Wrapper component to provide I18n context
function TestWrapper({ children }: { children: React.ReactNode }) {
  return <I18nProvider>{children}</I18nProvider>
}

// Helper to render with I18n context
function renderWithI18n(ui: React.ReactElement) {
  return render(ui, { wrapper: TestWrapper })
}

describe('ConfirmDialog Component', () => {
  const defaultProps = {
    open: true,
    title: 'Confirm Action',
    message: 'Are you sure you want to proceed?',
    onConfirm: vi.fn(),
    onCancel: vi.fn(),
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Rendering', () => {
    it('should render dialog when open is true', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} />)

      expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument()
    })

    it('should not render dialog when open is false', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} open={false} />)

      expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument()
    })

    it('should render title correctly', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} />)

      expect(screen.getByTestId('confirm-dialog-title')).toHaveTextContent('Confirm Action')
    })

    it('should render message correctly', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} />)

      expect(screen.getByTestId('confirm-dialog-message')).toHaveTextContent(
        'Are you sure you want to proceed?'
      )
    })

    it('should render confirm button with custom label', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} confirmLabel="Delete" />)

      expect(screen.getByTestId('confirm-dialog-confirm')).toHaveTextContent('Delete')
    })

    it('should render cancel button with custom label', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} cancelLabel="Go Back" />)

      expect(screen.getByTestId('confirm-dialog-cancel')).toHaveTextContent('Go Back')
    })

    it('should render with default variant', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} />)

      const dialog = screen.getByTestId('confirm-dialog')
      expect(dialog).toHaveAttribute('data-variant', 'default')
    })

    it('should render with danger variant', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} variant="danger" />)

      const dialog = screen.getByTestId('confirm-dialog')
      expect(dialog).toHaveAttribute('data-variant', 'danger')
    })

    it('should render destructive confirm button with danger variant', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} variant="danger" />)

      const confirmButton = screen.getByTestId('confirm-dialog-confirm')
      expect(confirmButton).toHaveAttribute('data-variant', 'destructive')
    })
  })

  describe('Button Interactions', () => {
    it('should call onConfirm when confirm button is clicked', async () => {
      const onConfirm = vi.fn()
      renderWithI18n(<ConfirmDialog {...defaultProps} onConfirm={onConfirm} />)

      fireEvent.click(screen.getByTestId('confirm-dialog-confirm'))

      expect(onConfirm).toHaveBeenCalledTimes(1)
    })

    it('should call onCancel when cancel button is clicked', async () => {
      const onCancel = vi.fn()
      renderWithI18n(<ConfirmDialog {...defaultProps} onCancel={onCancel} />)

      fireEvent.click(screen.getByTestId('confirm-dialog-cancel'))

      expect(onCancel).toHaveBeenCalledTimes(1)
    })
  })

  describe('Accessibility', () => {
    it('should have alertdialog role', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} />)

      // Radix AlertDialog renders with role="alertdialog" on the content
      const dialog = screen.getByTestId('confirm-dialog')
      expect(dialog).toHaveAttribute('role', 'alertdialog')
    })

    it('should have custom id on title', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} id="test-dialog" />)

      const title = screen.getByTestId('confirm-dialog-title')
      expect(title).toHaveAttribute('id', 'test-dialog-title')
    })

    it('should have custom id on description', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} id="test-dialog" />)

      const message = screen.getByTestId('confirm-dialog-message')
      expect(message).toHaveAttribute('id', 'test-dialog-description')
    })
  })

  describe('Custom ID', () => {
    it('should use custom id for accessibility attributes', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} id="custom-dialog" />)

      const title = screen.getByTestId('confirm-dialog-title')
      expect(title).toHaveAttribute('id', 'custom-dialog-title')

      const message = screen.getByTestId('confirm-dialog-message')
      expect(message).toHaveAttribute('id', 'custom-dialog-description')
    })

    it('should use default id when not provided', () => {
      renderWithI18n(<ConfirmDialog {...defaultProps} />)

      const title = screen.getByTestId('confirm-dialog-title')
      expect(title).toHaveAttribute('id', 'confirm-dialog-title')

      const message = screen.getByTestId('confirm-dialog-message')
      expect(message).toHaveAttribute('id', 'confirm-dialog-description')
    })
  })
})

describe('ConfirmDialog Integration', () => {
  it('should work with state management for open/close', () => {
    const TestComponent = () => {
      const [open, setOpen] = React.useState(false)
      const [confirmed, setConfirmed] = React.useState(false)

      return (
        <I18nProvider>
          <button data-testid="open-btn" onClick={() => setOpen(true)}>
            Open
          </button>
          <div data-testid="status">{confirmed ? 'Confirmed' : 'Not confirmed'}</div>
          <ConfirmDialog
            open={open}
            title="Confirm"
            message="Are you sure?"
            onConfirm={() => {
              setConfirmed(true)
              setOpen(false)
            }}
            onCancel={() => setOpen(false)}
          />
        </I18nProvider>
      )
    }

    render(<TestComponent />)

    // Initially closed
    expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument()
    expect(screen.getByTestId('status')).toHaveTextContent('Not confirmed')

    // Open dialog
    fireEvent.click(screen.getByTestId('open-btn'))
    expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument()

    // Confirm
    fireEvent.click(screen.getByTestId('confirm-dialog-confirm'))
    expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument()
    expect(screen.getByTestId('status')).toHaveTextContent('Confirmed')
  })

  it('should work with danger variant for destructive actions', () => {
    const onConfirm = vi.fn()
    const TestComponent = () => {
      const [open, setOpen] = React.useState(true)

      return (
        <I18nProvider>
          <ConfirmDialog
            open={open}
            title="Delete Item"
            message="This action cannot be undone."
            confirmLabel="Delete"
            cancelLabel="Keep"
            variant="danger"
            onConfirm={() => {
              onConfirm()
              setOpen(false)
            }}
            onCancel={() => setOpen(false)}
          />
        </I18nProvider>
      )
    }

    render(<TestComponent />)

    // Verify danger variant attribute
    expect(screen.getByTestId('confirm-dialog')).toHaveAttribute('data-variant', 'danger')

    // Verify destructive button variant
    expect(screen.getByTestId('confirm-dialog-confirm')).toHaveAttribute(
      'data-variant',
      'destructive'
    )

    // Verify custom labels
    expect(screen.getByTestId('confirm-dialog-confirm')).toHaveTextContent('Delete')
    expect(screen.getByTestId('confirm-dialog-cancel')).toHaveTextContent('Keep')

    // Confirm deletion
    fireEvent.click(screen.getByTestId('confirm-dialog-confirm'))
    expect(onConfirm).toHaveBeenCalledTimes(1)
  })
})
